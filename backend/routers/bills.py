import uuid
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import func
from database import get_db
from auth import CurrentUser
import models
import schemas

router = APIRouter(prefix="/bills", tags=["Bills"])


@router.post("", response_model=schemas.BillResponse, status_code=status.HTTP_201_CREATED)
def create_bill(
    body: schemas.BillCreate,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    """
    Create a bill and atomically deduct stock from each existing product.
    Safe for both catalog products and ad-hoc manual items.
    """
    bill_id = str(uuid.uuid4())

    # Map item -> valid_product or None
    validated_items = []
    for item in body.items:
        product = None
        if item.product_id:
            product = db.query(models.Product).filter(
                models.Product.id == item.product_id,
                models.Product.user_id == user_id
            ).first()

        validated_items.append((item, product))

    # Create bill record
    bill = models.Bill(
        id=bill_id,
        user_id=user_id,
        total_amount=body.total_amount,
        tax_amount=body.tax_amount,
        payment_mode=body.payment_mode
    )
    db.add(bill)

    # Create bill items and update stock for catalog items
    for item, product in validated_items:
        bill_item = models.BillItem(
            id=str(uuid.uuid4()),
            bill_id=bill_id,
            product_id=product.id if product else None,
            product_name=item.product_name,
            quantity=item.quantity,
            unit_price=item.unit_price,
            total_price=item.total_price
        )
        db.add(bill_item)

        if product:
            new_stock = max(0, product.stock - item.quantity)
            db.query(models.Product).filter(
                models.Product.id == product.id,
                models.Product.user_id == user_id
            ).update({models.Product.stock: new_stock})

    db.commit()
    db.refresh(bill)
    return bill


@router.get("", response_model=List[schemas.BillResponse])
def list_bills(
    user_id: CurrentUser,
    limit: int = 50,
    offset: int = 0,
    db: Session = Depends(get_db)
):
    user_bill_count = db.query(func.count(models.Bill.id)).filter(models.Bill.user_id == user_id).scalar() or 0
    if user_bill_count > 0:
        bills = db.query(models.Bill).filter(
            models.Bill.user_id == user_id
        ).order_by(models.Bill.created_at.desc()).offset(offset).limit(limit).all()
    else:
        bills = db.query(models.Bill).order_by(models.Bill.created_at.desc()).offset(offset).limit(limit).all()
    return bills


@router.get("/{bill_id}", response_model=schemas.BillResponse)
def get_bill(
    bill_id: str,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    bill = db.query(models.Bill).filter(
        models.Bill.id == bill_id,
        models.Bill.user_id == user_id
    ).first()
    if not bill:
        raise HTTPException(status_code=404, detail="Bill not found")
    return bill
