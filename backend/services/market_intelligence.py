from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional
from sqlalchemy import func
from sqlalchemy.orm import Session
import models


class MarketIntelligenceEngine:
    """
    Cross-Vendor Market Demand Intelligence Engine:
    - Aggregates anonymous demand signals across participating vendors.
    - Privacy Guard: Strict Minimum 3 participating vendors rule.
    - NEVER exposes competitor identity, store names, or individual competitor sales.
    """

    MIN_PARTICIPATING_VENDORS = 3

    @classmethod
    def get_market_demand_signal(
        cls,
        db: Session,
        current_user_id: str,
        product_name: str,
        category: str,
        vendor_7d_sales: int,
        reference_date: Optional[datetime] = None,
        prefetched_vendor_sales: Optional[Dict[str, int]] = None
    ) -> Dict[str, Any]:
        """
        Computes anonymized cross-vendor market demand signal for product_name / category.
        """
        if prefetched_vendor_sales is not None:
            participating_vendor_count = len(prefetched_vendor_sales)
            total_network_sales = sum(prefetched_vendor_sales.values())
        else:
            ref_dt = reference_date or datetime.utcnow()
            last_7d_start = (ref_dt - timedelta(days=7)).replace(hour=0, minute=0, second=0, microsecond=0)

            # Query total 7-day sales grouped by user_id across the entire network
            vendor_sales_query = db.query(
                models.Bill.user_id,
                func.coalesce(func.sum(models.BillItem.quantity), 0).label("qty_sold")
            ).join(
                models.BillItem, models.BillItem.bill_id == models.Bill.id
            ).filter(
                models.BillItem.product_name == product_name,
                models.Bill.created_at >= last_7d_start,
                models.Bill.created_at <= ref_dt
            ).group_by(
                models.Bill.user_id
            ).all()

            participating_vendor_count = len(vendor_sales_query)
            total_network_sales = sum(int(r.qty_sold or 0) for r in vendor_sales_query)


        # Privacy Guard: If fewer than 3 vendors participate, suppress cross-vendor statistics
        if participating_vendor_count < cls.MIN_PARTICIPATING_VENDORS:
            return {
                "market_insight_available": False,
                "demand_level": "NORMAL",
                "comparison_percentage": 100.0,
                "market_average_sales": float(vendor_7d_sales),
                "participating_vendors": participating_vendor_count,
                "insight_text": "Cross-vendor market signal unavailable (minimum 3 participating vendors required)."
            }

        market_avg_7d = total_network_sales / float(participating_vendor_count)


        # Relative store positioning compared to market benchmark
        if market_avg_7d > 0:
            comparison_pct = round((vendor_7d_sales / market_avg_7d) * 100.0, 1)
        else:
            comparison_pct = 100.0

        # Market Demand Level Classification
        if market_avg_7d >= 32.0:
            demand_level = "VERY_HIGH"
            level_text = "🔥 Very High"
        elif market_avg_7d >= 18.0:
            demand_level = "HIGH"
            level_text = "📈 High"
        elif market_avg_7d >= 6.0:
            demand_level = "NORMAL"
            level_text = "⚖️ Normal"
        else:
            demand_level = "LOW"
            level_text = "📉 Low"

        # Formulate actionable, anonymous market insight
        if demand_level in ["HIGH", "VERY_HIGH"] and vendor_7d_sales < (market_avg_7d * 0.7):
            insight = f"Market demand is {level_text} across {participating_vendor_count} partner stores. Your store sales are below market average — strong opportunity to capture demand."
        elif demand_level in ["HIGH", "VERY_HIGH"]:
            insight = f"Market demand is {level_text} across participating kirana stores. High consumer momentum detected."
        elif demand_level == "LOW":
            insight = f"Market demand is currently low across participating stores. Avoid over-purchasing."
        else:
            insight = f"Stable demand across {participating_vendor_count} participating store network."

        return {
            "market_insight_available": True,
            "demand_level": demand_level,
            "comparison_percentage": comparison_pct,
            "market_average_sales": round(market_avg_7d, 1),
            "participating_vendors": participating_vendor_count,
            "insight_text": insight
        }
