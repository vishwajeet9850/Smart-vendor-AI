"""
SmartVendor — Multi-Vendor 365-Day Synthetic Market Seed Script
Generates deterministic, realistic 1-year historical sales, inventory, and cross-vendor demand patterns.
Supports Harsh, Himanshu, Vishwajeet, and Demo Partner.
Covers all 7 business recommendation scenarios:
1. Urgent Restock (Out of Stock / Critical Depletion)
2. Low Stock (Reorder Buffer Warning)
3. Festival & Seasonal Surge (Advance Stocking for Rakhi/Ganesh/Diwali)
4. Trending & High Market Momentum (Cross-Vendor Partner Velocity)
5. Overstock / Dead Stock Clearance (Off-season cash recovery)
6. Near Expiry (Flash Discount Opportunity)
7. Optimal & Balanced Healthy Stock
"""

import math
import random
import sys
import uuid
from datetime import datetime, timedelta
from typing import List, Dict, Any

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

from database import SessionLocal, engine, Base
import models


# Ensure database tables exist
Base.metadata.create_all(bind=engine)

# Deterministic seed for reproducible evaluation
RANDOM_SEED = 42
random.seed(RANDOM_SEED)

# Multi-Vendor Profile Definitions
DEMO_VENDORS = [
    {
        "id": "uXXp4u9hvxP9hrv22LvllrlX6hx1",  # Harsh Firebase UID
        "name": "Harsh Sadgir",
        "shop_name": "Harsh Kirana & General Store",
        "phone": "9876543210",
        "address": "Shop 4, Market Yard, Pune",
        "vendor_scale": 1.0
    },
    {
        "id": "lV3NgaMqXxerZHZlLjjilXhJTL22",  # Himanshu Firebase UID
        "name": "Himanshu Sawant",
        "shop_name": "Himanshu Super Mart",
        "phone": "9876543211",
        "address": "Bazaar Road, Shivaji Nagar, Pune",
        "vendor_scale": 1.35
    },
    {
        "id": "wmPdaGXXkGRfiglryt7T7QS0eWG3",  # Vishwajeet Firebase UID
        "name": "Vishwajeet Shelke",
        "shop_name": "Vishwajeet Provision Store",
        "phone": "9876543212",
        "address": "Main Road, Kothrud, Pune",
        "vendor_scale": 1.45
    },
    {
        "id": "demo_vendor_444",  # 4th Participating Network Vendor
        "name": "Anil Kumar (Demo Partner)",
        "shop_name": "SmartVendor Express Kirana",
        "phone": "9876543213",
        "address": "Station Road, Pune",
        "vendor_scale": 1.15
    },
    # Also seed alias 'harsh' for legacy / local testing
    {
        "id": "harsh",
        "name": "Harsh (Local Dev)",
        "shop_name": "Harsh Kirana Store (Dev)",
        "phone": "9876543210",
        "address": "Shop 4, Market Yard, Pune",
        "vendor_scale": 1.0
    }
]

# Product Catalog Templates with specific Demand Profiles & Stock Scenarios
PRODUCT_DEFINITIONS = [
    # ── 1. URGENT RESTOCK (Out of Stock / Critical Depletion) ──
    {
        "name": "Amul Taaza Toned Milk 1L",
        "category": "Dairy",
        "price": 54.0,
        "base_daily_demand": 10.0,
        "profile": "STABLE",
        "moq": 12,
        "unit": "pack",
        "harsh_stock": 0,       # 🚨 OUT OF STOCK for Harsh
        "himanshu_stock": 6,    # 🟡 LOW STOCK BUFFER for Himanshu
        "vishwajeet_stock": 8,  # 📈 MARKET TREND for Vishwajeet
        "demo_stock": 25,
        "expiry_days": None
    },
    {
        "name": "Maggi 2-Minute Masala Instant Noodles",
        "category": "Instant Food",
        "price": 14.0,
        "base_daily_demand": 12.0,
        "profile": "TRENDING",
        "moq": 24,
        "unit": "pack",
        "harsh_stock": 2,       # 🚨 CRITICAL LOW for Harsh
        "himanshu_stock": 10,   # 📈 MARKET TREND for Himanshu
        "vishwajeet_stock": 8,  # 🟡 LOW STOCK for Vishwajeet
        "demo_stock": 60,
        "expiry_days": None
    },

    # ── 2. LOW STOCK (Reorder Buffer Warning) ──
    {
        "name": "Aashirvaad Shudh Chakki Atta 5kg",
        "category": "Groceries",
        "price": 270.0,
        "base_daily_demand": 5.0,
        "profile": "STABLE",
        "moq": 5,
        "unit": "pack",
        "harsh_stock": 4,       # 🟡 LOW STOCK BUFFER for Harsh
        "himanshu_stock": 55,   # ❄️ OVERSTOCK for Himanshu
        "vishwajeet_stock": 1,  # 🚨 URGENT RESTOCK for Vishwajeet
        "demo_stock": 40,
        "expiry_days": None
    },
    {
        "name": "Fortune Sunlite Sunflower Oil 1L",
        "category": "Oils",
        "price": 145.0,
        "base_daily_demand": 5.0,
        "profile": "STABLE",
        "moq": 6,
        "unit": "pouch",
        "harsh_stock": 4,       # 🟡 LOW STOCK for Harsh
        "himanshu_stock": 0,    # 🚨 OUT OF STOCK for Himanshu
        "vishwajeet_stock": 5,  # 🟡 LOW STOCK for Vishwajeet
        "demo_stock": 35,
        "expiry_days": None
    },

    # ── 3. FESTIVAL & SEASONAL SURGE (Advance Stocking for Rakhi/Ganesh/Diwali) ──
    {
        "name": "Haldiram Gulab Jamun Tin 1kg",
        "category": "Sweets",
        "price": 240.0,
        "base_daily_demand": 2.5,
        "profile": "FESTIVAL_SENSITIVE",
        "moq": 6,
        "unit": "tin",
        "harsh_stock": 3,       # 🎉 FESTIVAL SURGE for Harsh
        "himanshu_stock": 4,    # 🟡 LOW STOCK for Himanshu
        "vishwajeet_stock": 2,  # 🎉 FESTIVAL SURGE for Vishwajeet
        "demo_stock": 25,
        "expiry_days": None
    },
    {
        "name": "Happilo Premium California Almonds 500g",
        "category": "Dry Fruits",
        "price": 450.0,
        "base_daily_demand": 2.0,
        "profile": "FESTIVAL_SENSITIVE",
        "moq": 4,
        "unit": "pouch",
        "harsh_stock": 2,       # 🎉 FESTIVAL SURGE for Harsh
        "himanshu_stock": 2,    # 🎉 FESTIVAL SURGE for Himanshu
        "vishwajeet_stock": 1,  # 🚨 URGENT RESTOCK for Vishwajeet
        "demo_stock": 20,
        "expiry_days": None
    },
    {
        "name": "Cadbury Celebrations Gift Box 130g",
        "category": "Biscuits & Snacks",
        "price": 150.0,
        "base_daily_demand": 3.5,
        "profile": "FESTIVAL_SENSITIVE",
        "moq": 10,
        "unit": "box",
        "harsh_stock": 4,       # 🎉 FESTIVAL SURGE for Harsh
        "himanshu_stock": 3,    # 🎉 FESTIVAL SURGE for Himanshu
        "vishwajeet_stock": 3,  # 🎉 FESTIVAL SURGE for Vishwajeet
        "demo_stock": 30,
        "expiry_days": None
    },

    # ── 4. TRENDING & HIGH MARKET MOMENTUM (Cross-Vendor Partner Velocity) ──
    {
        "name": "Appy Fizz Sparkling Apple Drink 600ml",
        "category": "Beverages",
        "price": 40.0,
        "base_daily_demand": 6.0,
        "profile": "SEASONAL",
        "moq": 12,
        "unit": "bottle",
        "harsh_stock": 8,       # 📈 HIGH MARKET DEMAND for Harsh
        "himanshu_stock": 12,   # 📈 HIGH MARKET DEMAND for Himanshu
        "vishwajeet_stock": 10, # 📈 HIGH MARKET DEMAND for Vishwajeet
        "demo_stock": 55,
        "expiry_days": None
    },
    {
        "name": "Parle Hide & Seek Chocolate Biscuits",
        "category": "Biscuits & Snacks",
        "price": 30.0,
        "base_daily_demand": 7.0,
        "profile": "TRENDING",
        "moq": 12,
        "unit": "pack",
        "harsh_stock": 5,       # 📈 FAST MOVING TREND for Harsh
        "himanshu_stock": 25,   # 🔄 COMBO OPPORTUNITY for Himanshu
        "vishwajeet_stock": 20, # 🔄 COMBO OPPORTUNITY for Vishwajeet
        "demo_stock": 50,
        "expiry_days": None
    },
    {
        "name": "Amul Vanilla Magic Ice Cream 1L",
        "category": "Dairy",
        "price": 150.0,
        "base_daily_demand": 4.0,
        "profile": "SEASONAL",
        "moq": 10,
        "unit": "tub",
        "harsh_stock": 6,       # 📈 MARKET DEMAND for Harsh
        "himanshu_stock": 2,    # 🚨 CRITICAL LOW for Himanshu
        "vishwajeet_stock": 35, # 🟢 HEALTHY STOCK for Vishwajeet
        "demo_stock": 45,
        "expiry_days": None
    },

    # ── 5. OVERSTOCK / DEAD STOCK CLEARANCE (Off-season cash recovery) ──
    {
        "name": "Dabur Chyawanprash 500g",
        "category": "Health & Wellness",
        "price": 225.0,
        "base_daily_demand": 1.0,
        "profile": "SEASONAL",
        "moq": 6,
        "unit": "jar",
        "harsh_stock": 48,      # ❄️ OFF-SEASON OVERSTOCK for Harsh
        "himanshu_stock": 42,   # ❄️ OVERSTOCK for Himanshu
        "vishwajeet_stock": 40, # ❄️ OVERSTOCK for Vishwajeet
        "demo_stock": 20,
        "expiry_days": None
    },
    {
        "name": "Vicks Vaporub 50g",
        "category": "Health & Wellness",
        "price": 140.0,
        "base_daily_demand": 0.8,
        "profile": "SEASONAL",
        "moq": 6,
        "unit": "jar",
        "harsh_stock": 36,      # ❄️ OVERSTOCK for Harsh
        "himanshu_stock": 35,   # ❄️ OVERSTOCK for Himanshu
        "vishwajeet_stock": 38, # ❄️ OVERSTOCK for Vishwajeet
        "demo_stock": 18,
        "expiry_days": None
    },

    # ── 6. NEAR EXPIRY (Flash Discount Opportunity) ──
    {
        "name": "Britannia 100% Whole Wheat Bread 400g",
        "category": "Bakery",
        "price": 45.0,
        "base_daily_demand": 3.0,
        "profile": "STABLE",
        "moq": 5,
        "unit": "pack",
        "harsh_stock": 16,      # ⏳ NEAR EXPIRY (Expires in 4 days) for Harsh
        "himanshu_stock": 2,    # 🚨 CRITICAL LOW for Himanshu
        "vishwajeet_stock": 15, # ⏳ NEAR EXPIRY (Expires in 4 days) for Vishwajeet
        "demo_stock": 10,
        "expiry_days": 4
    },
    {
        "name": "Amul Masti Spiced Buttermilk 200ml",
        "category": "Dairy",
        "price": 15.0,
        "base_daily_demand": 4.0,
        "profile": "SEASONAL",
        "moq": 12,
        "unit": "pouch",
        "harsh_stock": 24,      # ⏳ NEAR EXPIRY (Expires in 5 days) for Harsh
        "himanshu_stock": 28,   # ⏳ NEAR EXPIRY (Expires in 3 days) for Himanshu
        "vishwajeet_stock": 20, # ⏳ NEAR EXPIRY (Expires in 5 days) for Vishwajeet
        "demo_stock": 15,
        "expiry_days": 5
    },

    # ── 7. OPTIMAL & BALANCED HEALTHY STOCK ──
    {
        "name": "Tata Salt 1kg",
        "category": "Groceries",
        "price": 28.0,
        "base_daily_demand": 8.0,
        "profile": "STABLE",
        "moq": 10,
        "unit": "pkt",
        "harsh_stock": 45,      # 🟢 HEALTHY STOCK for Harsh
        "himanshu_stock": 55,   # 🟢 HEALTHY STOCK for Himanshu
        "vishwajeet_stock": 50, # 🟢 HEALTHY STOCK for Vishwajeet
        "demo_stock": 60,
        "expiry_days": None
    },
    {
        "name": "Sugar 1kg",
        "category": "Groceries",
        "price": 44.0,
        "base_daily_demand": 9.0,
        "profile": "STABLE",
        "moq": 10,
        "unit": "kg",
        "harsh_stock": 50,      # 🟢 HEALTHY STOCK for Harsh
        "himanshu_stock": 65,   # 🟢 HEALTHY STOCK for Himanshu
        "vishwajeet_stock": 60, # 🟢 HEALTHY STOCK for Vishwajeet
        "demo_stock": 70,
        "expiry_days": None
    },
    {
        "name": "Tata Tea Gold 250g",
        "category": "Beverages",
        "price": 140.0,
        "base_daily_demand": 4.0,
        "profile": "SEASONAL",
        "moq": 10,
        "unit": "box",
        "harsh_stock": 25,      # 🟢 HEALTHY STOCK for Harsh
        "himanshu_stock": 5,    # 🟡 LOW STOCK BUFFER for Himanshu
        "vishwajeet_stock": 0,  # 🚨 OUT OF STOCK for Vishwajeet
        "demo_stock": 35,
        "expiry_days": None
    },
    {
        "name": "Fortune Everyday Basmati Rice 5kg",
        "category": "Groceries",
        "price": 180.0,
        "base_daily_demand": 5.0,
        "profile": "STABLE",
        "moq": 5,
        "unit": "pack",
        "harsh_stock": 28,      # 🟢 HEALTHY STOCK for Harsh
        "himanshu_stock": 35,   # 🟢 HEALTHY STOCK for Himanshu
        "vishwajeet_stock": 30, # 🟢 HEALTHY STOCK for Vishwajeet
        "demo_stock": 40,
        "expiry_days": None
    },
    {
        "name": "Tata Sampann Toor Dal 1kg",
        "category": "Groceries",
        "price": 160.0,
        "base_daily_demand": 4.0,
        "profile": "STABLE",
        "moq": 5,
        "unit": "kg",
        "harsh_stock": 22,      # 🟢 HEALTHY STOCK for Harsh
        "himanshu_stock": 28,   # 🟢 HEALTHY STOCK for Himanshu
        "vishwajeet_stock": 25, # 🟢 HEALTHY STOCK for Vishwajeet
        "demo_stock": 30,
        "expiry_days": None
    }
]



def calculate_synthetic_day_sales(
    date: datetime,
    base_demand: float,
    profile: str,
    vendor_scale: float,
    is_harsh: bool,
    product_name: str
) -> int:
    """
    Computes realistic daily sales:
    base_demand * seasonal_multiplier * dow_multiplier * festival_multiplier * vendor_scale + noise
    """
    month = date.month
    dow = date.weekday()  # 0=Mon .. 6=Sun
    day = date.day

    # 1. Seasonal Multipliers
    if profile == "SEASONAL":
        if "Ice Cream" in product_name or "Drink" in product_name or "Water" in product_name or "Buttermilk" in product_name:
            summer_curve = {
                1: 0.40, 2: 0.55, 3: 1.25, 4: 1.85, 5: 2.20,
                6: 2.00, 7: 1.40, 8: 1.10, 9: 0.90, 10: 0.60,
                11: 0.40, 12: 0.35
            }
            seasonal_mult = summer_curve.get(month, 1.0)
        elif "Chyawanprash" in product_name or "Vaporub" in product_name or "Coffee" in product_name:
            winter_curve = {
                1: 1.90, 2: 1.50, 3: 0.80, 4: 0.40, 5: 0.30,
                6: 0.30, 7: 0.50, 8: 0.50, 9: 0.80, 10: 1.40,
                11: 1.80, 12: 2.10
            }
            seasonal_mult = winter_curve.get(month, 1.0)
        elif "Tea" in product_name:
            monsoon_curve = {
                1: 1.4, 2: 1.2, 3: 0.9, 4: 0.8, 5: 0.8,
                6: 1.1, 7: 1.6, 8: 1.6, 9: 1.2, 10: 1.1,
                11: 1.3, 12: 1.5
            }
            seasonal_mult = monsoon_curve.get(month, 1.0)
        else:
            seasonal_mult = 1.0
    elif profile == "TRENDING":
        # Accelerating demand in recent weeks
        days_from_start = (date - (datetime.utcnow() - timedelta(days=365))).days
        progress = days_from_start / 365.0
        seasonal_mult = 0.7 + (progress * 0.9)  # 0.7 -> 1.6
    else:
        seasonal_mult = 1.0

    # 2. Day-of-Week Effect (Weekend peak)
    if dow in [4, 5, 6]:
        dow_mult = 1.25
    else:
        dow_mult = 0.88

    # 3. Festival Spikes (Rakhi & Ganesh Festival in August/September)
    festival_mult = 1.0
    if profile in ["FESTIVAL_SENSITIVE", "SEASONAL"]:
        if month == 8 and 12 <= day <= 28:  # Raksha Bandhan & Ganesh prep
            festival_mult = 3.2 if ("Gulab Jamun" in product_name or "Almonds" in product_name or "Celebrations" in product_name) else 1.4
        elif month == 11 and 1 <= day <= 15:  # Diwali
            festival_mult = 3.5 if ("Gulab Jamun" in product_name or "Almonds" in product_name or "Celebrations" in product_name) else 1.8
        elif month == 3 and 15 <= day <= 28:  # Holi
            festival_mult = 2.5 if "Gulab Jamun" in product_name else 1.3

    raw_sales = base_demand * seasonal_mult * dow_mult * festival_mult * vendor_scale
    noise = random.uniform(-0.15, 0.20) * raw_sales
    daily_units = int(round(max(0.0, raw_sales + noise)))

    return daily_units


def seed_demo_database():
    db = SessionLocal()
    print("=" * 70)
    print("SMARTVENDOR — SEEDING 365-DAY MULTI-VENDOR MARKETPLACE DEMO DATA")
    print("=" * 70)

    try:
        now = datetime.utcnow()
        # Strictly end synthetic bills on yesterday afternoon (12:00:00 UTC = 5:30 PM IST)
        # This guarantees 0 synthetic transactions ever touch Today (2026-08-29 IST)
        yesterday_end = (now - timedelta(days=1)).replace(hour=12, minute=0, second=0, microsecond=0)
        start_date = (yesterday_end - timedelta(days=364)).replace(hour=8, minute=0, second=0, microsecond=0)

        for vendor in DEMO_VENDORS:
            v_id = vendor["id"]
            is_harsh = ("uXXp4u9hvxP9hrv22LvllrlX6hx1" in v_id or v_id == "harsh")
            is_himanshu = ("lV3NgaMqXxerZHZlLjjilXhJTL22" in v_id)
            is_vishwajeet = ("wmPdaGXXkGRfiglryt7T7QS0eWG3" in v_id)

            print(f"\n[Store] Processing Vendor: {vendor['shop_name']} (ID: {v_id})")

            # 1. StoreProfile
            store_profile = db.query(models.StoreProfile).filter(models.StoreProfile.user_id == v_id).first()
            if not store_profile:
                store_profile = models.StoreProfile(
                    user_id=v_id,
                    name=vendor["shop_name"],
                    phone=vendor["phone"],
                    address=vendor["address"],
                    gst="27AAPFU1234F1Z5",
                    upi="smartvendor@okaxis"
                )
                db.add(store_profile)
            else:
                store_profile.name = vendor["shop_name"]
                store_profile.phone = vendor["phone"]
                store_profile.address = vendor["address"]

            # 2. Seed / Update Products
            vendor_products = {}
            for p_def in PRODUCT_DEFINITIONS:
                if is_harsh:
                    stock_val = p_def["harsh_stock"]
                elif is_himanshu:
                    stock_val = p_def["himanshu_stock"]
                elif is_vishwajeet:
                    stock_val = p_def["vishwajeet_stock"]
                else:
                    stock_val = p_def.get("demo_stock", 30)

                exp_dt = None
                if p_def.get("expiry_days"):
                    exp_dt = now + timedelta(days=p_def["expiry_days"])

                prod = db.query(models.Product).filter(
                    models.Product.user_id == v_id,
                    models.Product.name == p_def["name"]
                ).first()

                if not prod:
                    prod = models.Product(
                        user_id=v_id,
                        name=p_def["name"],
                        category=p_def["category"],
                        price=p_def["price"],
                        stock=stock_val,
                        low_stock_threshold=max(5, int(p_def["base_daily_demand"] * 2)),
                        unit=p_def["unit"],
                        supplier_moq=p_def["moq"],
                        seasonal_profile=p_def["profile"],
                        expiry_date=exp_dt
                    )
                    db.add(prod)
                    db.flush()
                else:
                    prod.price = p_def["price"]
                    prod.stock = stock_val
                    prod.category = p_def["category"]
                    prod.supplier_moq = p_def["moq"]
                    prod.seasonal_profile = p_def["profile"]
                    prod.unit = p_def["unit"]
                    prod.expiry_date = exp_dt

                vendor_products[p_def["name"]] = prod

            # 3. Clean previous synthetic bills (keeping any authentic bills created today on device)
            today_ist_start_utc = (now - timedelta(days=1)).replace(hour=18, minute=30, second=0, microsecond=0)
            
            # Delete synthetic history for this vendor before today
            db.query(models.Bill).filter(
                models.Bill.user_id == v_id,
                models.Bill.created_at < today_ist_start_utc
            ).delete()
            
            # Also clean any old synthetic leaked bills for today that aren't client-synced
            # (Authentic client bills have IDs like 'BILL_...' or specific UUIDs synced by user)
            leaked_synthetic = db.query(models.Bill).filter(
                models.Bill.user_id == v_id,
                models.Bill.created_at >= today_ist_start_utc,
                models.Bill.total_amount > 1000.0  # Synthetic bills had ~1300-1800 amounts
            ).all()
            for b in leaked_synthetic:
                db.delete(b)
            db.flush()

            # 4. Generate 364 days of synthetic sales transactions (ending yesterday 5:30 PM IST)
            print(f"   [Generate] Generating 364 days of realistic bills ({start_date.strftime('%Y-%m-%d')} -> {yesterday_end.strftime('%Y-%m-%d')})...")
            bills_to_add = []
            bill_items_to_add = []

            for day_offset in range(364):
                current_dt = start_date + timedelta(days=day_offset)

                daily_items = []
                for p_def in PRODUCT_DEFINITIONS:
                    p_name = p_def["name"]
                    prod_obj = vendor_products[p_name]
                    daily_units = calculate_synthetic_day_sales(
                        date=current_dt,
                        base_demand=p_def["base_daily_demand"],
                        profile=p_def["profile"],
                        vendor_scale=vendor["vendor_scale"],
                        is_harsh=is_harsh,
                        product_name=p_name
                    )

                    if daily_units > 0:
                        daily_items.append((prod_obj, p_name, float(p_def["price"]), daily_units))

                if not daily_items:
                    continue

                num_daily_bills = random.randint(4, 8)
                daily_bills = []
                for _ in range(num_daily_bills):
                    b_id = str(uuid.uuid4())
                    # Hours 4 to 12 UTC = 9:30 AM to 5:30 PM IST
                    b_time = current_dt.replace(
                        hour=random.randint(4, 12),
                        minute=random.randint(0, 59),
                        second=random.randint(0, 59)
                    )
                    daily_bills.append({
                        "id": b_id,
                        "time": b_time,
                        "payment": random.choice(["cash", "cash", "upi", "upi", "card"]),
                        "items": []
                    })

                for prod_obj, p_name, price, total_units in daily_items:
                    units_left = total_units
                    while units_left > 0:
                        chosen_bill = random.choice(daily_bills)
                        take_qty = random.randint(1, min(units_left, 3))
                        units_left -= take_qty
                        chosen_bill["items"].append((prod_obj, p_name, price, take_qty))

                for b_dict in daily_bills:
                    if not b_dict["items"]:
                        continue

                    bill_subtotal = sum(price * qty for _, _, price, qty in b_dict["items"])
                    bill_tax = round(bill_subtotal * 0.05, 2)
                    bill_total = round(bill_subtotal + bill_tax, 2)

                    b_obj = models.Bill(
                        id=b_dict["id"],
                        user_id=v_id,
                        total_amount=bill_total,
                        tax_amount=bill_tax,
                        payment_mode=b_dict["payment"],
                        created_at=b_dict["time"]
                    )
                    bills_to_add.append(b_obj)

                    for prod_obj, p_name, price, qty in b_dict["items"]:
                        item_subtotal = round(price * qty, 2)
                        item_obj = models.BillItem(
                            id=str(uuid.uuid4()),
                            bill_id=b_dict["id"],
                            product_id=prod_obj.id,
                            product_name=p_name,
                            quantity=qty,
                            unit_price=price,
                            total_price=item_subtotal
                        )
                        bill_items_to_add.append(item_obj)

            print(f"   [Commit] Committing {len(bills_to_add)} bills and {len(bill_items_to_add)} bill items...")
            db.bulk_save_objects(bills_to_add)
            db.bulk_save_objects(bill_items_to_add)
            db.commit()

        print("\n" + "=" * 70)
        print("[SUCCESS] MULTI-VENDOR MARKETPLACE SEED COMPLETED SUCCESSFULLY!")
        print("=" * 70)

    except Exception as e:
        db.rollback()
        print(f"[ERROR] Error during database seeding: {str(e)}")
        raise e
    finally:
        db.close()


if __name__ == "__main__":
    seed_demo_database()
