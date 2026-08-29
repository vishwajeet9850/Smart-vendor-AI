"""
Unit tests for SmartVendor Multi-Type Recommendations & Explainable Reasoning across all 3 users.
"""

import pytest
from database import SessionLocal
from services.recommendation_engine import StockRecommendationEngine
import models


@pytest.fixture
def db():
    session = SessionLocal()
    yield session
    session.close()


DEMO_USERS = [
    ("Harsh Sadgir", "uXXp4u9hvxP9hrv22LvllrlX6hx1"),
    ("Himanshu Sawant", "lV3NgaMqXxerZHZlLjjilXhJTL22"),
    ("Vishwajeet Shelke", "wmPdaGXXkGRfiglryt7T7QS0eWG3"),
]


def test_all_users_have_diverse_recommendation_types(db):
    """
    Verify that each of the 3 users has multiple distinct recommendation types
    with non-empty plain-English explanations.
    """
    valid_types = {
        "URGENT_RESTOCK",
        "LOW_STOCK_BUFFER",
        "FESTIVAL_SURGE",
        "MARKET_TREND",
        "NEAR_EXPIRY",
        "OVERSTOCK_CLEARANCE",
        "BUNDLE_OPPORTUNITY",
        "HEALTHY_STOCK",
    }

    for name, user_id in DEMO_USERS:
        bulk = StockRecommendationEngine.generate_bulk_recommendations(
            db=db, user_id=user_id, forecast_days=7
        )
        assert bulk.total_products >= 15, f"{name} should have at least 15 seeded products"

        found_types = set()
        for rec in bulk.recommendations:
            assert rec.recommendation_type in valid_types, f"Invalid type: {rec.recommendation_type}"
            assert len(rec.recommendation_title) > 3
            assert len(rec.action_label) > 3
            assert len(rec.simple_reason) > 20, f"Explanation too short for {rec.product_name}"
            found_types.add(rec.recommendation_type)

        # Every user should have at least 4 distinct recommendation types
        assert len(found_types) >= 4, f"{name} has only types: {found_types}"


def test_harsh_milk_urgent_restock(db):
    """Verify Harsh has Out of Stock for Amul Milk with clear explanation."""
    harsh_uid = "uXXp4u9hvxP9hrv22LvllrlX6hx1"
    product = db.query(models.Product).filter(
        models.Product.user_id == harsh_uid,
        models.Product.name == "Amul Taaza Toned Milk 1L"
    ).first()

    rec = StockRecommendationEngine.generate_recommendation_for_product(
        db=db, user_id=harsh_uid, product=product
    )
    assert rec.recommendation_type == "URGENT_RESTOCK"
    assert rec.current_stock == 0
    assert rec.recommended_purchase > 0
    assert "sold out" in rec.simple_reason.lower() or "out of stock" in rec.simple_reason.lower()


def test_himanshu_sunflower_oil_urgent_restock(db):
    """Verify Himanshu has Out of Stock for Sunflower Oil."""
    himanshu_uid = "lV3NgaMqXxerZHZlLjjilXhJTL22"
    product = db.query(models.Product).filter(
        models.Product.user_id == himanshu_uid,
        models.Product.name == "Fortune Sunlite Sunflower Oil 1L"
    ).first()

    rec = StockRecommendationEngine.generate_recommendation_for_product(
        db=db, user_id=himanshu_uid, product=product
    )
    assert rec.recommendation_type == "URGENT_RESTOCK"
    assert rec.current_stock == 0
    assert rec.recommended_purchase > 0


def test_vishwajeet_atta_urgent_restock(db):
    """Verify Vishwajeet has Critical Low Stock for Atta 5kg."""
    vishwajeet_uid = "wmPdaGXXkGRfiglryt7T7QS0eWG3"
    product = db.query(models.Product).filter(
        models.Product.user_id == vishwajeet_uid,
        models.Product.name == "Aashirvaad Shudh Chakki Atta 5kg"
    ).first()

    rec = StockRecommendationEngine.generate_recommendation_for_product(
        db=db, user_id=vishwajeet_uid, product=product
    )
    assert rec.recommendation_type == "URGENT_RESTOCK"
    assert rec.current_stock == 1
    assert rec.recommended_purchase > 0
    assert "critical low stock" in rec.simple_reason.lower()


def test_near_expiry_classification(db):
    """Verify near-expiry bread recommendation produces flash discount action."""
    harsh_uid = "uXXp4u9hvxP9hrv22LvllrlX6hx1"
    product = db.query(models.Product).filter(
        models.Product.user_id == harsh_uid,
        models.Product.name == "Britannia 100% Whole Wheat Bread 400g"
    ).first()

    rec = StockRecommendationEngine.generate_recommendation_for_product(
        db=db, user_id=harsh_uid, product=product
    )
    assert rec.recommendation_type == "NEAR_EXPIRY"
    assert rec.action_type == "DISCOUNT"
    assert rec.recommended_purchase == 0
    assert "expir" in rec.simple_reason.lower()
    assert "discount" in rec.simple_reason.lower() or "buy 1 get 1" in rec.simple_reason.lower()
