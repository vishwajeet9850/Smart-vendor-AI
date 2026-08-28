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
    db: Session = Depends(get_db)
):
    """
    Authoritative analytics summary strictly scoped to the authenticated user_id.
    Zero cross-user data leakage.
    """
    now = datetime.utcnow()
    today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)

    # Determine date filter bounds
    if range_type == "today" or days == 0 or (days == 1 and range_type != "yesterday"):
        start_time = today_start
        end_time = None
    elif range_type == "yesterday" or (days == 2 and range_type == "yesterday"):
        start_time = today_start - timedelta(days=1)
        end_time = today_start
    else:
        start_time = now - timedelta(days=days)
        end_time = None

    # Strictly user-scoped bill query
    bill_query = db.query(models.Bill).filter(
        models.Bill.user_id == user_id,
        models.Bill.created_at >= start_time
    )

    if end_time:
        bill_query = bill_query.filter(models.Bill.created_at < end_time)

    bills_list = bill_query.all()
    total_revenue = round(sum(b.total_amount for b in bills_list), 2)
    total_bills = len(bills_list)

    # User inventory counts
    total_products = db.query(func.count(models.Product.id)).filter(
        models.Product.user_id == user_id
    ).scalar() or 0

    low_stock_count = db.query(func.count(models.Product.id)).filter(
        models.Product.user_id == user_id,
        models.Product.stock <= models.Product.low_stock_threshold
    ).scalar() or 0

    # Top selling products strictly for this user
    items_query = db.query(
        models.BillItem.product_name,
        func.sum(models.BillItem.quantity).label("total_qty"),
        func.sum(models.BillItem.total_price).label("total_sales")
    ).join(
        models.Bill, models.BillItem.bill_id == models.Bill.id
    ).filter(
        models.Bill.user_id == user_id,
        models.Bill.created_at >= start_time
    )

    if end_time:
        items_query = items_query.filter(models.Bill.created_at < end_time)

    top_products_raw = items_query.group_by(
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

    # Daily revenue strictly for this user
    daily_query = db.query(
        func.date(models.Bill.created_at).label("day"),
        func.sum(models.Bill.total_amount).label("rev"),
        func.count(models.Bill.id).label("cnt")
    ).filter(
        models.Bill.user_id == user_id,
        models.Bill.created_at >= start_time
    )

    if end_time:
        daily_query = daily_query.filter(models.Bill.created_at < end_time)

    daily_raw = daily_query.group_by(
        func.date(models.Bill.created_at)
    ).order_by(
        func.date(models.Bill.created_at)
    ).all()

    daily_revenue = [
        schemas.DailyRevenue(
            date=str(r[0]),
            revenue=round(float(r[1] or 0.0), 2),
            bill_count=int(r[2] or 0)
        )
        for r in daily_raw
    ]

    # Stock recommendations strictly calculated for this user
    stock_recommendations = []
    all_products = db.query(models.Product).filter(
        models.Product.user_id == user_id
    ).all()

    last_30_days_start = now - timedelta(days=30)
    last_7_days_start = now - timedelta(days=7)

    for p in all_products:
        items_30d_query = db.query(func.coalesce(func.sum(models.BillItem.quantity), 0)).join(
            models.Bill, models.BillItem.bill_id == models.Bill.id
        ).filter(
            models.Bill.user_id == user_id,
            (models.BillItem.product_id == p.id) | (models.BillItem.product_name == p.name),
            models.Bill.created_at >= last_30_days_start
        )
        items_30d_qty = items_30d_query.scalar() or 0
        daily_velocity = items_30d_qty / 30.0

        items_7d_query = db.query(func.coalesce(func.sum(models.BillItem.quantity), 0)).join(
            models.Bill, models.BillItem.bill_id == models.Bill.id
        ).filter(
            models.Bill.user_id == user_id,
            (models.BillItem.product_id == p.id) | (models.BillItem.product_name == p.name),
            models.Bill.created_at >= last_7_days_start
        )
        qty_7d = items_7d_query.scalar() or 0

        is_low_stock = (p.stock <= p.low_stock_threshold or p.stock <= 5)
        is_out_of_stock = (p.stock == 0)

        if is_out_of_stock or is_low_stock or daily_velocity >= 0.5 or qty_7d >= 2:
            target_stock = max(int(daily_velocity * 7), 15)
            reorder_amt = max(target_stock - p.stock, 10)

            if is_out_of_stock:
                urgency = "HIGH"
                velocity_text = "Out of Stock"
                reason = f"'{p.name}' is out of stock! ({items_30d_qty} sold recently). Restock required."
            elif is_low_stock:
                urgency = "HIGH"
                velocity_text = f"Low Stock ({p.stock} left)"
                reason = f"Only {p.stock} units of '{p.name}' remaining. Reorder +{reorder_amt} units."
            else:
                urgency = "MEDIUM"
                velocity_text = f"Fast Moving ({qty_7d}/wk)"
                reason = f"'{p.name}' maintains active sales ({qty_7d} sold this week). Recommended reorder: +{reorder_amt}."

            stock_recommendations.append(
                schemas.StockRecommendationItem(
                    product_id=p.id,
                    product_name=p.name,
                    current_stock=p.stock,
                    recommended_reorder=reorder_amt,
                    category=p.category,
                    peak_window="General Demand",
                    sales_velocity=velocity_text,
                    reasoning=reason,
                    urgency_level=urgency
                )
            )

    stock_recommendations.sort(
        key=lambda x: (0 if x.urgency_level == "HIGH" else 1 if x.urgency_level == "MEDIUM" else 2, x.current_stock)
    )

    return schemas.AnalyticsSummary(
        total_revenue=total_revenue,
        total_bills=total_bills,
        total_products=total_products,
        low_stock_count=low_stock_count,
        top_products=top_products,
        daily_revenue=daily_revenue,
        stock_recommendations=stock_recommendations[:10],
        market_trends=[]
    )
