import math
from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional
from sqlalchemy.orm import Session
import models
import schemas
from services.forecasting_engine import DemandForecastingEngine
from services.market_intelligence import MarketIntelligenceEngine


class StockRecommendationEngine:
    """
    Production-structured Stock Recommendation Engine:
    - Combines Predicted Demand + Safety Stock (default 20%) + Supplier MOQ Rounding
    - Stock Status: RESTOCK, LOW_STOCK, STOCK_OK, OVERSTOCK
    - Expiry-Aware Guard
    - Cross-Vendor Market Demand Integration
    - Transparent Explainable AI Reason Generator
    """

    DEFAULT_SAFETY_STOCK_PCT = 0.20

    @classmethod
    def generate_recommendation_for_product(
        cls,
        db: Session,
        user_id: str,
        product: models.Product,
        forecast_days: int = 7,
        safety_stock_pct: float = DEFAULT_SAFETY_STOCK_PCT,
        reference_date: Optional[datetime] = None,
        prefetched_daily_sales: Optional[Dict[str, int]] = None,
        prefetched_monthly_sales: Optional[Dict[int, int]] = None,
        prefetched_vendor_sales: Optional[Dict[str, int]] = None
    ) -> schemas.StockRecommendationResponse:
        """
        Calculates authoritative stock recommendation for a specific product.
        """
        ref_dt = reference_date or datetime.utcnow()

        # 1. Compute Demand Forecast
        forecast = DemandForecastingEngine.forecast_demand(
            db=db,
            user_id=user_id,
            product=product,
            forecast_days=forecast_days,
            reference_date=ref_dt,
            prefetched_daily_sales=prefetched_daily_sales,
            prefetched_monthly_sales=prefetched_monthly_sales
        )

        predicted_daily = forecast["predicted_daily_demand"]
        predicted_demand = forecast["predicted_demand"]
        trend = forecast["trend"]
        seasonal_profile = product.seasonal_profile or "STABLE"
        seasonal_factor = forecast["seasonal_factor"]
        seasonal_desc = forecast["seasonal_desc"]
        sales_7d_total = forecast["sales_7d_total"]

        # 2. Safety Stock & Target Stock
        safety_stock = int(math.ceil(predicted_demand * safety_stock_pct))
        target_stock = predicted_demand + safety_stock
        current_stock = max(0, product.stock)

        # 3. Calculate Raw Purchase
        raw_purchase = max(0, target_stock - current_stock)

        # 4. Supplier MOQ Rounding
        moq = max(1, product.supplier_moq or 1)
        if raw_purchase > 0 and moq > 1:
            recommended_purchase = int(math.ceil(raw_purchase / float(moq)) * moq)
        else:
            recommended_purchase = raw_purchase

        # 5. Cross-Vendor Market Signal
        market_info_dict = MarketIntelligenceEngine.get_market_demand_signal(
            db=db,
            current_user_id=user_id,
            product_name=product.name,
            category=product.category,
            vendor_7d_sales=sales_7d_total,
            reference_date=ref_dt,
            prefetched_vendor_sales=prefetched_vendor_sales
        )
        market_info = schemas.MarketDemandInfo(**market_info_dict)

        # 6. Expiry-Aware Guard
        is_near_expiry = False
        days_to_expiry = None
        if product.expiry_date:
            days_to_expiry = (product.expiry_date - ref_dt).days
            if 0 <= days_to_expiry <= 14 and current_stock >= max(2, int(predicted_demand * 0.3)):
                is_near_expiry = True

        # 7. Multi-Type Recommendation Classification & Explainability
        rec_details = cls._classify_recommendation(
            product_name=product.name,
            category=product.category,
            current_stock=current_stock,
            predicted_daily=predicted_daily,
            predicted_demand=predicted_demand,
            target_stock=target_stock,
            raw_purchase=raw_purchase,
            recommended_purchase=recommended_purchase,
            forecast_days=forecast_days,
            trend=trend,
            seasonal_profile=seasonal_profile,
            seasonal_factor=seasonal_factor,
            seasonal_desc=seasonal_desc,
            market_info=market_info,
            is_near_expiry=is_near_expiry,
            days_to_expiry=days_to_expiry,
            moq=moq
        )

        status = rec_details["status"]
        final_purchase = rec_details["recommended_purchase"]
        rec_type = rec_details["recommendation_type"]
        rec_title = rec_details["recommendation_title"]
        act_type = rec_details["action_type"]
        act_label = rec_details["action_label"]
        reason = rec_details["reason"]
        simple_reason = rec_details["simple_reason"]

        return schemas.StockRecommendationResponse(
            product_id=product.id,
            product_name=product.name,
            category=product.category,
            current_stock=current_stock,
            predicted_daily_demand=predicted_daily,
            predicted_demand=predicted_demand,
            safety_stock=safety_stock,
            target_stock=target_stock,
            recommended_purchase=final_purchase,
            status=status,
            trend=trend,
            seasonal_profile=seasonal_profile,
            seasonal_factor=seasonal_factor,
            supplier_moq=moq,
            unit=product.unit or "pcs",
            market=market_info,
            reason=reason,
            recommendation_type=rec_type,
            recommendation_title=rec_title,
            action_type=act_type,
            action_label=act_label,
            simple_reason=simple_reason
        )

    @classmethod
    def generate_bulk_recommendations(
        cls,
        db: Session,
        user_id: str,
        forecast_days: int = 7,
        safety_stock_pct: float = DEFAULT_SAFETY_STOCK_PCT,
        reference_date: Optional[datetime] = None
    ) -> schemas.BulkStockRecommendationResponse:
        """
        High-performance bulk stock recommendation generator with batched SQL pre-fetching.
        Replaces 300+ individual queries with 3 fast composite index queries.
        """
        ref_dt = reference_date or datetime.utcnow()
        from sqlalchemy import func

        products = db.query(models.Product).filter(
            models.Product.user_id == user_id
        ).all()

        if not products:
            return schemas.BulkStockRecommendationResponse(
                recommendations=[],
                total_products=0,
                restock_count=0,
                low_stock_count=0,
                overstock_count=0,
                optimal_count=0,
                generated_at=ref_dt
            )

        # 1. Batch fetch 21-day sales for this user in ONE query
        start_21d = (ref_dt - timedelta(days=21)).replace(hour=0, minute=0, second=0, microsecond=0)
        daily_raw = db.query(
            models.BillItem.product_name,
            models.BillItem.product_id,
            func.date(models.Bill.created_at).label("dt"),
            func.sum(models.BillItem.quantity).label("qty")
        ).join(
            models.Bill, models.BillItem.bill_id == models.Bill.id
        ).filter(
            models.Bill.user_id == user_id,
            models.Bill.transaction_type != "RETURN",
            models.Bill.created_at >= start_21d,
            models.Bill.created_at <= ref_dt
        ).group_by(
            models.BillItem.product_name,
            models.BillItem.product_id,
            func.date(models.Bill.created_at)
        ).all()

        product_daily_map: Dict[str, Dict[str, int]] = {}
        for r in daily_raw:
            p_name = r[0]
            p_id = r[1]
            dt_str = str(r[2])
            qty = int(r[3] or 0)
            if p_name:
                product_daily_map.setdefault(p_name, {})[dt_str] = qty
            if p_id:
                product_daily_map.setdefault(p_id, {})[dt_str] = qty

        # 2. Batch fetch 1-year monthly sales for seasonal analysis in ONE query
        start_1y = (ref_dt - timedelta(days=365)).replace(hour=0, minute=0, second=0, microsecond=0)
        monthly_raw = db.query(
            models.BillItem.product_name,
            models.BillItem.product_id,
            func.strftime("%m", models.Bill.created_at).label("month"),
            func.sum(models.BillItem.quantity).label("qty")
        ).join(
            models.Bill, models.BillItem.bill_id == models.Bill.id
        ).filter(
            models.Bill.user_id == user_id,
            models.Bill.transaction_type != "RETURN",
            models.Bill.created_at >= start_1y,
            models.Bill.created_at <= ref_dt
        ).group_by(
            models.BillItem.product_name,
            models.BillItem.product_id,
            func.strftime("%m", models.Bill.created_at)
        ).all()


        product_monthly_map: Dict[str, Dict[int, int]] = {}
        for r in monthly_raw:
            p_name = r[0]
            p_id = r[1]
            m_int = int(r[2]) if r[2] else 0
            qty = int(r[3] or 0)
            if p_name:
                product_monthly_map.setdefault(p_name, {})[m_int] = qty
            if p_id:
                product_monthly_map.setdefault(p_id, {})[m_int] = qty

        # 3. Batch fetch 7-day cross-vendor market sales in ONE query
        start_7d = (ref_dt - timedelta(days=7)).replace(hour=0, minute=0, second=0, microsecond=0)
        network_raw = db.query(
            models.BillItem.product_name,
            models.Bill.user_id,
            func.sum(models.BillItem.quantity).label("qty")
        ).join(
            models.Bill, models.BillItem.bill_id == models.Bill.id
        ).filter(
            models.Bill.created_at >= start_7d,
            models.Bill.created_at <= ref_dt
        ).group_by(
            models.BillItem.product_name,
            models.Bill.user_id
        ).all()

        network_map: Dict[str, Dict[str, int]] = {}
        for r in network_raw:
            p_name = r[0]
            u_id = r[1]
            qty = int(r[2] or 0)
            if p_name:
                network_map.setdefault(p_name, {})[u_id] = qty

        recs: List[schemas.StockRecommendationResponse] = []
        for p in products:
            p_daily = product_daily_map.get(p.id) or product_daily_map.get(p.name) or {}
            p_monthly = product_monthly_map.get(p.id) or product_monthly_map.get(p.name) or {}
            p_network = network_map.get(p.name) or {}

            rec = cls.generate_recommendation_for_product(
                db=db,
                user_id=user_id,
                product=p,
                forecast_days=forecast_days,
                safety_stock_pct=safety_stock_pct,
                reference_date=ref_dt,
                prefetched_daily_sales=p_daily,
                prefetched_monthly_sales=p_monthly,
                prefetched_vendor_sales=p_network
            )
            recs.append(rec)

        # Priority Sort:
        # 1. URGENT_RESTOCK
        # 2. FESTIVAL_SURGE
        # 3. MARKET_TREND
        # 4. LOW_STOCK_BUFFER
        # 5. NEAR_EXPIRY
        # 6. OVERSTOCK_CLEARANCE
        # 7. BUNDLE_OPPORTUNITY
        # 8. HEALTHY_STOCK
        type_priority = {
            "URGENT_RESTOCK": 0,
            "FESTIVAL_SURGE": 1,
            "MARKET_TREND": 2,
            "LOW_STOCK_BUFFER": 3,
            "NEAR_EXPIRY": 4,
            "OVERSTOCK_CLEARANCE": 5,
            "BUNDLE_OPPORTUNITY": 6,
            "HEALTHY_STOCK": 7
        }

        def sort_key(item: schemas.StockRecommendationResponse):
            prio = type_priority.get(item.recommendation_type, 8)
            gap = -(item.recommended_purchase)
            return (prio, gap, item.current_stock)

        recs.sort(key=sort_key)

        restock_count = sum(1 for r in recs if r.status == "RESTOCK")
        low_stock_count = sum(1 for r in recs if r.status == "LOW_STOCK")
        overstock_count = sum(1 for r in recs if r.status == "OVERSTOCK")
        optimal_count = sum(1 for r in recs if r.status == "STOCK_OK")

        return schemas.BulkStockRecommendationResponse(
            recommendations=recs,
            total_products=len(recs),
            restock_count=restock_count,
            low_stock_count=low_stock_count,
            overstock_count=overstock_count,
            optimal_count=optimal_count,
            generated_at=ref_dt
        )

    @classmethod
    def _classify_recommendation(
        cls,
        product_name: str,
        category: str,
        current_stock: int,
        predicted_daily: float,
        predicted_demand: int,
        target_stock: int,
        raw_purchase: int,
        recommended_purchase: int,
        forecast_days: int,
        trend: str,
        seasonal_profile: str,
        seasonal_factor: float,
        seasonal_desc: str,
        market_info: schemas.MarketDemandInfo,
        is_near_expiry: bool,
        days_to_expiry: Optional[int],
        moq: int
    ) -> Dict[str, Any]:
        """
        Classifies product into 8 actionable recommendation categories with plain-language explanations.
        """
        daily_pace = max(0.1, predicted_daily)
        days_left = max(0, int(current_stock / daily_pace))

        # ── 1. NEAR EXPIRY FLASH DEAL ──────────────────────────────────────────
        if is_near_expiry:
            exp_days = days_to_expiry if days_to_expiry is not None else 5
            simple_reason = (
                f"⏳ Expiry Warning: You have {current_stock} units expiring in {exp_days} days. "
                f"Normal customer sales (~{round(daily_pace, 1)}/day) won't clear them all in time. "
                f"Put them near the checkout counter with a 15–20% discount or 'Buy 1 Get 1' deal to recover cash and avoid waste."
            )
            return {
                "status": "OVERSTOCK",
                "recommendation_type": "NEAR_EXPIRY",
                "recommendation_title": "⏳ Expiry Flash Deal",
                "action_type": "DISCOUNT",
                "action_label": "Flash 20% Off",
                "recommended_purchase": 0,
                "reason": simple_reason,
                "simple_reason": simple_reason
            }

        # ── 2. URGENT RESTOCK (Out of stock or critically depleted) ────────────
        if current_stock == 0 or (current_stock <= 3 and predicted_daily >= 0.5) or (current_stock < int(target_stock * 0.35) and raw_purchase > 0):
            p_amt = recommended_purchase if recommended_purchase > 0 else max(10, int(target_stock - current_stock))
            if moq > 1:
                p_amt = int(math.ceil(p_amt / float(moq)) * moq)

            if current_stock == 0:
                simple_reason = (
                    f"🚨 Out of Stock: '{product_name}' is completely sold out! "
                    f"Your customers buy ~{round(daily_pace, 1)} units/day ({predicted_demand} units/week). "
                    f"You are losing customer sales right now. Reorder +{p_amt} units immediately."
                )
            else:
                simple_reason = (
                    f"🚨 Critical Low Stock: Only {current_stock} units left on your shelf for '{product_name}'. "
                    f"At your daily sales pace (~{round(daily_pace, 1)} units/day), you will run out in ~{days_left} day(s). "
                    f"Reorder +{p_amt} units today."
                )

            if moq > 1 and p_amt > raw_purchase:
                simple_reason += f" (Rounded up to supplier wholesale carton of {moq} units)."

            return {
                "status": "RESTOCK",
                "recommendation_type": "URGENT_RESTOCK",
                "recommendation_title": "🚨 Urgent Restock",
                "action_type": "RESTOCK",
                "action_label": f"Restock +{p_amt}",
                "recommended_purchase": p_amt,
                "reason": simple_reason,
                "simple_reason": simple_reason
            }

        # ── 3. FESTIVAL & SEASONAL SURGE ───────────────────────────────────────
        if seasonal_profile in ["SEASONAL", "FESTIVAL_SENSITIVE"] and seasonal_factor >= 1.25 and current_stock < int(target_stock * 1.3):
            p_amt = recommended_purchase if recommended_purchase > 0 else max(8, target_stock - current_stock)
            if moq > 1:
                p_amt = int(math.ceil(p_amt / float(moq)) * moq)
            surge_pct = int(round((seasonal_factor - 1.0) * 100))

            simple_reason = (
                f"🎉 Festival Demand Surge: Upcoming festival season ({seasonal_desc}) increases customer buying by +{surge_pct}% "
                f"({seasonal_factor}x normal demand). Your current shelf stock ({current_stock} units) is not enough for the peak. "
                f"Stock up +{p_amt} units in advance before rush."
            )
            return {
                "status": "RESTOCK" if current_stock < int(target_stock * 0.7) else "LOW_STOCK",
                "recommendation_type": "FESTIVAL_SURGE",
                "recommendation_title": "🎉 Festival Surge",
                "action_type": "FESTIVAL_ORDER",
                "action_label": f"Festival Stock-Up +{p_amt}",
                "recommended_purchase": p_amt,
                "reason": simple_reason,
                "simple_reason": simple_reason
            }

        # ── 4. CROSS-VENDOR MARKET TREND ───────────────────────────────────────
        if market_info.market_insight_available and market_info.demand_level in ["HIGH", "VERY_HIGH"] and current_stock < target_stock:
            p_amt = recommended_purchase if recommended_purchase > 0 else max(10, target_stock - current_stock)
            if moq > 1:
                p_amt = int(math.ceil(p_amt / float(moq)) * moq)

            simple_reason = (
                f"📈 High Market Demand: Partner kirana stores across Pune are seeing strong sales for '{product_name}' "
                f"({market_info.market_average_sales} units/week). Your shelf stock ({current_stock} units) is running low. "
                f"Reorder +{p_amt} units to capture rising neighborhood demand."
            )
            return {
                "status": "RESTOCK" if current_stock < int(target_stock * 0.6) else "LOW_STOCK",
                "recommendation_type": "MARKET_TREND",
                "recommendation_title": "📈 High Market Demand",
                "action_type": "MARKET_ORDER",
                "action_label": f"Trend Order +{p_amt}",
                "recommended_purchase": p_amt,
                "reason": simple_reason,
                "simple_reason": simple_reason
            }

        # ── 5. LOW STOCK SAFETY BUFFER ─────────────────────────────────────────
        if current_stock < target_stock:
            p_amt = recommended_purchase if recommended_purchase > 0 else max(5, target_stock - current_stock)
            if moq > 1:
                p_amt = int(math.ceil(p_amt / float(moq)) * moq)

            simple_reason = (
                f"🟡 Reorder Buffer Warning: Only {current_stock} units left on shelf. "
                f"Expected {forecast_days}-day sales is {predicted_demand} units. Stock will last ~{days_left} days. "
                f"Reorder +{p_amt} units to maintain a safe inventory buffer before weekend."
            )
            return {
                "status": "LOW_STOCK",
                "recommendation_type": "LOW_STOCK_BUFFER",
                "recommendation_title": "🟡 Reorder Buffer",
                "action_type": "REORDER",
                "action_label": f"Reorder +{p_amt}",
                "recommended_purchase": p_amt,
                "reason": simple_reason,
                "simple_reason": simple_reason
            }

        # ── 6. OVERSTOCK / DEAD STOCK CLEARANCE ────────────────────────────────
        if (current_stock > int(target_stock * 1.8) and current_stock >= 10) or (seasonal_factor <= 0.70 and current_stock >= 15):
            days_supply = int(current_stock / daily_pace)
            if seasonal_factor <= 0.70:
                simple_reason = (
                    f"❄️ Off-Season Slow Mover: High inventory ({current_stock} units) during low seasonal demand ({seasonal_desc}). "
                    f"Tying up working cash. Do not reorder. Offer a 10% discount to clear shelf space."
                )
            else:
                simple_reason = (
                    f"❄️ Overstock Alert: You have {current_stock} units in stock, which is {days_supply} days of supply "
                    f"(expected sales is {predicted_demand} units/week). Working capital is locked on shelf. "
                    f"Do not reorder; bundle or discount to recover cash."
                )

            return {
                "status": "OVERSTOCK",
                "recommendation_type": "OVERSTOCK_CLEARANCE",
                "recommendation_title": "❄️ Overstock Alert",
                "action_type": "PAUSE_REORDER",
                "action_label": "Pause & Clear Stock",
                "recommended_purchase": 0,
                "reason": simple_reason,
                "simple_reason": simple_reason
            }

        # ── 7. SMART COMBO OPPORTUNITY ─────────────────────────────────────────
        combo_categories = ["Biscuits & Snacks", "Instant Food", "Beverages", "Sweets"]
        if category in combo_categories and current_stock >= 12:
            simple_reason = (
                f"🔄 Smart Combo Opportunity: Customers frequently purchase '{product_name}' with tea or snacks. "
                f"Place it at the front checkout with a ₹5 combo discount to increase average bill size."
            )
            return {
                "status": "STOCK_OK",
                "recommendation_type": "BUNDLE_OPPORTUNITY",
                "recommendation_title": "🔄 Smart Combo",
                "action_type": "COMBO",
                "action_label": "Create Combo Deal",
                "recommended_purchase": 0,
                "reason": simple_reason,
                "simple_reason": simple_reason
            }

        # ── 8. HEALTHY / OPTIMAL STOCK ─────────────────────────────────────────
        simple_reason = (
            f"🟢 Healthy Stock: Your inventory of {current_stock} units is optimal for expected {forecast_days}-day sales "
            f"({predicted_demand} units) plus safety buffer. No purchase needed."
        )
        return {
            "status": "STOCK_OK",
            "recommendation_type": "HEALTHY_STOCK",
            "recommendation_title": "🟢 Healthy Stock",
            "action_type": "MAINTAIN",
            "action_label": "Stock Balanced",
            "recommended_purchase": 0,
            "reason": simple_reason,
            "simple_reason": simple_reason
        }


