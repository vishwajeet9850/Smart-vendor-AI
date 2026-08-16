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
    now = datetime.utcnow()
    today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)

    # Determine exact date filter bounds
    if range_type == "today" or days == 0 or (days == 1 and range_type != "yesterday"):
        start_time = today_start
        end_time = None
    elif range_type == "yesterday" or (days == 2 and range_type == "yesterday"):
        start_time = today_start - timedelta(days=1)
        end_time = today_start
    else:
        start_time = now - timedelta(days=days)
        end_time = None

    # Check if user has bills under their user_id; if not, query all bills
    user_bill_count = db.query(func.count(models.Bill.id)).filter(models.Bill.user_id == user_id).scalar() or 0

    # Base query for bills
    if user_bill_count > 0:
        bill_query = db.query(models.Bill).filter(
            models.Bill.user_id == user_id,
            models.Bill.created_at >= start_time
        )
    else:
        bill_query = db.query(models.Bill).filter(
            models.Bill.created_at >= start_time
        )

    if end_time:
        bill_query = bill_query.filter(models.Bill.created_at < end_time)

    # Total revenue and bill count
    bills_list = bill_query.all()
    total_revenue = sum(b.total_amount for b in bills_list)
    total_bills = len(bills_list)

    # Total products and low stock count
    total_products = db.query(func.count(models.Product.id)).filter(
        models.Product.user_id == user_id
    ).scalar() or 0

    low_stock_count = db.query(func.count(models.Product.id)).filter(
        models.Product.user_id == user_id,
        models.Product.stock <= models.Product.low_stock_threshold
    ).scalar() or 0

    # Top products query with exact date bounds
    item_query = db.query(
        models.BillItem.product_name,
        func.sum(models.BillItem.quantity).label("qty"),
        func.sum(models.BillItem.total_price).label("rev")
    ).join(
        models.Bill, models.BillItem.bill_id == models.Bill.id
    ).filter(
        models.Bill.created_at >= start_time
    )

    if user_bill_count > 0:
        item_query = item_query.filter(models.Bill.user_id == user_id)

    if end_time:
        item_query = item_query.filter(models.Bill.created_at < end_time)

    top_products_raw = item_query.group_by(
        models.BillItem.product_name
    ).order_by(
        func.sum(models.BillItem.quantity).desc()
    ).limit(5).all()

    top_products = [
        schemas.TopProduct(
            product_name=row[0],
            quantity_sold=int(row[1]),
            revenue=float(row[2])
        )
        for row in top_products_raw
    ]

    # Daily revenue query with exact date bounds
    daily_query = db.query(
        func.date(models.Bill.created_at).label("day"),
        func.sum(models.Bill.total_amount).label("rev"),
        func.count(models.Bill.id).label("cnt")
    ).filter(
        models.Bill.created_at >= start_time
    )

    if user_bill_count > 0:
        daily_query = daily_query.filter(models.Bill.user_id == user_id)

    if end_time:
        daily_query = daily_query.filter(models.Bill.created_at < end_time)

    daily_raw = daily_query.group_by(
        func.date(models.Bill.created_at)
    ).order_by(
        func.date(models.Bill.created_at)
    ).all()

    daily_revenue = [
        schemas.DailyRevenue(
            date=str(row[0]),
            revenue=float(row[1]),
            bill_count=int(row[2])
        )
        for row in daily_raw
    ]

    # ─── Smart AI Peak Hour & Seasonal Stock Recommendations ─────────────────
    all_products = db.query(models.Product).filter(
        models.Product.user_id == user_id
    ).all()

    stock_recommendations = []
    last_7_days_start = now - timedelta(days=7)
    last_30_days_start = now - timedelta(days=30)

    for p in all_products:
        # Fetch bill items for this product in last 7 days
        item_filter_query = db.query(models.BillItem).join(
            models.Bill, models.BillItem.bill_id == models.Bill.id
        ).filter(
            (models.BillItem.product_id == p.id) | (models.BillItem.product_name == p.name),
            models.Bill.created_at >= last_7_days_start
        )
        if user_bill_count > 0:
            item_filter_query = item_filter_query.filter(models.Bill.user_id == user_id)

        items_7d = item_filter_query.all()

        qty_7d = sum(item.quantity for item in items_7d)
        daily_velocity = qty_7d / 7.0

        items_30d_query = db.query(func.coalesce(func.sum(models.BillItem.quantity), 0)).join(
            models.Bill, models.BillItem.bill_id == models.Bill.id
        ).filter(
            (models.BillItem.product_id == p.id) | (models.BillItem.product_name == p.name),
            models.Bill.created_at >= last_30_days_start
        )
        if user_bill_count > 0:
            items_30d_query = items_30d_query.filter(models.Bill.user_id == user_id)

        items_30d_qty = items_30d_query.scalar() or 0
        monthly_velocity = items_30d_qty / 30.0

        # Determine Peak Hour Window
        hour_counts = {
            "🌅 Morning Rush (6-11 AM)": 0,
            "☀️ Afternoon Demand (12-4 PM)": 0,
            "🌆 Evening Peak (5-9 PM)": 0,
            "🌙 Night Demand (10 PM-5 AM)": 0
        }
        for item in items_7d:
            if item.bill and item.bill.created_at:
                hour = item.bill.created_at.hour
                if 6 <= hour < 12:
                    hour_counts["🌅 Morning Rush (6-11 AM)"] += item.quantity
                elif 12 <= hour < 17:
                    hour_counts["☀️ Afternoon Demand (12-4 PM)"] += item.quantity
                elif 17 <= hour < 22:
                    hour_counts["🌆 Evening Peak (5-9 PM)"] += item.quantity
                else:
                    hour_counts["🌙 Night Demand (10 PM-5 AM)"] += item.quantity

        peak_window = max(hour_counts, key=hour_counts.get) if qty_7d > 0 else "General Demand"
        if hour_counts.get(peak_window, 0) == 0:
            peak_window = "General Demand"

        # Calculate 7d vs prior period velocity
        prev_7d_start = now - timedelta(days=14)
        items_prev_7d_query = db.query(func.coalesce(func.sum(models.BillItem.quantity), 0)).join(
            models.Bill, models.BillItem.bill_id == models.Bill.id
        ).filter(
            (models.BillItem.product_id == p.id) | (models.BillItem.product_name == p.name),
            models.Bill.created_at >= prev_7d_start,
            models.Bill.created_at < last_7_days_start
        )
        if user_bill_count > 0:
            items_prev_7d_query = items_prev_7d_query.filter(models.Bill.user_id == user_id)
        qty_prev_7d = items_prev_7d_query.scalar() or 0

        # Detect true demand surge (comparing week-over-week velocity)
        is_true_surge = (qty_prev_7d > 0 and qty_7d >= qty_prev_7d * 1.5 and qty_7d >= 3)
        is_low_stock = (p.stock <= p.low_stock_threshold or p.stock <= 5)
        is_out_of_stock = (p.stock == 0)

        if is_out_of_stock or is_low_stock or is_true_surge or daily_velocity >= 0.5 or qty_7d >= 2:
            buffer_days = 7
            target_stock = max(int(daily_velocity * buffer_days), 15)
            if is_true_surge:
                target_stock = int(target_stock * 1.5)

            reorder_amt = max(target_stock - p.stock, 10)

            # Assign distinct, informative velocity & demand badges
            if is_out_of_stock:
                urgency = "HIGH"
                velocity_text = "🚨 Out of Stock"
                reason = f"🚨 '{p.name.capitalize()}' is 100% OUT OF STOCK! ({items_30d_qty} sold recently). Immediate restock required ahead of {peak_window}."
            elif is_low_stock:
                urgency = "HIGH"
                velocity_text = f"⚠️ Low Stock ({p.stock} left)"
                reason = f"⚠️ Critical inventory level! Only {p.stock} units of '{p.name.capitalize()}' remaining. Peak demand observed during {peak_window}."
            elif is_true_surge:
                urgency = "MEDIUM"
                pct = int(((qty_7d - qty_prev_7d) / max(qty_prev_7d, 1)) * 100)
                velocity_text = f"⚡ Sudden Surge (+{pct}%)"
                reason = f"⚡ '{p.name.capitalize()}' ({p.category}) has a rapid demand surge (+{pct}% vs last week). Increase safety stock by +{reorder_amt} units."
            elif peak_window != "General Demand" and qty_7d >= 3:
                urgency = "MEDIUM"
                velocity_text = f"⏰ {peak_window.split(' ')[0]} Peak ({qty_7d}/wk)"
                reason = f"⏰ '{p.name.capitalize()}' sells heavily during {peak_window}. Ensure shelves are stocked before this window."
            elif daily_velocity >= 2.0:
                urgency = "MEDIUM"
                velocity_text = f"🔥 Fast Moving ({int(daily_velocity)}/day)"
                reason = f"🔥 '{p.name.capitalize()}' has high turnover ({int(daily_velocity)} sold per day). Reorder +{reorder_amt} units to prevent stockouts."
            else:
                urgency = "LOW"
                velocity_text = f"📈 Steady Demand ({qty_7d}/wk)"
                reason = f"📦 '{p.name.capitalize()}' ({p.category}) maintains steady demand ({qty_7d} sold this week). Reorder +{reorder_amt} for {peak_window}."

            stock_recommendations.append(
                schemas.StockRecommendationItem(
                    product_id=p.id,
                    product_name=p.name,
                    current_stock=p.stock,
                    recommended_reorder=reorder_amt,
                    category=p.category,
                    peak_window=peak_window,
                    sales_velocity=velocity_text,
                    reasoning=reason,
                    urgency_level=urgency
                )
            )

    # Sort high urgency first
    stock_recommendations.sort(
        key=lambda x: (0 if x.urgency_level == "HIGH" else 1 if x.urgency_level == "MEDIUM" else 2, x.current_stock)
    )
    stock_recommendations = stock_recommendations[:10]

    # ─── Cross-Vendor Market Trends & Benchmark Analytics ───────────────────────
    market_trends = []

    # 1. Top Selling Products Across ALL Vendors in Platform
    global_top_raw = db.query(
        models.BillItem.product_name,
        func.sum(models.BillItem.quantity).label("global_qty")
    ).join(
        models.Bill, models.BillItem.bill_id == models.Bill.id
    ).filter(
        models.Bill.created_at >= last_30_days_start
    ).group_by(
        models.BillItem.product_name
    ).order_by(
        func.sum(models.BillItem.quantity).desc()
    ).limit(5).all()

    vendor_product_names = set(p.name.lower() for p in all_products)

    for g_name, g_qty in global_top_raw:
        if g_name.lower() not in vendor_product_names:
            market_trends.append(
                schemas.MarketTrendInsight(
                    title=f"💡 Market Opportunity: {g_name}",
                    description=f"'{g_name}' is high-selling across local vendors ({g_qty} units sold platform-wide). Consider adding this item to your catalog!",
                    recommended_product=g_name,
                    action_type="ADD_PRODUCT",
                    badge_label="🌐 Popular Across Vendors"
                )
            )
        else:
            market_trends.append(
                schemas.MarketTrendInsight(
                    title=f"🔥 High Platform Demand: {g_name}",
                    description=f"'{g_name}' is currently a top seller across local vendors. Ensure sufficient stock to meet market demand.",
                    recommended_product=g_name,
                    action_type="RESTOCK",
                    badge_label="📈 High Market Demand"
                )
            )

    # 2. Cross-Vendor Top Category Trend
    global_cat_raw = db.query(
        models.Product.category,
        func.sum(models.BillItem.quantity).label("cat_qty")
    ).join(
        models.BillItem, models.BillItem.product_id == models.Product.id
    ).join(
        models.Bill, models.BillItem.bill_id == models.Bill.id
    ).filter(
        models.Bill.created_at >= last_30_days_start
    ).group_by(
        models.Product.category
    ).order_by(
        func.sum(models.BillItem.quantity).desc()
    ).first()

    if global_cat_raw and global_cat_raw[0]:
        top_cat_name, top_cat_qty = global_cat_raw[0], global_cat_raw[1]
        market_trends.append(
            schemas.MarketTrendInsight(
                title=f"📊 Category Trend: {top_cat_name}",
                description=f"{top_cat_name} is the #1 selling category across local vendors ({top_cat_qty} units sold). Keep your {top_cat_name} stock well replenished.",
                recommended_product=top_cat_name,
                action_type="TREND_ALERT",
                badge_label="🏆 Top Category"
            )
        )

    return schemas.AnalyticsSummary(
        total_revenue=total_revenue,
        total_bills=total_bills,
        total_products=total_products,
        low_stock_count=low_stock_count,
        top_products=top_products,
        daily_revenue=daily_revenue,
        stock_recommendations=stock_recommendations,
        market_trends=market_trends[:5]
    )
