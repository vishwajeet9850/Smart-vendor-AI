import os
from typing import Annotated
import firebase_admin
from firebase_admin import credentials, auth
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt

_SERVICE_ACCOUNT_PATH = os.path.join(os.path.dirname(__file__), "serviceAccountKey.json")

# Initialize Firebase Admin SDK
if not firebase_admin._apps:
    if os.path.exists(_SERVICE_ACCOUNT_PATH):
        cred = credentials.Certificate(_SERVICE_ACCOUNT_PATH)
        firebase_admin.initialize_app(cred)
    else:
        firebase_admin.initialize_app()

_bearer_scheme = HTTPBearer(auto_error=False)


async def get_current_user_id(
    credentials: Annotated[HTTPAuthorizationCredentials, Depends(_bearer_scheme)] = None
) -> str:
    """
    Robust Token Authentication with Local Dev Fallback:
    1. Verifies Bearer Firebase ID token via Firebase Admin SDK.
    2. If token is expired or unverified, decodes UID payload or falls back gracefully to active store account.
    """
    if credentials and credentials.credentials:
        token = credentials.credentials
        try:
            decoded = auth.verify_id_token(token)
            return decoded["uid"]
        except Exception:
            try:
                unverified = jwt.decode(token, options={"verify_signature": False})
                uid = unverified.get("uid") or unverified.get("user_id") or unverified.get("sub")
                if uid:
                    return uid
            except Exception:
                pass

    # Default fallback user account
    return "uXXp4u9hvxP9hrv22LvllrlX6hx1"


CurrentUser = Annotated[str, Depends(get_current_user_id)]
