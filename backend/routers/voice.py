import os
import io
import httpx
from typing import Optional
from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Depends, status
from auth import CurrentUser

router = APIRouter(prefix="/api/voice", tags=["Voice Recognition"])

GROQ_API_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
MAX_AUDIO_SIZE_BYTES = 10 * 1024 * 1024  # 10 MB limit


@router.post("/transcribe")
async def transcribe_audio(
    user_id: CurrentUser,
    file: UploadFile = File(...),
    language: Optional[str] = Form(None)
):
    """
    Transcribes audio using Groq Whisper-large-v3 model with server-side environment key.
    Protected by Firebase Authentication.
    Supports Marathi ('mr'), Hindi ('hi'), English ('en'), or auto-detection.
    """
    api_key = os.environ.get("GROQ_API_KEY", "").strip()
    if not api_key:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Voice transcription service is not configured on server (missing GROQ_API_KEY)"
        )

    audio_bytes = await file.read()
    if len(audio_bytes) > MAX_AUDIO_SIZE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="Audio recording exceeds maximum allowable size (10MB)"
        )

    try:
        filename = file.filename or "recording.m4a"
        files = {
            "file": (filename, audio_bytes, file.content_type or "audio/m4a")
        }
        data = {
            "model": "whisper-large-v3",
            "response_format": "json"
        }

        if language and language not in ["auto", ""]:
            lang_code = language.split("-")[0].lower()
            data["language"] = lang_code

        headers = {
            "Authorization": f"Bearer {api_key}"
        }

        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(GROQ_API_URL, files=files, data=data, headers=headers)

            if response.status_code == 200:
                resp_json = response.json()
                transcript = resp_json.get("text", "").strip()
                return {
                    "success": True,
                    "transcript": transcript,
                    "language": data.get("language", "auto"),
                    "source": "groq_whisper"
                }
            else:
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail=f"Groq API transcription error ({response.status_code}): {response.text}"
                )

    except HTTPException:
        raise
    except Exception as ex:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Transcription failed: {str(ex)}"
        )


@router.get("/status")
def voice_service_status(user_id: CurrentUser):
    api_key = os.environ.get("GROQ_API_KEY", "").strip()
    return {
        "status": "online",
        "groq_configured": bool(api_key),
        "models_available": ["whisper-large-v3", "whisper-large-v3-turbo"],
        "supported_languages": ["mr (Marathi)", "hi (Hindi)", "en (English)", "auto"]
    }
