import uuid
from datetime import datetime
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
    Authoritative Bill / Return Creation:
    1. Validates items, quantities, and conditions.
    2. Authoritatively calculates unit prices, item line totals, subtotal, tax, and total.
    3. Atomically updates inventory stock:
       - BILL: Deducts sold quantity from stock (stock = max(0, stock - qty))
       - RETURN + GOOD: Adds returned quantity back to stock (stock = stock + qty)
       - RETURN + DAMAGED: Keeps stock unchanged (stock = stock + 0), recording damaged return for audit.
    """
    if not body.items:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot create an empty bill or return"
        )

    is_return = (body.transaction_type or "BILL").upper() == "RETURN"
    transaction_type = "RETURN" if is_return else "BILL"

    # Idempotent sync check: If bill with this ID already exists for user, return it
    bill_id = body.id or str(uuid.uuid4())
    existing_bill = db.query(models.Bill).filter(
        models.Bill.id == bill_id,
        models.Bill.user_id == user_id
    ).first()
    if existing_bill:
        return existing_bill

    bill_items_to_create = []
    subtotal = 0.0

    # Step 1: Validate items & compute authoritative pricing
    for item in body.items:
        if item.quantity <= 0:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Item quantity must be greater than 0. Received: {item.quantity}"
            )

        condition = (item.condition or "GOOD").upper()
        if condition not in ["GOOD", "DAMAGED"]:
            condition = "GOOD"

        product = None
        if item.product_id:
            product = db.query(models.Product).filter(
                models.Product.id == item.product_id,
                models.Product.user_id == user_id
            ).first()

            if product:
                unit_price = float(product.price)
                product_name = product.name
            else:
                unit_price = max(0.0, float(item.unit_price or 0.0))
                product_name = item.product_name.strip() or "Custom Item"
        else:
            # Manual / ad-hoc item without product_id
            unit_price = max(0.0, float(item.unit_price or 0.0))
            product_name = item.product_name.strip() or "Custom Item"

        line_total = round(unit_price * item.quantity, 2)
        subtotal += line_total

        bill_item = models.BillItem(
            id=str(uuid.uuid4()),
            bill_id=bill_id,
            product_id=product.id if product else None,
            product_name=product_name,
            quantity=item.quantity,
            unit_price=unit_price,
            total_price=line_total,
            condition=condition
        )
        bill_items_to_create.append((bill_item, product, item.quantity, condition))

    # Authoritative financial totals
    subtotal = round(subtotal, 2)
    tax_amount = round(subtotal * 0.05, 2)
    total_amount = round(subtotal + tax_amount, 2)

    bill = models.Bill(
        id=bill_id,
        user_id=user_id,
        transaction_type=transaction_type,
        total_amount=total_amount,
        tax_amount=tax_amount,
        payment_mode=body.payment_mode or "cash",
        created_at=body.created_at or datetime.utcnow()
    )
    db.add(bill)

    # Step 2: Add bill items & update stock atomically
    for bill_item, product, qty, item_cond in bill_items_to_create:
        db.add(bill_item)
        if product:
            if is_return:
                if item_cond == "GOOD":
                    product.stock = product.stock + qty
                elif item_cond == "DAMAGED":
                    # Damaged items are recorded on return bill but NOT added back to sellable stock
                    pass
            else:
                product.stock = max(0, product.stock - qty)

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
    """
    Strictly queries bills belonging to the authenticated user.
    Never falls back to global data.
    """
    bills = db.query(models.Bill).filter(
        models.Bill.user_id == user_id
    ).order_by(models.Bill.created_at.desc()).offset(offset).limit(limit).all()
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
