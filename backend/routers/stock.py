from datetime import datetime
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from database import get_db
from auth import CurrentUser
import models
import schemas
from services.recommendation_engine import StockRecommendationEngine
from services.market_intelligence import MarketIntelligenceEngine

router = APIRouter(prefix="/api/stock", tags=["Stock Recommendations & Market Intelligence"])


@router.post("/recommend", response_model=schemas.StockRecommendationResponse)
def get_single_recommendation(
    body: schemas.StockRecommendationRequest,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    """
    Authoritative single-product stock recommendation strictly scoped to authenticated user_id.
    Calculates demand forecast, safety stock, MOQ rounding, and cross-vendor market signals.
    """
    if not body.product_id and not body.product_name:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Either product_id or product_name must be provided."
        )

    query = db.query(models.Product).filter(models.Product.user_id == user_id)
    if body.product_id:
        query = query.filter(models.Product.id == body.product_id)
    elif body.product_name:
        query = query.filter(models.Product.name.ilike(body.product_name.strip()))

    product = query.first()
    if not product:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Product not found in your store catalog."
        )

    return StockRecommendationEngine.generate_recommendation_for_product(
        db=db,
        user_id=user_id,
        product=product,
        forecast_days=max(1, min(30, body.forecast_days))
    )


@router.get("/recommendations", response_model=schemas.BulkStockRecommendationResponse)
def get_bulk_recommendations(
    user_id: CurrentUser,
    forecast_days: int = Query(7, ge=1, le=30),
    status_filter: Optional[str] = Query(None),
    db: Session = Depends(get_db)
):
    """
    Returns prioritized bulk restock recommendations for all products in the authenticated vendor's inventory.
    Priority order: RESTOCK > LOW_STOCK > OVERSTOCK > STOCK_OK.
    Zero competitor leakage.
    """
    bulk_result = StockRecommendationEngine.generate_bulk_recommendations(
        db=db,
        user_id=user_id,
        forecast_days=forecast_days
    )

    if status_filter:
        filtered = [r for r in bulk_result.recommendations if r.status.upper() == status_filter.upper()]
        bulk_result.recommendations = filtered

    return bulk_result


@router.get("/market-trends", response_model=List[schemas.MarketTrendInsight])
def get_market_trends(
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    """
    Returns high-level anonymized cross-vendor market demand opportunities across participating stores.
    Minimum 3 vendors rule enforced for privacy.
    """
    # Fetch top bulk recommendations to extract high market demand products
    bulk_result = StockRecommendationEngine.generate_bulk_recommendations(
        db=db,
        user_id=user_id,
        forecast_days=7
    )

    market_insights: List[schemas.MarketTrendInsight] = []
    for rec in bulk_result.recommendations:
        if rec.market.market_insight_available and rec.market.demand_level in ["HIGH", "VERY_HIGH"]:
            badge = "🔥 High Market Demand" if rec.market.demand_level == "VERY_HIGH" else "📈 Market Trend"
            market_insights.append(
                schemas.MarketTrendInsight(
                    title=f"High Demand for {rec.product_name}",
                    description=rec.market.insight_text or f"Surge in {rec.product_name} sales across network.",
                    recommended_product=rec.product_name,
                    action_type="RESTOCK" if rec.status in ["RESTOCK", "LOW_STOCK"] else "MONITOR",
                    badge_label=badge
                )
            )

    return market_insights[:5]
