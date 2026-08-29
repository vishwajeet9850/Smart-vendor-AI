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


class MasterCatalogResponse(BaseModel):
    id: str
    name: str
    category: str
    suggested_price: float
    barcode: Optional[str] = None

    model_config = {"from_attributes": True}



# ─── Bill Schemas ──────────────────────────────────────────────────────────────

class BillItemCreate(BaseModel):
    product_id: Optional[str] = None
    product_name: str
    quantity: int
    unit_price: float
    total_price: float
    condition: Optional[str] = "GOOD"  # GOOD, DAMAGED


class BillCreate(BaseModel):
    id: Optional[str] = None
    transaction_id: Optional[str] = None
    transaction_type: Optional[str] = "BILL"  # BILL, RETURN
    items: List[BillItemCreate]
    total_amount: float
    tax_amount: float = 0.0
    payment_mode: str = "cash"
    created_at: Optional[datetime] = None


class BillItemResponse(BaseModel):
    id: str
    product_id: Optional[str]
    product_name: str
    quantity: int
    unit_price: float
    total_price: float
    condition: str = "GOOD"

    model_config = {"from_attributes": True}


class BillResponse(BaseModel):
    id: str
    user_id: str
    transaction_id: Optional[str] = None
    transaction_type: str = "BILL"
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
    recommendation_type: str = "RESTOCK"
    recommendation_title: str = "Restock Recommendation"
    action_type: str = "RESTOCK"
    action_label: str = "Order Stock"
    simple_reason: str = ""


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


# ─── Stock Recommendation & Market Intelligence Schemas ───────────────────────

class StockRecommendationRequest(BaseModel):
    product_id: Optional[str] = None
    product_name: Optional[str] = None
    forecast_days: int = 7


class MarketDemandInfo(BaseModel):
    market_insight_available: bool = True
    demand_level: str = "NORMAL"  # LOW, NORMAL, HIGH, VERY_HIGH
    comparison_percentage: float = 100.0
    market_average_sales: float = 0.0
    participating_vendors: int = 0
    insight_text: Optional[str] = None


class StockRecommendationResponse(BaseModel):
    product_id: str
    product_name: str
    category: str = "General"
    current_stock: int
    predicted_daily_demand: float
    predicted_demand: int
    safety_stock: int
    target_stock: int
    recommended_purchase: int
    status: str  # RESTOCK, LOW_STOCK, STOCK_OK, OVERSTOCK
    trend: str  # INCREASING, STABLE, DECREASING
    seasonal_profile: str = "STABLE"  # STABLE, SEASONAL, TRENDING, FESTIVAL_SENSITIVE
    seasonal_factor: float = 1.0
    supplier_moq: int = 1
    unit: str = "pcs"
    market: MarketDemandInfo
    reason: str
    recommendation_type: str = "URGENT_RESTOCK"
    recommendation_title: str = "🚨 Urgent Restock"
    action_type: str = "RESTOCK"
    action_label: str = "Order Stock"
    simple_reason: str = ""


class BulkStockRecommendationResponse(BaseModel):
    recommendations: List[StockRecommendationResponse]
    total_products: int
    restock_count: int
    low_stock_count: int
    overstock_count: int
    optimal_count: int
    generated_at: datetime = Field(default_factory=datetime.utcnow)


# ─── Blackout Challenge & Resilience Schemas ──────────────────────────────────

class JournalTransactionResponse(BaseModel):
    id: str
    transaction_id: str
    user_id: str
    type: str  # SALE, RETURN, STOCK_ADJUST
    bill_id: Optional[str] = None
    product_id: Optional[str] = None
    product_name: Optional[str] = None
    quantity: Optional[int] = 0
    unit_price: Optional[float] = 0.0
    total_amount: Optional[float] = 0.0
    previous_stock: Optional[int] = None
    new_stock: Optional[int] = None
    return_condition: str = "GOOD"
    status: str = "APPLIED"  # PENDING, APPLIED, RECOVERED, FAILED
    payload_json: Optional[str] = None
    timestamp: Optional[datetime] = None
    created_at: datetime

    model_config = {"from_attributes": True}


class SystemCheckpointResponse(BaseModel):
    id: str
    user_id: str
    checkpoint_id: str
    checkpoint_type: str = "AUTO"
    products_count: int = 0
    bills_count: int = 0
    last_transaction_id: Optional[str] = None
    created_at: datetime

    model_config = {"from_attributes": True}


class RecoveryReport(BaseModel):
    system_status: str = "HEALTHY"  # HEALTHY, BLACKOUT_ACTIVE, RECOVERING, RECOVERED
    last_checkpoint_id: Optional[str] = None
    last_checkpoint_timestamp: Optional[datetime] = None
    transactions_discovered: int = 0
    successfully_recovered: int = 0
    already_present: int = 0
    unrecoverable: int = 0
    unrecoverable_details: List[str] = []
    inventory_summary: List[dict] = []
    bills_count: int = 0
    report_generated_at: datetime = Field(default_factory=datetime.utcnow)


class SystemStatusResponse(BaseModel):
    system_status: str  # HEALTHY, BLACKOUT_ACTIVE, RECOVERING, RECOVERED
    is_blackout_active: bool = False
    blackout_started_at: Optional[datetime] = None
    simulated_failure_reason: Optional[str] = None
    primary_database_status: str = "ONLINE"  # ONLINE, CORRUPTED_UNAVAILABLE
    last_verified_checkpoint: Optional[datetime] = None
    last_checkpoint_id: Optional[str] = None
    total_journaled_transactions: int = 0
    pending_recovery_count: int = 0
    recovered_transactions_count: int = 0
    latest_report: Optional[RecoveryReport] = None





