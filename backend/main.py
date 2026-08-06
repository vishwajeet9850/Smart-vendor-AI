from fastapi import FastAPI, Depends
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
import models
from database import engine, Base, get_db

# Create all tables on startup
Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="SmartVendor AI API",
    description="Backend API for SmartVendor AI — Billing, Inventory, Analytics, and Store Profiles",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
from routers import products, bills, analytics, store
app.include_router(products.router)
app.include_router(bills.router)
app.include_router(analytics.router)
app.include_router(store.router)


@app.get("/", tags=["Health"])
def root():
    return {
        "status": "ok",
        "app": "SmartVendor AI API",
        "version": "1.0.0",
        "docs": "/docs",
        "view_data": "/view-data"
    }


@app.get("/health", tags=["Health"])
def health():
    return {"status": "healthy"}


@app.get("/view-data", tags=["Database Inspection"])
def view_data(db: Session = Depends(get_db)):
    products_list = db.query(models.Product).all()
    bills_list = db.query(models.Bill).all()

    return {
        "total_inventory_items": len(products_list),
        "total_completed_bills": len(bills_list),
        "inventory_products": [
            {
                "id": p.id,
                "name": p.name,
                "category": p.category,
                "price": p.price,
                "stock": p.stock,
                "barcode": p.barcode
            }
            for p in products_list
        ],
        "completed_bills": [
            {
                "bill_id": b.id,
                "total_amount": b.total_amount,
                "payment_mode": b.payment_mode,
                "created_at": str(b.created_at),
                "items": [
                    {
                        "product_name": item.product_name,
                        "quantity": item.quantity,
                        "unit_price": item.unit_price,
                        "total_price": item.total_price
                    }
                    for item in b.items
                ]
            }
            for b in bills_list
        ]
    }
