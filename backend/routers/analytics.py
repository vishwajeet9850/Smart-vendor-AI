from datetime import datetime, timedelta
from typing import List, Optional
from fastapi import APIRouter, Depends, Query
from sqlalchemy import func
from sqlalchemy.orm import Session
from database import get_db
from auth import CurrentUser
import models
import schemas

router = APIRouter(prefix="/analytics", tags=["Analytics"])


@router.get("/summary", response_model=schemas.AnalyticsSummary)
def get_summary(
    user_id: CurrentUser,
    days: int = 30,
    range_type: Optional[str] = Query(None),
    tz_offset_minutes: int = 330,
    db: Session = Depends(get_db)
):
    """
    Authoritative analytics summary strictly scoped to the authenticated user_id.
    Fully optimized with database-level aggregations and timezone-aware day boundaries.
    """
    now = datetime.utcnow()
    tz_delta = timedelta(minutes=tz_offset_minutes)
    local_now = now + tz_delta
    local_today_start = local_now.replace(hour=0, minute=0, second=0, microsecond=0)
    today_start_utc = local_today_start - tz_delta

    # Determine date filter bounds relative to user's local timezone
    if range_type == "today" or days == 0 or (days == 1 and range_type != "yesterday"):
        start_time = today_start_utc
        end_time = None
    elif range_type == "yesterday" or (days == 2 and range_type == "yesterday"):
        start_time = today_start_utc - timedelta(days=1)
        end_time = today_start_utc
    elif range_type == "7days" or days == 7:
        start_time = today_start_utc - timedelta(days=6)
        end_time = None
    elif range_type == "30days" or days >= 30:
        start_time = today_start_utc - timedelta(days=days - 1)
        end_time = None
    else:
        start_time = now - timedelta(days=days)
        end_time = None

    # 1. Authoritative Total Revenue and Total Bills via direct SQL aggregation
    # 1. Authoritative Total Revenue (Sales minus Returns) and Total Bills
    base_filter = [
        models.Bill.user_id == user_id,
        models.Bill.created_at >= start_time
    ]
    if end_time:
        base_filter.append(models.Bill.created_at < end_time)

    sales_revenue = db.query(
        func.coalesce(func.sum(models.Bill.total_amount), 0.0)
    ).filter(*base_filter, models.Bill.transaction_type != "RETURN").scalar() or 0.0

    refunds_revenue = db.query(
        func.coalesce(func.sum(models.Bill.total_amount), 0.0)
    ).filter(*base_filter, models.Bill.transaction_type == "RETURN").scalar() or 0.0

    total_revenue = round(max(0.0, float(sales_revenue) - float(refunds_revenue)), 2)

    total_bills = db.query(
        func.count(models.Bill.id)
    ).filter(*base_filter, models.Bill.transaction_type != "RETURN").scalar() or 0
    total_bills = int(total_bills)

    # 2. Inventory counts
    total_products = db.query(func.count(models.Product.id)).filter(
        models.Product.user_id == user_id
    ).scalar() or 0

    low_stock_count = db.query(func.count(models.Product.id)).filter(
        models.Product.user_id == user_id,
        models.Product.stock <= models.Product.low_stock_threshold
    ).scalar() or 0

    # 3. Top selling products strictly for this user (excluding return transactions)
    items_filter = [
        models.Bill.user_id == user_id,
        models.Bill.transaction_type != "RETURN",
        models.Bill.created_at >= start_time
    ]
    if end_time:
        items_filter.append(models.Bill.created_at < end_time)

    top_products_raw = db.query(
        models.BillItem.product_name,
        func.coalesce(func.sum(models.BillItem.quantity), 0).label("total_qty"),
        func.coalesce(func.sum(models.BillItem.total_price), 0.0).label("total_sales")
    ).join(
        models.Bill, models.BillItem.bill_id == models.Bill.id
    ).filter(
        *items_filter
    ).group_by(
        models.BillItem.product_name
    ).order_by(
        func.sum(models.BillItem.total_price).desc()
    ).limit(5).all()

    top_products = [
        schemas.TopProduct(
            product_name=r[0],
            quantity_sold=int(r[1] or 0),
            revenue=round(float(r[2] or 0.0), 2)
        )
        for r in top_products_raw
    ]

    # 4. Daily revenue strictly for this user (grouped by local timezone calendar date, net of returns)
    tz_str = f"+{tz_offset_minutes} minutes"
    local_date_col = func.date(models.Bill.created_at, tz_str)

    daily_sales_raw = db.query(
        local_date_col.label("day"),
        func.coalesce(func.sum(models.Bill.total_amount), 0.0).label("rev"),
        func.count(models.Bill.id).label("cnt")
    ).filter(
        *base_filter, models.Bill.transaction_type != "RETURN"
    ).group_by(
        local_date_col
    ).all()
    sales_by_day = {r[0]: (float(r[1] or 0.0), int(r[2] or 0)) for r in daily_sales_raw}

    daily_returns_raw = db.query(
        local_date_col.label("day"),
        func.coalesce(func.sum(models.Bill.total_amount), 0.0).label("refunds")
    ).filter(
        *base_filter, models.Bill.transaction_type == "RETURN"
    ).group_by(
        local_date_col
    ).all()
    returns_by_day = {r[0]: float(r[1] or 0.0) for r in daily_returns_raw}

    all_days = sorted(set(sales_by_day.keys()) | set(returns_by_day.keys()))
    daily_revenue = [
        schemas.DailyRevenue(
            date=str(d),
            revenue=round(max(0.0, sales_by_day.get(d, (0.0, 0))[0] - returns_by_day.get(d, 0.0)), 2),
            bill_count=sales_by_day.get(d, (0.0, 0))[1]
        )
        for d in all_days
    ]


    # 5. Authoritative Multi-Type Stock Recommendations strictly for this user
    from services.recommendation_engine import StockRecommendationEngine

    bulk_recs = StockRecommendationEngine.generate_bulk_recommendations(
        db=db,
        user_id=user_id,
        forecast_days=7
    )

    stock_recommendations = []
    for rec in bulk_recs.recommendations:
        urgency = "HIGH" if rec.recommendation_type in ["URGENT_RESTOCK", "NEAR_EXPIRY"] else (
            "MEDIUM" if rec.recommendation_type in ["FESTIVAL_SURGE", "MARKET_TREND", "LOW_STOCK_BUFFER"] else "LOW"
        )
        stock_recommendations.append(
            schemas.StockRecommendationItem(
                product_id=rec.product_id,
                product_name=rec.product_name,
                current_stock=rec.current_stock,
                recommended_reorder=rec.recommended_purchase,
                category=rec.category,
                peak_window=rec.seasonal_profile,
                sales_velocity=f"{rec.trend.capitalize()} (~{rec.predicted_daily_demand}/day)",
                reasoning=rec.simple_reason or rec.reason,
                urgency_level=urgency,
                recommendation_type=rec.recommendation_type,
                recommendation_title=rec.recommendation_title,
                action_type=rec.action_type,
                action_label=rec.action_label,
                simple_reason=rec.simple_reason
            )
        )

    # Prioritize non-healthy stock recommendations, keeping top items
    active_recs = [r for r in stock_recommendations if r.recommendation_type != "HEALTHY_STOCK"]
    if not active_recs:
        active_recs = stock_recommendations

    return schemas.AnalyticsSummary(
        total_revenue=total_revenue,
        total_bills=total_bills,
        total_products=total_products,
        low_stock_count=low_stock_count,
        top_products=top_products,
        daily_revenue=daily_revenue,
        stock_recommendations=active_recs[:10],
        market_trends=[]
    )


