import uuid
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from database import get_db
from auth import CurrentUser
import models
import schemas

router = APIRouter(prefix="/products", tags=["Products"])


from sqlalchemy import or_

def seed_sample_products_for_user(db: Session, user_id: str) -> List[models.Product]:
    sample_data = [
        ("Oreo", "Snacks", 30.0, 80, "8901262010160"),
        ("Soya Sticks", "Snacks", 20.0, 90, "8901262010161"),
        ("Jim Jam", "Snacks", 35.0, 75, "8901262010162"),
        ("Bourbon", "Snacks", 30.0, 70, "8901262010163"),
        ("Monaco", "Snacks", 15.0, 100, "8901262010164"),
        ("KrackJack", "Snacks", 15.0, 95, "8901262010165"),
        ("Little Hearts", "Snacks", 20.0, 85, "8901262010166"),
        ("Hide and Seek", "Snacks", 30.0, 60, "8901262010060"),
        ("Good Day", "Snacks", 25.0, 80, "8901262010061"),
        ("Parle G", "Snacks", 10.0, 150, "8901262010062"),
        ("5 Star", "Chocolates", 15.0, 110, "8901262010167"),
        ("Munch", "Chocolates", 10.0, 140, "8901262010168"),
        ("Perk", "Chocolates", 10.0, 130, "8901262010169"),
        ("Snickers", "Chocolates", 45.0, 50, "8901262010170"),
        ("Dairy Milk", "Chocolates", 40.0, 100, "8901262010063"),
        ("Dairy Milk Silk", "Chocolates", 80.0, 45, "8901262010064"),
        ("KitKat", "Chocolates", 20.0, 90, "8901262010065"),
        ("Maggi", "Instant Food", 14.0, 120, "8901262010091"),
        ("Yippee Noodles", "Instant Food", 14.0, 115, "8901262010171"),
        ("Top Ramen", "Instant Food", 15.0, 80, "8901262010172"),
        ("Aashirvaad Atta", "Groceries", 270.0, 40, "8901058000001"),
        ("Patanjali Atta", "Groceries", 240.0, 35, "8901058000002"),
        ("Fortune Basmati Rice", "Groceries", 180.0, 30, "8901058000003"),
        ("Toor Dal", "Groceries", 160.0, 50, "8901058000004"),
        ("Chana Dal", "Groceries", 95.0, 60, "8901058000005"),
        ("Moong Dal", "Groceries", 120.0, 45, "8901058000006"),
        ("Rajma", "Groceries", 140.0, 40, "8901058000007"),
        ("Tata Salt", "Groceries", 28.0, 100, "8901262010053"),
        ("Sugar", "Groceries", 44.0, 120, "8901262010039"),
        ("Amul Milk", "Dairy", 28.0, 75, "8901262010015"),
        ("Amul Butter", "Dairy", 56.0, 50, "8901262010016"),
        ("Amul Cheese", "Dairy", 75.0, 40, "8901262010173"),
        ("Amul Ghee", "Dairy", 290.0, 25, "8901262010174"),
        ("Fortune Oil", "Oils", 145.0, 35, "8901262010084"),
        ("Dabur Honey", "Groceries", 195.0, 30, "8901262010175"),
        ("Kissan Ketchup", "Groceries", 125.0, 40, "8901262010176"),
        ("Quaker Oats", "Breakfast", 110.0, 30, "8901262010077"),
        ("Surf Excel", "Cleaning", 140.0, 45, "8901262010107"),
        ("Vim Bar", "Cleaning", 15.0, 150, "8901262010177"),
        ("Lizol", "Cleaning", 110.0, 35, "8901262010178"),
        ("Harpic", "Cleaning", 95.0, 40, "8901262010179"),
        ("Dettol Soap", "Hygiene", 38.0, 70, "8901262010114"),
        ("Colgate", "Hygiene", 65.0, 60, "8901262010180"),
        ("Pepsodent", "Hygiene", 55.0, 50, "8901262010181"),
        ("Clinic Plus Shampoo", "Hygiene", 70.0, 45, "8901262010182"),
        ("Taj Tea", "Beverages", 180.0, 35, "8901262010121"),
        ("Red Label Tea", "Beverages", 140.0, 40, "8901262010122"),
        ("Frooti", "Beverages", 35.0, 90, "8901262010183"),
        ("Maaza", "Beverages", 35.0, 85, "8901262010184"),
        ("Appy Fizz", "Beverages", 35.0, 80, "8901262010185"),
        ("Coca Cola", "Beverages", 40.0, 65, "8901262010131"),
        ("Sprite", "Beverages", 40.0, 60, "8901262010132"),
        ("Thums Up", "Beverages", 40.0, 70, "8901262010133"),
        ("Lays Chips", "Snacks", 20.0, 100, "8901262010141"),
        ("Kurkure", "Snacks", 20.0, 110, "8901262010142"),
        ("Haldiram Bhujia", "Snacks", 55.0, 50, "8901262010143"),
        ("Everest Turmeric", "Spices", 32.0, 60, "8901262010151")
    ]
    created = []
    for name, cat, price, stock, barcode in sample_data:
        p = models.Product(
            id=str(uuid.uuid4()),
            user_id=user_id,
            name=name,
            category=cat,
            price=price,
            stock=stock,
            low_stock_threshold=10,
            barcode=barcode
        )
        created.append(p)
    db.add_all(created)
    db.commit()
    return db.query(models.Product).filter(models.Product.user_id == user_id).order_by(models.Product.name).all()


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

    products = query.order_by(models.Product.name).all()

    # If user has 0 products in store inventory, automatically seed 12 sample store products for them
    if not products and not category and not search:
        products = seed_sample_products_for_user(db, user_id)

    return products


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
