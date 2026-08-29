"""
YOLO Product Detection Endpoint
Runs best.pt against camera frame images.
Returns detected product labels + confidence scores.
"""
import io
import base64
import logging
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, File, UploadFile, HTTPException, Form
from PIL import Image
import numpy as np

router = APIRouter(prefix="/detect", tags=["YOLO Detection"])
logger = logging.getLogger(__name__)

MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "best.pt"
_yolo_model = None

MAX_IMAGE_SIZE_BYTES = 15 * 1024 * 1024  # 15 MB limit


def preload_and_warmup_model():
    """
    Loads YOLO weights and executes a 480x480 dummy forward pass during server boot.
    Eliminates first-frame inference lag completely.
    """
    global _yolo_model
    try:
        from ultralytics import YOLO
        logger.info(f"Loading YOLO model from {MODEL_PATH}")
        _yolo_model = YOLO(str(MODEL_PATH))
        # Warm up PyTorch computation graph and tensor caches
        dummy_img = Image.new("RGB", (480, 480), color=(128, 128, 128))
        _yolo_model.predict(source=dummy_img, imgsz=480, conf=0.45, verbose=False)
        logger.info(f"YOLO model ready and fully warmed up. Classes ({len(_yolo_model.names)}): {_yolo_model.names}")
    except Exception as e:
        logger.error(f"Failed to preload YOLO model: {e}")


def _get_model():
    global _yolo_model
    if _yolo_model is None:
        preload_and_warmup_model()
    return _yolo_model


# Warm up immediately on module load
preload_and_warmup_model()


from pydantic import BaseModel


class Detection(BaseModel):
    label: str
    confidence: float
    bbox: list[float]


class DetectResponse(BaseModel):
    detections: list[Detection]
    top_label: Optional[str] = None
    top_confidence: Optional[float] = None


def _verify_color_signature(crop_img: Image.Image, label: str) -> bool:
    """
    Sanity check to discard completely pitch-black frames or zero-size crops.
    Avoids arbitrary color thresholds that reject genuine product packaging in varied lighting.
    """
    try:
        rgb = np.array(crop_img.convert("RGB"))
        if rgb.size == 0:
            return True

        mean_lum = np.mean(rgb)
        lbl = label.lower().strip()

        # Reject completely black / covered camera
        if mean_lum < 15.0:
            return False

        if lbl in ["appe_fizz", "appy"] and mean_lum < 25.0:
            return False

        return True
    except Exception:
        return True


def _run_inference(img: Image.Image, conf_threshold: float = 0.45) -> DetectResponse:
    model = _get_model()
    rgb_img = img.convert("RGB")
    w, h = rgb_img.size

    results = model.predict(
        source=rgb_img,
        imgsz=480,
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

            crop_box = (max(0, int(x1)), max(0, int(y1)), min(w, int(x2)), min(h, int(y2)))
            crop = rgb_img.crop(crop_box)
            if not _verify_color_signature(crop, label):
                continue

            detections.append(Detection(
                label=label,
                confidence=round(conf, 4),
                bbox=[round(x1 / w, 4), round(y1 / h, 4),
                      round(x2 / w, 4), round(y2 / h, 4)]
            ))

    detections.sort(key=lambda d: d.confidence, reverse=True)
    top = detections[0] if detections else None
    return DetectResponse(
        detections=detections,
        top_label=top.label if top else None,
        top_confidence=top.confidence if top else None,
    )


@router.post("/image", response_model=DetectResponse, summary="Detect products in an uploaded image")
async def detect_from_upload(
    file: UploadFile = File(...),
    conf: float = Form(default=0.45)
):
    if not (0.0 <= conf <= 1.0):
        raise HTTPException(status_code=400, detail="Confidence threshold must be between 0.0 and 1.0")

    contents = await file.read()
    if len(contents) > MAX_IMAGE_SIZE_BYTES:
        raise HTTPException(status_code=413, detail="Uploaded image exceeds maximum allowable size (15MB)")

    try:
        img = Image.open(io.BytesIO(contents))
        return _run_inference(img, conf_threshold=conf)
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid image format: {e}")


@router.post("/base64", response_model=DetectResponse, summary="Detect products from a base64 image")
async def detect_from_base64(
    payload: dict
):
    b64 = payload.get("image", "")
    try:
        conf = float(payload.get("conf", 0.45))
    except (ValueError, TypeError):
        raise HTTPException(status_code=400, detail="Invalid confidence value")

    if not (0.0 <= conf <= 1.0):
        raise HTTPException(status_code=400, detail="Confidence threshold must be between 0.0 and 1.0")

    try:
        img_bytes = base64.b64decode(b64)
        if len(img_bytes) > MAX_IMAGE_SIZE_BYTES:
            raise HTTPException(status_code=413, detail="Payload exceeds maximum allowable size (15MB)")

        img = Image.open(io.BytesIO(img_bytes))
        return _run_inference(img, conf_threshold=conf)
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid image payload: {e}")


@router.get("/classes", summary="List classes the YOLO model can detect")
async def get_classes():
    try:
        model = _get_model()
        return {"classes": model.names, "num_classes": len(model.names)}
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
