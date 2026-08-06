from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, Field


# ─── Store Profile Schemas ─────────────────────────────────────────────────────

class StoreProfileCreate(BaseModel):
    name: str = ""
    address: str = ""
    phone: str = ""
    gst: str = ""
    upi: str = ""


class StoreProfileResponse(BaseModel):
    user_id: str
    name: str
    address: str
    phone: str
    gst: str
    upi: str
    updated_at: datetime

    model_config = {"from_attributes": True}


# ─── Product Schemas ───────────────────────────────────────────────────────────

class ProductCreate(BaseModel):
    name: str
    barcode: Optional[str] = None
    category: str = "General"
    price: float
    stock: int = 0
    low_stock_threshold: int = 5
    image_url: Optional[str] = None


class ProductUpdate(BaseModel):
    name: Optional[str] = None
    barcode: Optional[str] = None
    category: Optional[str] = None
    price: Optional[float] = None
    stock: Optional[int] = None
    low_stock_threshold: Optional[int] = None
    image_url: Optional[str] = None


class StockUpdateRequest(BaseModel):
    stock: int


class ProductResponse(BaseModel):
    id: str
    user_id: str
    name: str
    barcode: Optional[str]
    category: str
    price: float
    stock: int
    low_stock_threshold: int
    image_url: Optional[str]
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


# ─── Bill Schemas ──────────────────────────────────────────────────────────────

class BillItemCreate(BaseModel):
    product_id: Optional[str] = None
    product_name: str
    quantity: int
    unit_price: float
    total_price: float


class BillCreate(BaseModel):
    items: List[BillItemCreate]
    total_amount: float
    tax_amount: float = 0.0
    payment_mode: str = "cash"


class BillItemResponse(BaseModel):
    id: str
    product_id: Optional[str]
    product_name: str
    quantity: int
    unit_price: float
    total_price: float

    model_config = {"from_attributes": True}


class BillResponse(BaseModel):
    id: str
    user_id: str
    total_amount: float
    tax_amount: float
    payment_mode: str
    created_at: datetime
    items: List[BillItemResponse] = []

    model_config = {"from_attributes": True}


# ─── Analytics Schemas ─────────────────────────────────────────────────────────

class DailyRevenue(BaseModel):
    date: str
    revenue: float
    bill_count: int


class TopProduct(BaseModel):
    product_name: str
    quantity_sold: int
    revenue: float


class StockRecommendationItem(BaseModel):
    product_id: str
    product_name: str
    current_stock: int
    recommended_reorder: int
    category: str
    peak_window: str = "General Demand"
    sales_velocity: str = "Moderate"
    reasoning: str = "Stock level requires attention based on sales velocity."
    urgency_level: str = "MEDIUM"


class MarketTrendInsight(BaseModel):
    title: str
    description: str
    recommended_product: str
    action_type: str = "RESTOCK"
    badge_label: str = "🌐 Market Trend"


class AnalyticsSummary(BaseModel):
    total_revenue: float
    total_bills: int
    total_products: int
    low_stock_count: int
    top_products: List[TopProduct]
    daily_revenue: List[DailyRevenue]
    stock_recommendations: List[StockRecommendationItem] = []
    market_trends: List[MarketTrendInsight] = []



