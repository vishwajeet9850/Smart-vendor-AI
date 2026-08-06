import uuid
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from database import get_db
from auth import CurrentUser
import models
import schemas

router = APIRouter(prefix="/products", tags=["Products"])


@router.get("", response_model=List[schemas.ProductResponse])
def list_products(
    user_id: CurrentUser,
    category: Optional[str] = None,
    search: Optional[str] = None,
    db: Session = Depends(get_db)
):
    query = db.query(models.Product).filter(models.Product.user_id == user_id)
    if category:
        query = query.filter(models.Product.category == category)
    if search:
        query = query.filter(models.Product.name.ilike(f"%{search}%"))
    return query.order_by(models.Product.name).all()


@router.post("", response_model=schemas.ProductResponse, status_code=status.HTTP_201_CREATED)
def create_product(
    body: schemas.ProductCreate,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    product = models.Product(
        id=str(uuid.uuid4()),
        user_id=user_id,
        **body.model_dump()
    )
    db.add(product)
    db.commit()
    db.refresh(product)
    return product


@router.get("/barcode/{barcode}", response_model=schemas.ProductResponse)
def get_product_by_barcode(
    barcode: str,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    product = db.query(models.Product).filter(
        models.Product.user_id == user_id,
        models.Product.barcode == barcode
    ).first()
    if not product:
        raise HTTPException(status_code=404, detail="Product not found for this barcode")
    return product


@router.get("/{product_id}", response_model=schemas.ProductResponse)
def get_product(
    product_id: str,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    product = db.query(models.Product).filter(
        models.Product.id == product_id,
        models.Product.user_id == user_id
    ).first()
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    return product


@router.put("/{product_id}", response_model=schemas.ProductResponse)
def update_product(
    product_id: str,
    body: schemas.ProductUpdate,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    product = db.query(models.Product).filter(
        models.Product.id == product_id,
        models.Product.user_id == user_id
    ).first()
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")

    for field, value in body.model_dump(exclude_unset=True).items():
        setattr(product, field, value)

    db.commit()
    db.refresh(product)
    return product


@router.put("/{product_id}/stock", response_model=schemas.ProductResponse)
def update_stock(
    product_id: str,
    body: schemas.StockUpdateRequest,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    product = db.query(models.Product).filter(
        models.Product.id == product_id,
        models.Product.user_id == user_id
    ).first()
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")

    product.stock = body.stock
    db.commit()
    db.refresh(product)
    return product


@router.delete("/{product_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_product(
    product_id: str,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    product = db.query(models.Product).filter(
        models.Product.id == product_id,
        models.Product.user_id == user_id
    ).first()
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    db.delete(product)
    db.commit()
