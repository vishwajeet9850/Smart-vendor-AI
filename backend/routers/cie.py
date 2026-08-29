from typing import Optional, Dict, Any
from fastapi import APIRouter, Depends, Query, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from database import get_db
from auth import _bearer_scheme
from firebase_admin import auth as fb_auth
from services.cie_manager import cie_manager

router = APIRouter(prefix="/cie", tags=["CIE Cross-Vendor Incident Engine"])


def get_incident_user_id(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(_bearer_scheme),
    user_id: Optional[str] = Query(None)
) -> str:
    if credentials and credentials.credentials:
        try:
            decoded_token = fb_auth.verify_id_token(credentials.credentials)
            uid = decoded_token.get("uid")
            if uid:
                return uid
        except Exception:
            pass
    if user_id:
        return user_id
    return "uXXp4u9hvxP9hrv22LvllrlX6hx1"


@router.get("/status")
def get_cie_status(
    user_id: str = Depends(get_incident_user_id),
    db: Session = Depends(get_db)
):
    """
    Returns current CIE incident alert state and status.
    """
    return cie_manager.get_cie_status(db=db, current_user_id=user_id)


@router.post("/simulate-incident")
def simulate_cie_incident(
    user_id: str = Depends(get_incident_user_id),
    product_name: Optional[str] = Query(None, description="Optional product name to simulate return incident on"),
    db: Session = Depends(get_db)
):
    """
    🚨 Simulate Cross-Vendor Incident:
    - Injects returns for product across 7–8 vendors within 15–30 min cluster.
    - Tags records with demoIncidentId = 'cross_vendor_return_demo'.
    - Runs CIE anomaly detection (>= 5 vendors within 30 min) and generates alert.
    """
    return cie_manager.simulate_cross_vendor_incident(
        db=db,
        current_user_id=user_id,
        target_product_name=product_name
    )


@router.post("/reset-incident")
def reset_cie_incident(
    user_id: str = Depends(get_incident_user_id),
    db: Session = Depends(get_db)
):
    """
    ↩️ Reset Demo Incident:
    - Deletes ONLY records belonging to demoIncidentId = 'cross_vendor_return_demo'.
    - Restores database to exact pre-simulation state.
    - Never modifies real returns, bills, inventory, or production data.
    """
    return cie_manager.reset_demo_incident(
        db=db,
        current_user_id=user_id
    )
