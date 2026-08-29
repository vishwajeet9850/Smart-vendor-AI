import uuid
import pytest
from datetime import datetime
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from database import Base
import models
import schemas
from routers import bills, analytics


@pytest.fixture
def test_db():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    Base.metadata.create_all(bind=engine)
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


def test_normal_billing_stock_deduction(test_db):
    """
    Scenario 1: Normal Billing
    Initial Stock = 10, Bill = 3 -> New Stock = 7
    """
    user_id = "test_vendor_1"
    product = models.Product(
        id="prod_100",
        user_id=user_id,
        name="Parle-G 100g",
        price=10.0,
        stock=10,
        low_stock_threshold=5,
        category="Biscuits"
    )
    test_db.add(product)
    test_db.commit()

    bill_create = schemas.BillCreate(
        transaction_type="BILL",
        items=[
            schemas.BillItemCreate(
                product_id="prod_100",
                product_name="Parle-G 100g",
                quantity=3,
                unit_price=10.0,
                total_price=30.0,
                condition="GOOD"
            )
        ],
        total_amount=30.0,
        payment_mode="cash"
    )

    created = bills.create_bill(body=bill_create, user_id=user_id, db=test_db)
    assert created.transaction_type == "BILL"
    assert len(created.items) == 1

    test_db.refresh(product)
    assert product.stock == 7


def test_normal_good_return_increases_stock(test_db):
    """
    Scenario 2: Normal Return (Good/Resalable)
    Initial Stock = 7, Return = 2 GOOD -> New Stock = 9
    """
    user_id = "test_vendor_1"
    product = models.Product(
        id="prod_100",
        user_id=user_id,
        name="Parle-G 100g",
        price=10.0,
        stock=7,
        low_stock_threshold=5,
        category="Biscuits"
    )
    test_db.add(product)
    test_db.commit()

    return_create = schemas.BillCreate(
        transaction_type="RETURN",
        items=[
            schemas.BillItemCreate(
                product_id="prod_100",
                product_name="Parle-G 100g",
                quantity=2,
                unit_price=10.0,
                total_price=20.0,
                condition="GOOD"
            )
        ],
        total_amount=20.0,
        payment_mode="cash"
    )

    created = bills.create_bill(body=return_create, user_id=user_id, db=test_db)
    assert created.transaction_type == "RETURN"
    assert created.items[0].condition == "GOOD"

    test_db.refresh(product)
    assert product.stock == 9


def test_damaged_return_does_not_increase_stock(test_db):
    """
    Scenario 3: Damaged Return
    Initial Stock = 7, Return = 2 DAMAGED -> New Stock = 7 (Unchanged)
    """
    user_id = "test_vendor_1"
    product = models.Product(
        id="prod_100",
        user_id=user_id,
        name="Parle-G 100g",
        price=10.0,
        stock=7,
        low_stock_threshold=5,
        category="Biscuits"
    )
    test_db.add(product)
    test_db.commit()

    return_create = schemas.BillCreate(
        transaction_type="RETURN",
        items=[
            schemas.BillItemCreate(
                product_id="prod_100",
                product_name="Parle-G 100g",
                quantity=2,
                unit_price=10.0,
                total_price=20.0,
                condition="DAMAGED"
            )
        ],
        total_amount=20.0,
        payment_mode="cash"
    )

    created = bills.create_bill(body=return_create, user_id=user_id, db=test_db)
    assert created.transaction_type == "RETURN"
    assert created.items[0].condition == "DAMAGED"

    test_db.refresh(product)
    assert product.stock == 7  # Damaged items must NOT increase sellable stock


def test_mixed_good_and_damaged_return(test_db):
    """
    Scenario 4: Mixed Return
    Initial Stock = 7, Return: 1 GOOD + 2 DAMAGED -> New Stock = 8
    """
    user_id = "test_vendor_1"
    product = models.Product(
        id="prod_100",
        user_id=user_id,
        name="Parle-G 100g",
        price=10.0,
        stock=7,
        low_stock_threshold=5,
        category="Biscuits"
    )
    test_db.add(product)
    test_db.commit()

    return_create = schemas.BillCreate(
        transaction_type="RETURN",
        items=[
            schemas.BillItemCreate(
                product_id="prod_100",
                product_name="Parle-G 100g",
                quantity=1,
                unit_price=10.0,
                total_price=10.0,
                condition="GOOD"
            ),
            schemas.BillItemCreate(
                product_id="prod_100",
                product_name="Parle-G 100g",
                quantity=2,
                unit_price=10.0,
                total_price=20.0,
                condition="DAMAGED"
            )
        ],
        total_amount=30.0,
        payment_mode="cash"
    )

    created = bills.create_bill(body=return_create, user_id=user_id, db=test_db)
    assert created.transaction_type == "RETURN"
    assert len(created.items) == 2

    test_db.refresh(product)
    assert product.stock == 8  # Only the 1 GOOD unit added back (7 + 1 = 8)


def test_analytics_revenue_and_bills_count_with_returns(test_db):
    """
    Scenario 6 & 7: Analytics Net Revenue
    Bill of ₹450 + Return of ₹200 -> Net Revenue = ₹250, Total Bills = 1 (Returns not counted as bill sales)
    """
    user_id = "test_vendor_1"
    product = models.Product(
        id="prod_100",
        user_id=user_id,
        name="Parle-G 100g",
        price=100.0,
        stock=50,
        low_stock_threshold=5,
        category="Biscuits"
    )
    test_db.add(product)
    test_db.commit()

    # 1. Create a sale
    sale_bill = models.Bill(
        id="bill_1",
        user_id=user_id,
        transaction_type="BILL",
        total_amount=450.0,
        tax_amount=21.43,
        payment_mode="cash",
        created_at=datetime.utcnow()
    )
    test_db.add(sale_bill)

    # 2. Create a return
    return_bill = models.Bill(
        id="return_1",
        user_id=user_id,
        transaction_type="RETURN",
        total_amount=200.0,
        tax_amount=9.52,
        payment_mode="cash",
        created_at=datetime.utcnow()
    )
    test_db.add(return_bill)
    test_db.commit()

    summary = analytics.get_summary(user_id=user_id, days=30, db=test_db)
    assert summary.total_revenue == 250.0  # 450 - 200 = 250
    assert summary.total_bills == 1  # only sales bill counted, not return
