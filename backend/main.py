from dotenv import load_dotenv
load_dotenv()

import uuid
from fastapi import FastAPI, Depends
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
import models
from database import engine, Base, get_db, SessionLocal, run_migrations

# Create all tables on startup & apply safe idempotent migrations
Base.metadata.create_all(bind=engine)
run_migrations()


app = FastAPI(
    title="SmartVendor AI API",
    description="Backend API for SmartVendor AI — Billing, Inventory, Analytics, Store Profiles, and Master Catalog",
    version="1.0.0"
)

# Secure CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://localhost:8000",
        "http://127.0.0.1:8000",
        "http://10.88.240.180:8000"
    ],
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# Register routers
from routers import products, bills, analytics, store, catalog, detect, voice, stock, resilience, cie
app.include_router(products.router)
app.include_router(bills.router)
app.include_router(analytics.router)
app.include_router(store.router)
app.include_router(catalog.router)
app.include_router(detect.router)
app.include_router(voice.router)
app.include_router(stock.router)
app.include_router(resilience.router)
app.include_router(cie.router)



@app.get("/", tags=["Health"])
def root():
    return {
        "status": "ok",
        "app": "SmartVendor AI API",
        "version": "1.0.0",
        "docs": "/docs"
    }


@app.get("/health", tags=["Health"])
def health():
    return {"status": "healthy"}
