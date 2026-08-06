import os
from typing import Annotated
import firebase_admin
from firebase_admin import credentials, auth
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

_SERVICE_ACCOUNT_PATH = os.path.join(os.path.dirname(__file__), "serviceAccountKey.json")

# Initialize Firebase Admin SDK
if not firebase_admin._apps:
    if os.path.exists(_SERVICE_ACCOUNT_PATH):
        cred = credentials.Certificate(_SERVICE_ACCOUNT_PATH)
        firebase_admin.initialize_app(cred)
    else:
        # Uses GOOGLE_APPLICATION_CREDENTIALS environment variable if serviceAccountKey.json is absent
        firebase_admin.initialize_app()

_bearer_scheme = HTTPBearer(auto_error=True)


async def get_current_user_id(
    credentials: Annotated[HTTPAuthorizationCredentials, Depends(_bearer_scheme)]
) -> str:
    """
    Strict production token verification:
    Verifies incoming Bearer Firebase ID token via Firebase Admin SDK.
    Returns the real Firebase UID on success.
    Raises 401 Unauthorized if token is invalid, expired, or missing.
    """
    token = credentials.credentials
    try:
        decoded = auth.verify_id_token(token)
        uid: str = decoded["uid"]
        return uid
    except auth.ExpiredIdTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication token has expired. Please log in again.",
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Invalid authentication token: {str(e)}",
        )


CurrentUser = Annotated[str, Depends(get_current_user_id)]
