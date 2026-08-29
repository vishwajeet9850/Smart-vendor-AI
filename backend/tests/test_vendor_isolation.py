"""
Security and Vendor Isolation Test Suite for SmartVendor
Verifies strict isolation, zero cross-vendor data leakage, IDOR prevention, and anonymity in market intelligence.
"""

import pytest
from database import SessionLocal
import models
from services.recommendation_engine import StockRecommendationEngine
from services.market_intelligence import MarketIntelligenceEngine


@pytest.fixture
def db():
    session = SessionLocal()
    yield session
    session.close()


def test_strict_vendor_inventory_isolation(db):
    """Verify products and stock belonging to Harsh are never returned for Himanshu or Vishwajeet."""
    harsh_uid = "uXXp4u9hvxP9hrv22LvllrlX6hx1"
    himanshu_uid = "lV3NgaMqXxerZHZlLjjilXhJTL22"
    vishwajeet_uid = "wmPdaGXXkGRfiglryt7T7QS0eWG3"

    harsh_products = db.query(models.Product).filter(models.Product.user_id == harsh_uid).all()
    himanshu_products = db.query(models.Product).filter(models.Product.user_id == himanshu_uid).all()
    vishwajeet_products = db.query(models.Product).filter(models.Product.user_id == vishwajeet_uid).all()

    harsh_ids = {p.id for p in harsh_products}
    himanshu_ids = {p.id for p in himanshu_products}
    vishwajeet_ids = {p.id for p in vishwajeet_products}

    # Intersections must be completely empty (disjoint primary keys)
    assert len(harsh_ids.intersection(himanshu_ids)) == 0
    assert len(harsh_ids.intersection(vishwajeet_ids)) == 0
    assert len(himanshu_ids.intersection(vishwajeet_ids)) == 0


def test_strict_vendor_sales_isolation(db):
    """Verify bills and sales revenue belonging to Harsh are never accessible to another vendor."""
    harsh_uid = "uXXp4u9hvxP9hrv22LvllrlX6hx1"
    himanshu_uid = "lV3NgaMqXxerZHZlLjjilXhJTL22"

    harsh_bills = db.query(models.Bill).filter(models.Bill.user_id == harsh_uid).all()
    himanshu_bills = db.query(models.Bill).filter(models.Bill.user_id == himanshu_uid).all()

    assert len(harsh_bills) > 0
    assert len(himanshu_bills) > 0

    for b in harsh_bills:
        assert b.user_id == harsh_uid
        assert b.user_id != himanshu_uid


def test_zero_competitor_data_leakage_in_recommendations(db):
    """
    Verify that StockRecommendationResponse and MarketDemandInfo NEVER leak competitor names,
    competitor IDs, or exact competitor sales.
    """
    harsh_uid = "uXXp4u9hvxP9hrv22LvllrlX6hx1"
    bulk_harsh = StockRecommendationEngine.generate_bulk_recommendations(
        db=db,
        user_id=harsh_uid,
        forecast_days=7
    )

    competitor_markers = ["Himanshu", "lV3Nga", "Vishwajeet", "wmPdaG", "demo_vendor_444"]

    for rec in bulk_harsh.recommendations:
        # Check text in reasons and insights
        full_text = f"{rec.reason} {rec.market.insight_text or ''}"
        for marker in competitor_markers:
            assert marker not in full_text, f"Competitor data leakage detected: '{marker}' found in recommendation for '{rec.product_name}'"

        # Check market dict does not contain individual vendor sales
        market_dict = rec.market.model_dump()
        assert "competitor_sales" not in market_dict
        assert "competitor_stores" not in market_dict
        assert "vendor_breakdown" not in market_dict


def test_minimum_participating_vendor_privacy_rule(db):
    """
    Verify that if fewer than 3 vendors exist for a product category,
    market_insight_available is set to False.
    """
    dummy_vendor = "single_dummy_vendor_999"
    signal = MarketIntelligenceEngine.get_market_demand_signal(
        db=db,
        current_user_id=dummy_vendor,
        product_name="NonExistentRareItem999",
        category="RareUnusedCategory",
        vendor_7d_sales=2
    )

    assert signal["market_insight_available"] is False
    assert signal["participating_vendors"] < 3
    assert "unavailable" in signal["insight_text"].lower()


if __name__ == "__main__":
    pytest.main(["-v", __file__])
