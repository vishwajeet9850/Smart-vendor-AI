import os
import io
import httpx
from typing import Optional
from fastapi import APIRouter, UploadFile, File, Form, Header, HTTPException

router = APIRouter(prefix="/api/voice", tags=["Voice Recognition"])

GROQ_API_URL = "https://api.groq.com/openai/v1/audio/transcriptions"


@router.post("/transcribe")
async def transcribe_audio(
    file: UploadFile = File(...),
    language: Optional[str] = Form(None),
    x_groq_api_key: Optional[str] = Header(None, alias="X-Groq-Api-Key")
):
    """
    Transcribes audio using Groq's ultra-fast Whisper-large-v3 model.
    Supports Marathi ('mr'), Hindi ('hi'), English ('en'), or auto-detection.
    """
    api_key = x_groq_api_key or os.environ.get("GROQ_API_KEY", "").strip()

    if not api_key:
        return {
            "success": False,
            "transcript": "",
            "error": "GROQ_API_KEY is not configured on server or in request header.",
            "source": "none"
        }

    try:
        audio_bytes = await file.read()
        filename = file.filename or "recording.m4a"

        # Prepare multipart data for Groq API
        files = {
            "file": (filename, audio_bytes, file.content_type or "audio/m4a")
        }
        data = {
            "model": "whisper-large-v3",
            "response_format": "json"
        }

        # If language is specified and valid (e.g. 'mr', 'hi', 'en')
        if language and language not in ["auto", ""]:
            # normalize 'mr-IN' -> 'mr', 'hi-IN' -> 'hi', 'en-IN' -> 'en'
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
                error_detail = response.text
                return {
                    "success": False,
                    "transcript": "",
                    "error": f"Groq API error ({response.status_code}): {error_detail}",
                    "source": "groq_error"
                }

    except Exception as ex:
        return {
            "success": False,
            "transcript": "",
            "error": f"Transcription failed: {str(ex)}",
            "source": "server_error"
        }


@router.get("/status")
def voice_service_status():
    api_key = os.environ.get("GROQ_API_KEY", "").strip()
    return {
        "status": "online",
        "groq_configured": bool(api_key),
        "models_available": ["whisper-large-v3", "whisper-large-v3-turbo"],
        "supported_languages": ["mr (Marathi)", "hi (Hindi)", "en (English)", "auto"]
    }
