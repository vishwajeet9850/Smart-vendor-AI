from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from database import get_db
from auth import CurrentUser
import models
import schemas

router = APIRouter(prefix="/store", tags=["Store Profile"])


@router.get("", response_model=schemas.StoreProfileResponse)
def get_store_profile(
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    profile = db.query(models.StoreProfile).filter(
        models.StoreProfile.user_id == user_id
    ).first()

    if not profile:
        # Create an empty profile for new user
        profile = models.StoreProfile(
            user_id=user_id,
            name="",
            address="",
            phone="",
            gst="",
            upi=""
        )
        db.add(profile)
        db.commit()
        db.refresh(profile)

    return profile


@router.put("", response_model=schemas.StoreProfileResponse)
def update_store_profile(
    body: schemas.StoreProfileCreate,
    user_id: CurrentUser,
    db: Session = Depends(get_db)
):
    profile = db.query(models.StoreProfile).filter(
        models.StoreProfile.user_id == user_id
    ).first()

    if not profile:
        profile = models.StoreProfile(user_id=user_id)
        db.add(profile)

    profile.name = body.name
    profile.address = body.address
    profile.phone = body.phone
    profile.gst = body.gst
    profile.upi = body.upi

    db.commit()
    db.refresh(profile)
    return profile
