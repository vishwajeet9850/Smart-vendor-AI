import math
from datetime import datetime, timedelta
from typing import Dict, List, Tuple, Any, Optional
from sqlalchemy import func, and_
from sqlalchemy.orm import Session
import models


class DemandForecastingEngine:
    """
    Production-structured Demand Forecasting Engine:
    - 7-day Linear Weighted Moving Average (weights 1..7)
    - Trend Detection (INCREASING, STABLE, DECREASING)
    - 1-Year Historical Seasonal Pattern Analysis & Bounded Seasonal Factors
    - Day-of-week & Festival sensitivity
    """

    @staticmethod
    def get_product_sales_series(
        db: Session,
        user_id: str,
        product_name: str,
        product_id: Optional[str] = None,
        days: int = 365,
        reference_date: Optional[datetime] = None
    ) -> Dict[str, int]:
        """
        Retrieves daily aggregated sales units for a specific product and user over `days`.
        Returns a dict mapping 'YYYY-MM-DD' -> total_quantity_sold.
        """
        ref_dt = reference_date or datetime.utcnow()
        start_date = (ref_dt - timedelta(days=days)).replace(hour=0, minute=0, second=0, microsecond=0)

        filter_cond = (models.BillItem.product_name == product_name)
        if product_id:
            filter_cond = (filter_cond | (models.BillItem.product_id == product_id))

        query = db.query(
            func.date(models.Bill.created_at).label("sale_date"),
            func.coalesce(func.sum(models.BillItem.quantity), 0).label("qty")
        ).join(
            models.Bill, models.BillItem.bill_id == models.Bill.id
        ).filter(
            models.Bill.user_id == user_id,
            models.Bill.transaction_type != "RETURN",
            filter_cond,
            models.Bill.created_at >= start_date,
            models.Bill.created_at <= ref_dt
        ).group_by(
            func.date(models.Bill.created_at)
        ).all()


        return {str(r.sale_date): int(r.qty or 0) for r in query}

    @staticmethod
    def compute_weighted_moving_average(daily_sales_7d: List[int]) -> float:
        """
        Linear Weighted Moving Average for recent 7 days:
        Weights: [1, 2, 3, 4, 5, 6, 7] (sum = 28)
        """
        if not daily_sales_7d:
            return 0.0

        # Ensure length 7 (pad with 0s if shorter)
        if len(daily_sales_7d) < 7:
            daily_sales_7d = [0] * (7 - len(daily_sales_7d)) + daily_sales_7d

        weights = [1, 2, 3, 4, 5, 6, 7]
        total_weighted_sales = sum(s * w for s, w in zip(daily_sales_7d, weights))
        total_weights = sum(weights)

        return total_weighted_sales / float(total_weights)

    @staticmethod
    def detect_trend(
        recent_7d_sales: List[int],
        prev_14d_sales: List[int]
    ) -> Tuple[str, float]:
        """
        Compares average daily sales of recent 7 days against previous 14 days.
        Returns (trend_str, trend_multiplier).
        """
        recent_avg = (sum(recent_7d_sales) / 7.0) if recent_7d_sales else 0.0
        prev_avg = (sum(prev_14d_sales) / 14.0) if prev_14d_sales else 0.0

        if recent_avg == 0.0 and prev_avg == 0.0:
            return "STABLE", 1.0

        if prev_avg == 0.0:
            return ("INCREASING", 1.15) if recent_avg > 0.5 else ("STABLE", 1.0)

        ratio = recent_avg / prev_avg

        if ratio >= 1.18 and recent_avg >= 0.5:
            return "INCREASING", min(1.25, 1.0 + (ratio - 1.0) * 0.5)
        elif ratio <= 0.82 and prev_avg >= 0.5:
            return "DECREASING", max(0.75, 1.0 - (1.0 - ratio) * 0.5)
        else:
            return "STABLE", 1.0

    @staticmethod
    def calculate_seasonal_factor(
        db: Session,
        user_id: str,
        product_name: str,
        seasonal_profile: str,
        target_month: int,
        product_id: Optional[str] = None,
        reference_date: Optional[datetime] = None,
        prefetched_monthly_sales: Optional[Dict[int, int]] = None
    ) -> Tuple[float, str]:
        """
        Computes historical seasonal factor by comparing the historical target month sales
        against the 1-year monthly baseline average.
        """
        if seasonal_profile not in ["SEASONAL", "FESTIVAL_SENSITIVE"]:
            return 1.0, "Stable Year-round Demand"

        if prefetched_monthly_sales is not None:
            month_map = prefetched_monthly_sales
        else:
            ref_dt = reference_date or datetime.utcnow()
            start_1y = (ref_dt - timedelta(days=365)).replace(hour=0, minute=0, second=0, microsecond=0)

            filter_cond = (models.BillItem.product_name == product_name)
            if product_id:
                filter_cond = (filter_cond | (models.BillItem.product_id == product_id))

            # Monthly aggregation strictly for this user & product
            monthly_query = db.query(
                func.strftime("%m", models.Bill.created_at).label("month"),
                func.coalesce(func.sum(models.BillItem.quantity), 0).label("qty")
            ).join(
                models.Bill, models.BillItem.bill_id == models.Bill.id
            ).filter(
                models.Bill.user_id == user_id,
                models.Bill.transaction_type != "RETURN",
                filter_cond,
                models.Bill.created_at >= start_1y,
                models.Bill.created_at <= ref_dt
            ).group_by(
                func.strftime("%m", models.Bill.created_at)
            ).all()


            month_map = {int(r.month): int(r.qty or 0) for r in monthly_query if r.month}

        if not month_map or sum(month_map.values()) == 0:
            return 1.0, "Standard Demand Baseline"

        total_annual_units = sum(month_map.values())
        avg_monthly_units = total_annual_units / 12.0

        target_month_units = month_map.get(target_month, 0)

        # Target month factor bounded between 0.35 and 2.4
        raw_factor = (target_month_units / avg_monthly_units) if avg_monthly_units > 0 else 1.0
        bounded_factor = max(0.35, min(2.4, raw_factor))

        if bounded_factor >= 1.4:
            desc = "☀️ Peak Seasonal / Festive Demand"
        elif bounded_factor >= 1.15:
            desc = "📈 Above-Average Seasonal Demand"
        elif bounded_factor <= 0.65:
            desc = "❄️ Off-Season Low Demand"
        else:
            desc = "⚖️ Normal Seasonal Baseline"

        return round(bounded_factor, 2), desc

    @classmethod
    def forecast_demand(
        cls,
        db: Session,
        user_id: str,
        product: models.Product,
        forecast_days: int = 7,
        reference_date: Optional[datetime] = None,
        prefetched_daily_sales: Optional[Dict[str, int]] = None,
        prefetched_monthly_sales: Optional[Dict[int, int]] = None
    ) -> Dict[str, Any]:
        """
        Complete mathematical forecasting pipeline:
        1. Fetch sales history for product (or use prefetched data)
        2. Extract latest 7 days for WMA
        3. Extract previous 14 days for Trend
        4. Calculate historical seasonal factor for current/upcoming month
        5. Forecast daily and total demand
        """
        ref_dt = reference_date or datetime.utcnow()
        if prefetched_daily_sales is not None:
            sales_by_date = prefetched_daily_sales
        else:
            sales_by_date = cls.get_product_sales_series(
                db=db,
                user_id=user_id,
                product_name=product.name,
                product_id=product.id,
                days=30,
                reference_date=ref_dt
            )

        # Build chronological sales for the last 21 days
        last_21_days_sales = []
        for i in range(20, -1, -1):
            dt_str = (ref_dt - timedelta(days=i)).strftime("%Y-%m-%d")
            last_21_days_sales.append(sales_by_date.get(dt_str, 0))

        # Recent 7 days: [day -6 .. day 0]
        recent_7d = last_21_days_sales[14:21]
        # Previous 14 days: [day -20 .. day -7]
        prev_14d = last_21_days_sales[0:14]

        # 1. Recent Demand Signal: Weighted Moving Average
        wma_daily = cls.compute_weighted_moving_average(recent_7d)

        # Fallback if recent sales are 0 but 30-day or total history exists
        total_recent_sold = sum(sales_by_date.values())
        if wma_daily < 0.1 and total_recent_sold > 0:
            wma_daily = max(0.2, total_recent_sold / max(1, len(sales_by_date)))

        # 2. Trend Detection
        trend_label, trend_mult = cls.detect_trend(recent_7d, prev_14d)

        # 3. Seasonal Factor
        target_month = ref_dt.month
        seasonal_factor, seasonal_desc = cls.calculate_seasonal_factor(
            db=db,
            user_id=user_id,
            product_name=product.name,
            seasonal_profile=product.seasonal_profile or "STABLE",
            target_month=target_month,
            reference_date=ref_dt,
            prefetched_monthly_sales=prefetched_monthly_sales
        )

        # 4. Final Predicted Daily Demand
        predicted_daily_demand = max(0.0, wma_daily * trend_mult * seasonal_factor)

        # 5. Total Predicted Forecast
        raw_predicted_demand = predicted_daily_demand * forecast_days
        predicted_demand = int(math.ceil(raw_predicted_demand)) if raw_predicted_demand > 0.01 else 0

        # Totals for analytics
        sales_7d_total = sum(recent_7d)
        sales_30d_total = sum(
            sales_by_date.get((ref_dt - timedelta(days=i)).strftime("%Y-%m-%d"), 0)
            for i in range(30)
        )

        return {
            "wma_daily": round(wma_daily, 2),
            "trend": trend_label,
            "trend_multiplier": round(trend_mult, 2),
            "seasonal_factor": seasonal_factor,
            "seasonal_desc": seasonal_desc,
            "predicted_daily_demand": round(predicted_daily_demand, 2),
            "predicted_demand": predicted_demand,
            "sales_7d_total": sales_7d_total,
            "sales_30d_total": sales_30d_total,
            "total_365d_sold": total_recent_sold,
            "forecast_days": forecast_days
        }

