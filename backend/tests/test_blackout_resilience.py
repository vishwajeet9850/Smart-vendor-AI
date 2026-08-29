import pytest
import uuid
from datetime import datetime
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from auth import get_current_user_id
from database import Base, get_db
import models
from main import app
from services.resilience_manager import resilience_manager

# In-memory SQLite engine for isolated fast testing
TEST_DATABASE_URL = "sqlite:///:memory:"

test_engine = create_engine(
    TEST_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)

active_user = "vendor_blackout_1"

def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()

from fastapi import Request

async def override_auth(request: Request):
    return request.headers.get("x-user-id") or active_user

app.dependency_overrides[get_db] = override_get_db
app.dependency_overrides[get_current_user_id] = override_auth
client = TestClient(app)



import glob
import os

@pytest.fixture(autouse=True)
def setup_db():
    from services.resilience_manager import JOURNAL_DIR, CHECKPOINT_DIR
    for f in glob.glob(os.path.join(JOURNAL_DIR, "journal_vendor_*.jsonl")):
        try:
            os.remove(f)
        except Exception:
            pass
    for f in glob.glob(os.path.join(CHECKPOINT_DIR, "*.json")):
        try:
            os.remove(f)
        except Exception:
            pass
    Base.metadata.create_all(bind=test_engine)
    yield
    Base.metadata.drop_all(bind=test_engine)
    for f in glob.glob(os.path.join(JOURNAL_DIR, "journal_vendor_*.jsonl")):
        try:
            os.remove(f)
        except Exception:
            pass
    for f in glob.glob(os.path.join(CHECKPOINT_DIR, "*.json")):
        try:
            os.remove(f)
        except Exception:
            pass




def get_headers(user_id: str):
    return {"x-user-id": user_id}


def test_normal_billing_and_journaling():
    user = "vendor_blackout_1"
    headers = get_headers(user)

    # 1. Create Product: Rice with stock 50
    prod_res = client.post("/products", json={
        "name": "Basmati Rice 1kg",
        "category": "Grocery",
        "price": 80.0,
        "stock": 50
    }, headers=headers)
    assert prod_res.status_code == 201
    prod_id = prod_res.json()["id"]

    # 2. Create Bill: Buy 5 Rice
    bill_res = client.post("/bills", json={
        "items": [{
            "product_id": prod_id,
            "product_name": "Basmati Rice 1kg",
            "quantity": 5,
            "unit_price": 80.0,
            "total_price": 400.0
        }],
        "total_amount": 400.0
    }, headers=headers)
    assert bill_res.status_code == 201

    # 3. Check stock reduced to 45
    updated_prod = client.get(f"/products/{prod_id}", headers=headers).json()
    assert updated_prod["stock"] == 45

    # 4. Check journal entry created
    journal_res = client.get("/resilience/journal", headers=headers)
    assert journal_res.status_code == 200
    journal = journal_res.json()
    assert len(journal) >= 1
    tx = journal[0]
    assert tx["type"] == "SALE"
    assert tx["product_id"] == prod_id
    assert tx["quantity"] == 5
    assert tx["status"] == "APPLIED"


def test_normal_good_and_damaged_returns_journaling():
    user = "vendor_blackout_2"
    headers = get_headers(user)

    # 1. Create Product: Milk with stock 20
    prod_res = client.post("/products", json={
        "name": "Fresh Milk 1L",
        "category": "Dairy",
        "price": 30.0,
        "stock": 20
    }, headers=headers)
    prod_id = prod_res.json()["id"]

    # 2. Return 3 Good Milk (Restocked)
    ret_good_res = client.post("/bills", json={
        "transaction_type": "RETURN",
        "items": [{
            "product_id": prod_id,
            "product_name": "Fresh Milk 1L",
            "quantity": 3,
            "unit_price": 30.0,
            "total_price": 90.0,
            "condition": "GOOD"
        }],
        "total_amount": 90.0
    }, headers=headers)
    assert ret_good_res.status_code == 201

    # Stock should be 20 + 3 = 23
    assert client.get(f"/products/{prod_id}", headers=headers).json()["stock"] == 23

    # 3. Return 2 Damaged Milk (Not Restocked)
    ret_dam_res = client.post("/bills", json={
        "transaction_type": "RETURN",
        "items": [{
            "product_id": prod_id,
            "product_name": "Fresh Milk 1L",
            "quantity": 2,
            "unit_price": 30.0,
            "total_price": 60.0,
            "condition": "DAMAGED"
        }],
        "total_amount": 60.0
    }, headers=headers)
    assert ret_dam_res.status_code == 201

    # Stock should remain 23
    assert client.get(f"/products/{prod_id}", headers=headers).json()["stock"] == 23


def test_simulate_blackout_and_recovery_mode():
    user = "vendor_blackout_3"
    headers = get_headers(user)

    # Initially healthy
    status_before = client.get("/resilience/status", headers=headers).json()
    assert status_before["system_status"] == "HEALTHY"
    assert status_before["is_blackout_active"] is False

    # Simulate Blackout
    blackout_res = client.post("/resilience/simulate-blackout", headers=headers)
    assert blackout_res.status_code == 200
    status_after = blackout_res.json()
    assert status_after["system_status"] == "BLACKOUT_ACTIVE"
    assert status_after["is_blackout_active"] is True
    assert status_after["primary_database_status"] == "CORRUPTED_UNAVAILABLE"


def test_end_to_end_blackout_demo_scenario():
    """
    Exact Hackathon Demo Scenario:
    1. Initial: Rice stock = 50.
    2. Checkpoint taken.
    3. Normal sale: 5 Rice -> Stock = 45.
    4. SIMULATE BLACKOUT -> Recovery Mode Active.
    5. Blackout sale: 3 Rice -> Safely journaled.
    6. RESTORE SYSTEM -> Replays transactions.
    7. Final Rice stock = 42 (No duplicates, consistent inventory).
    """
    user = "vendor_demo_4"
    headers = get_headers(user)

    # Step 1: Create Rice with 50 stock
    prod_res = client.post("/products", json={
        "name": "Fortune Rice",
        "category": "Grocery",
        "price": 50.0,
        "stock": 50
    }, headers=headers)
    prod_id = prod_res.json()["id"]

    # Step 2: Create Baseline Checkpoint
    chk_res = client.post("/resilience/checkpoint", headers=headers)
    assert chk_res.status_code == 201

    # Step 3: Perform Normal Sale: 5 Rice
    sale1 = client.post("/bills", json={
        "items": [{
            "product_id": prod_id,
            "product_name": "Fortune Rice",
            "quantity": 5,
            "unit_price": 50.0,
            "total_price": 250.0
        }],
        "total_amount": 250.0
    }, headers=headers)
    assert sale1.status_code == 201
    assert client.get(f"/products/{prod_id}", headers=headers).json()["stock"] == 45

    # Step 4: Simulate Blackout
    blackout_res = client.post("/resilience/simulate-blackout", headers=headers)
    assert blackout_res.json()["is_blackout_active"] is True

    # Step 5: Perform Sale during Blackout: 3 Rice
    blackout_sale = client.post("/bills", json={
        "items": [{
            "product_id": prod_id,
            "product_name": "Fortune Rice",
            "quantity": 3,
            "unit_price": 50.0,
            "total_price": 150.0
        }],
        "total_amount": 150.0
    }, headers=headers)
    assert blackout_sale.status_code == 201

    # Verify journal recorded the blackout sale as PENDING
    journal = client.get("/resilience/journal", headers=headers).json()
    pending_txs = [t for t in journal if t["status"] == "PENDING"]
    assert len(pending_txs) >= 1

    # Step 6: RESTORE SYSTEM
    restore_res = client.post("/resilience/restore", headers=headers)
    assert restore_res.status_code == 200
    report = restore_res.json()
    assert report["system_status"] == "HEALTHY"
    assert report["successfully_recovered"] >= 1

    # Step 7: Verify final stock is exactly 42 (50 - 5 - 3 = 42)
    final_prod = client.get(f"/products/{prod_id}", headers=headers).json()
    assert final_prod["stock"] == 42

    # Step 8: Verify system status is now healthy
    status_now = client.get("/resilience/status", headers=headers).json()
    assert status_now["system_status"] == "HEALTHY"
    assert status_now["is_blackout_active"] is False


def test_idempotency_prevents_duplicate_replay():
    """
    Verifies that re-running restore or replaying an already-applied transaction
    never creates duplicate bills or double stock deductions.
    """
    user = "vendor_idempotent_5"
    headers = get_headers(user)

    # 1. Product: Atta with 30 stock
    prod_res = client.post("/products", json={
        "name": "Aashirvaad Atta",
        "category": "Grocery",
        "price": 40.0,
        "stock": 30
    }, headers=headers)
    prod_id = prod_res.json()["id"]

    # 2. Checkpoint
    client.post("/resilience/checkpoint", headers=headers)

    # 3. Sale of 4 Atta
    fixed_tx_id = "TXN_TEST_IDEMPOTENT_123"
    client.post("/bills", json={
        "transaction_id": fixed_tx_id,
        "items": [{
            "product_id": prod_id,
            "product_name": "Aashirvaad Atta",
            "quantity": 4,
            "unit_price": 40.0,
            "total_price": 160.0
        }],
        "total_amount": 160.0
    }, headers=headers)

    # Stock is 26
    assert client.get(f"/products/{prod_id}", headers=headers).json()["stock"] == 26

    # 4. Attempt to send duplicate bill with same transaction_id
    dup_res = client.post("/bills", json={
        "transaction_id": fixed_tx_id,
        "items": [{
            "product_id": prod_id,
            "product_name": "Aashirvaad Atta",
            "quantity": 4,
            "unit_price": 40.0,
            "total_price": 160.0
        }],
        "total_amount": 160.0
    }, headers=headers)
    assert dup_res.status_code == 201

    # Stock must NOT be deducted twice (must remain 26)
    assert client.get(f"/products/{prod_id}", headers=headers).json()["stock"] == 26

    # 5. Execute system restore
    report1 = client.post("/resilience/restore", headers=headers).json()
    assert report1["system_status"] == "HEALTHY"
    assert client.get(f"/products/{prod_id}", headers=headers).json()["stock"] == 26

    # 6. Execute system restore a second time
    report2 = client.post("/resilience/restore", headers=headers).json()
    assert report2["system_status"] == "HEALTHY"
    # Stock must still be exactly 26
    assert client.get(f"/products/{prod_id}", headers=headers).json()["stock"] == 26


def test_user_data_isolation_in_resilience():
    user_a = "vendor_isolated_a"
    user_b = "vendor_isolated_b"

    # User A creates product and checkpoint
    client.post("/products", json={"name": "Prod A", "price": 10.0, "stock": 100}, headers=get_headers(user_a))
    client.post("/resilience/checkpoint", headers=get_headers(user_a))

    # User B creates product and checkpoint
    client.post("/products", json={"name": "Prod B", "price": 20.0, "stock": 200}, headers=get_headers(user_b))
    client.post("/resilience/checkpoint", headers=get_headers(user_b))

    # User A puts system in blackout
    client.post("/resilience/simulate-blackout", headers=get_headers(user_a))

    # Verify User A is in blackout, but User B remains healthy
    status_a = client.get("/resilience/status", headers=get_headers(user_a)).json()
    status_b = client.get("/resilience/status", headers=get_headers(user_b)).json()

    assert status_a["is_blackout_active"] is True
    assert status_b["is_blackout_active"] is False

    # User A's journal has only User A's items
    journal_a = client.get("/resilience/journal", headers=get_headers(user_a)).json()
    journal_b = client.get("/resilience/journal", headers=get_headers(user_b)).json()

    for item in journal_a:
        assert item["user_id"] == user_a
    for item in journal_b:
        assert item["user_id"] == user_b
