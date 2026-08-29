import uuid
from datetime import datetime
from sqlalchemy import Column, String, Float, Integer, DateTime, ForeignKey, Text, Index
from sqlalchemy.orm import relationship
from database import Base


def generate_uuid() -> str:
    return str(uuid.uuid4())


class Product(Base):
    __tablename__ = "products"

    id = Column(String, primary_key=True, default=generate_uuid)
    user_id = Column(String, nullable=False, index=True)
    name = Column(String, nullable=False)
    barcode = Column(String, nullable=True)
    category = Column(String, nullable=False, default="General")
    price = Column(Float, nullable=False)
    stock = Column(Integer, nullable=False, default=0)
    low_stock_threshold = Column(Integer, nullable=False, default=5)
    unit = Column(String, nullable=True, default="pcs")
    supplier_moq = Column(Integer, nullable=True, default=1)
    seasonal_profile = Column(String, nullable=True, default="STABLE")  # STABLE, SEASONAL, TRENDING, FESTIVAL_SENSITIVE
    expiry_date = Column(DateTime, nullable=True)
    image_url = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    bill_items = relationship("BillItem", back_populates="product")

    __table_args__ = (
        Index("ix_products_user_name", "user_id", "name"),
        Index("ix_products_user_category", "user_id", "category"),
    )


class Bill(Base):
    __tablename__ = "bills"

    id = Column(String, primary_key=True, default=generate_uuid)
    user_id = Column(String, nullable=False, index=True)
    transaction_type = Column(String, nullable=False, default="BILL", index=True)  # BILL, RETURN
    total_amount = Column(Float, nullable=False)
    tax_amount = Column(Float, nullable=False, default=0.0)
    payment_mode = Column(String, nullable=False, default="cash")
    created_at = Column(DateTime, default=datetime.utcnow, index=True)

    items = relationship("BillItem", back_populates="bill", cascade="all, delete-orphan")

    __table_args__ = (
        Index("ix_bills_user_created", "user_id", "created_at"),
        Index("ix_bills_user_type", "user_id", "transaction_type"),
    )


class BillItem(Base):
    __tablename__ = "bill_items"

    id = Column(String, primary_key=True, default=generate_uuid)
    bill_id = Column(String, ForeignKey("bills.id"), nullable=False)
    product_id = Column(String, ForeignKey("products.id"), nullable=True)
    product_name = Column(String, nullable=False)
    quantity = Column(Integer, nullable=False)
    unit_price = Column(Float, nullable=False)
    total_price = Column(Float, nullable=False)
    condition = Column(String, nullable=False, default="GOOD")  # GOOD, DAMAGED

    bill = relationship("Bill", back_populates="items")
    product = relationship("Product", back_populates="bill_items")

    __table_args__ = (
        Index("ix_bill_items_bill_id", "bill_id"),
        Index("ix_bill_items_product_id", "product_id"),
        Index("ix_bill_items_prod_name", "product_name"),
    )




class StoreProfile(Base):
    __tablename__ = "store_profiles"

    user_id = Column(String, primary_key=True)
    name = Column(String, nullable=False, default="")
    address = Column(Text, nullable=False, default="")
    phone = Column(String, nullable=False, default="")
    gst = Column(String, nullable=False, default="")
    upi = Column(String, nullable=False, default="")
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class MasterCatalog(Base):
    __tablename__ = "master_catalog"

    id = Column(String, primary_key=True, default=generate_uuid)
    name = Column(String, nullable=False, index=True)
    category = Column(String, nullable=False, default="General")
    suggested_price = Column(Float, nullable=False, default=0.0)
    barcode = Column(String, nullable=True, index=True)

