"""
YOLO Object Detection Router
Runs best.pt against a camera frame image sent from the Android app.
Returns detected product labels + confidence scores.
"""
import io
import base64
import logging
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, File, UploadFile, HTTPException, Form
from fastapi.responses import JSONResponse
from PIL import Image
import numpy as np

router = APIRouter(prefix="/detect", tags=["YOLO Detection"])
logger = logging.getLogger(__name__)

# ── Load YOLO model once at startup ──────────────────────────────────────────
MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "best.pt"
_yolo_model = None


def _get_model():
    global _yolo_model
    if _yolo_model is None:
        try:
            from ultralytics import YOLO
            logger.info(f"Loading YOLO model from {MODEL_PATH}")
            _yolo_model = YOLO(str(MODEL_PATH))
            logger.info(f"YOLO model loaded. Classes: {_yolo_model.names}")
        except Exception as e:
            logger.error(f"Failed to load YOLO model: {e}")
            raise RuntimeError(f"YOLO model could not be loaded: {e}")
    return _yolo_model


# ── Schemas ───────────────────────────────────────────────────────────────────
from pydantic import BaseModel


class Detection(BaseModel):
    label: str
    confidence: float
    bbox: list[float]  # [x1, y1, x2, y2] normalised 0-1


class DetectResponse(BaseModel):
    detections: list[Detection]
    top_label: Optional[str] = None
    top_confidence: Optional[float] = None


# ── Helper ────────────────────────────────────────────────────────────────────
def _verify_color_signature(crop_img: Image.Image, label: str) -> bool:
    """
    Sub-millisecond (<0.5ms) physical packaging color profile validator.
    Eliminates out-of-domain false positives (e.g. Green Soya Sticks confused as Yellow Maggi).
    """
    try:
        rgb = np.array(crop_img.convert("RGB"))
        if rgb.size == 0:
            return True

        mean_lum = np.mean(rgb)
        lbl = label.lower().strip()

        # Reject black/covered camera
        if lbl in ["appe_fizz", "appy"]:
            if mean_lum < 35.0:
                return False

        small = crop_img.resize((64, 64)).convert("HSV")
        hsv_np = np.array(small)
        h = hsv_np[:, :, 0]
        s = hsv_np[:, :, 1]
        v = hsv_np[:, :, 2]

        saturated = (s > 40) & (v > 50)
        if not np.any(saturated):
            return True

        h_deg = (h[saturated] / 255.0) * 360.0
        total = len(h_deg)
        if total == 0:
            return True

        yellow_pct = (np.sum((h_deg >= 40) & (h_deg <= 75)) / total) * 100
        green_pct = (np.sum((h_deg >= 80) & (h_deg <= 165)) / total) * 100
        blue_pct = (np.sum((h_deg >= 180) & (h_deg <= 260)) / total) * 100
        red_pct = (np.sum((h_deg >= 340) | (h_deg <= 18)) / total) * 100
        purple_pct = (np.sum((h_deg >= 260) & (h_deg < 340)) / total) * 100

        if lbl == "maggi":
            if green_pct > 28.0 and yellow_pct < 35.0:
                return False
        elif lbl == "surf_excel":
            if yellow_pct > 35.0:
                return False
        elif lbl == "oreo":
            if green_pct > 40.0:
                return False
        elif lbl == "appe_fizz":
            if green_pct > 40.0:
                return False

        return True
    except Exception:
        return True


def _run_inference(img: Image.Image, conf_threshold: float = 0.65) -> DetectResponse:
    model = _get_model()
    rgb_img = img.convert("RGB")
    w, h = rgb_img.size

    # Run inference with class-agnostic NMS to eliminate multi-class overlaps on the same object
    results = model.predict(
        source=rgb_img,
        conf=conf_threshold,
        iou=0.45,
        agnostic_nms=True,
        verbose=False
    )

    detections: list[Detection] = []

    for result in results:
        for box in result.boxes:
            cls_id = int(box.cls[0])
            label = model.names[cls_id]
            conf = float(box.conf[0])
            x1, y1, x2, y2 = box.xyxy[0].tolist()

            # Fast Color Signature Guard (<0.5ms)
            crop_box = (max(0, int(x1)), max(0, int(y1)), min(w, int(x2)), min(h, int(y2)))
            crop = rgb_img.crop(crop_box)
            if not _verify_color_signature(crop, label):
                logger.info(f"[Color Guard] Rejected false positive: {label} ({conf*100:.1f}%) on non-matching package color")
                continue

            # normalise [0..1]
            detections.append(Detection(
                label=label,
                confidence=round(conf, 4),
                bbox=[round(x1 / w, 4), round(y1 / h, 4),
                      round(x2 / w, 4), round(y2 / h, 4)]
            ))

    # Sort by confidence descending
    detections.sort(key=lambda d: d.confidence, reverse=True)

    if detections:
        det_summary = ", ".join(f"{d.label} ({int(d.confidence*100)}%)" for d in detections)
        logger.info(f"[YOLO Inference] Found {len(detections)} product(s): {det_summary}")
        print(f"🎯 [YOLO] Detections: {det_summary}")

    top = detections[0] if detections else None
    return DetectResponse(
        detections=detections,
        top_label=top.label if top else None,
        top_confidence=top.confidence if top else None,
    )


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.post("/image", response_model=DetectResponse, summary="Detect products in an uploaded image")
async def detect_from_upload(
    file: UploadFile = File(...),
    conf: float = Form(default=0.65)
):
    try:
        contents = await file.read()
        img = Image.open(io.BytesIO(contents))
        return _run_inference(img, conf_threshold=conf)
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        logger.error(f"Detection error: {e}")
        raise HTTPException(status_code=400, detail=f"Invalid image: {e}")


@router.post("/base64", response_model=DetectResponse, summary="Detect products from a base64 image")
async def detect_from_base64(payload: dict):
    try:
        b64 = payload.get("image", "")
        conf = float(payload.get("conf", 0.65))
        img_bytes = base64.b64decode(b64)
        img = Image.open(io.BytesIO(img_bytes))
        return _run_inference(img, conf_threshold=conf)
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        logger.error(f"Detection base64 error: {e}")
        raise HTTPException(status_code=400, detail=f"Invalid payload: {e}")


@router.get("/classes", summary="List classes the YOLO model can detect")
async def get_classes():
    try:
        model = _get_model()
        return {"classes": model.names, "num_classes": len(model.names)}
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
