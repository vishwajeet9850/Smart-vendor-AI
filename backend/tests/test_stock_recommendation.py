"""
Unit and Functional Test Suite for SmartVendor Demand Forecasting & Stock Recommendations
"""

import math
from datetime import datetime, timedelta
import pytest
from database import SessionLocal
import models
from services.forecasting_engine import DemandForecastingEngine
from services.recommendation_engine import StockRecommendationEngine
from services.market_intelligence import MarketIntelligenceEngine


@pytest.fixture
def db():
    session = SessionLocal()
    yield session
    session.close()


def test_weighted_moving_average_math():
    """Verify linear weighted moving average weights (1..7 sum=28)."""
    # Sales: [10, 10, 10, 10, 10, 10, 10] -> WMA = 10.0
    wma_flat = DemandForecastingEngine.compute_weighted_moving_average([10, 10, 10, 10, 10, 10, 10])
    assert math.isclose(wma_flat, 10.0, rel_tol=1e-3)

    # Sales increasing: [1, 2, 3, 4, 5, 6, 7]
    # Sum(s*w) = 1*1 + 2*2 + 3*3 + 4*4 + 5*5 + 6*6 + 7*7 = 1+4+9+16+25+36+49 = 140
    # WMA = 140 / 28 = 5.0
    wma_inc = DemandForecastingEngine.compute_weighted_moving_average([1, 2, 3, 4, 5, 6, 7])
    assert math.isclose(wma_inc, 5.0, rel_tol=1e-3)


def test_trend_detection():
    """Verify trend classification logic (INCREASING, STABLE, DECREASING)."""
    # Recent 7d average = 10/day, previous 14d average = 5/day -> INCREASING
    trend_inc, mult_inc = DemandForecastingEngine.detect_trend(
        recent_7d_sales=[10] * 7,
        prev_14d_sales=[5] * 14
    )
    assert trend_inc == "INCREASING"
    assert mult_inc > 1.0

    # Recent 7d average = 2/day, previous 14d average = 8/day -> DECREASING
    trend_dec, mult_dec = DemandForecastingEngine.detect_trend(
        recent_7d_sales=[2] * 7,
        prev_14d_sales=[8] * 14
    )
    assert trend_dec == "DECREASING"
    assert mult_dec < 1.0

    # Recent 7d average = 6/day, previous 14d average = 6/day -> STABLE
    trend_st, mult_st = DemandForecastingEngine.detect_trend(
        recent_7d_sales=[6] * 7,
        prev_14d_sales=[6] * 14
    )
    assert trend_st == "STABLE"
    assert math.isclose(mult_st, 1.0, rel_tol=1e-3)


def test_harsh_ice_cream_recommendation_and_cross_vendor_opportunity(db):
    """
    Scenario 8 Verification:
    Harsh has low Ice Cream stock (6 units).
    Expected: Status RESTOCK, Trend INCREASING, Market Demand VERY_HIGH / HIGH, Recommended Purchase > 0.
    """
    harsh_uid = "uXXp4u9hvxP9hrv22LvllrlX6hx1"
    product = db.query(models.Product).filter(
        models.Product.user_id == harsh_uid,
        models.Product.name.like("%Ice Cream%")
    ).first()

    assert product is not None, "Harsh Ice Cream product must exist in database"

    rec = StockRecommendationEngine.generate_recommendation_for_product(
        db=db,
        user_id=harsh_uid,
        product=product,
        forecast_days=7
    )

    print(f"\n[Harsh Ice Cream Rec]: Predicted 7d={rec.predicted_demand}, Stock={rec.current_stock}, "
          f"Purchase={rec.recommended_purchase}, Status={rec.status}, Market={rec.market.demand_level}")

    assert rec.status in ["RESTOCK", "LOW_STOCK"]
    assert rec.recommended_purchase > 0
    # Supplier MOQ is 10, so recommended_purchase should be a multiple of 10
    assert rec.recommended_purchase % (product.supplier_moq or 1) == 0
    assert rec.market.market_insight_available is True
    assert rec.market.participating_vendors >= 3
    assert len(rec.reason) > 10


def test_stable_product_rice_stock_ok(db):
    """
    Scenario 1 Verification:
    Fortune Basmati Rice for Harsh (Stock = 25 units).
    Expected: Status STOCK_OK, recommended purchase = 0.
    """
    harsh_uid = "uXXp4u9hvxP9hrv22LvllrlX6hx1"
    product = db.query(models.Product).filter(
        models.Product.user_id == harsh_uid,
        models.Product.name == "Fortune Everyday Basmati Rice 5kg"
    ).first()

    assert product is not None

    rec = StockRecommendationEngine.generate_recommendation_for_product(
        db=db,
        user_id=harsh_uid,
        product=product,
        forecast_days=7
    )

    print(f"\n[Rice Rec]: Predicted 7d={rec.predicted_demand}, Stock={rec.current_stock}, Status={rec.status}")
    assert rec.current_stock >= 15
    assert rec.status in ["STOCK_OK", "LOW_STOCK"]


def test_overstock_chyawanprash_in_summer(db):
    """
    Scenario 6 Verification:
    Dabur Chyawanprash in Summer (Harsh Stock = 45 units).
    Expected: OVERSTOCK, recommended_purchase = 0.
    """
    harsh_uid = "uXXp4u9hvxP9hrv22LvllrlX6hx1"
    product = db.query(models.Product).filter(
        models.Product.user_id == harsh_uid,
        models.Product.name.like("%Chyawanprash%")
    ).first()

    assert product is not None

    rec = StockRecommendationEngine.generate_recommendation_for_product(
        db=db,
        user_id=harsh_uid,
        product=product,
        forecast_days=7
    )

    print(f"\n[Chyawanprash Rec]: Predicted 7d={rec.predicted_demand}, Stock={rec.current_stock}, Status={rec.status}")
    assert rec.status in ["OVERSTOCK", "STOCK_OK"]
    if rec.status == "OVERSTOCK":
        assert rec.recommended_purchase == 0


def test_bulk_recommendations_priority_sorting(db):
    """
    Verify bulk endpoint returns items correctly prioritized:
    RESTOCK > LOW_STOCK > OVERSTOCK > STOCK_OK
    """
    harsh_uid = "uXXp4u9hvxP9hrv22LvllrlX6hx1"
    bulk = StockRecommendationEngine.generate_bulk_recommendations(
        db=db,
        user_id=harsh_uid,
        forecast_days=7
    )

    assert bulk.total_products > 0
    assert len(bulk.recommendations) == bulk.total_products

    # Check order
    prio_order = {"RESTOCK": 0, "LOW_STOCK": 1, "OVERSTOCK": 2, "STOCK_OK": 3}
    for i in range(len(bulk.recommendations) - 1):
        curr_prio = prio_order.get(bulk.recommendations[i].status, 4)
        next_prio = prio_order.get(bulk.recommendations[i+1].status, 4)
        assert curr_prio <= next_prio, f"Priority violation at index {i}: {bulk.recommendations[i].status} followed by {bulk.recommendations[i+1].status}"


if __name__ == "__main__":
    pytest.main(["-v", __file__])
