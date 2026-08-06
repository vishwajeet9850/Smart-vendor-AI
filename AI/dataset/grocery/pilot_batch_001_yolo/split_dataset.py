from pathlib import Path
import random
import shutil

ROOT = Path(r"C:\Users\Acer\Downloads\pilot_batch_001_yolo")

TRAIN_IMAGES = ROOT / "images" / "train"
VAL_IMAGES = ROOT / "images" / "val"

TRAIN_LABELS = ROOT / "labels" / "train"
VAL_LABELS = ROOT / "labels" / "val"

VAL_IMAGES.mkdir(parents=True, exist_ok=True)
VAL_LABELS.mkdir(parents=True, exist_ok=True)

image_extensions = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

images = [
    p for p in TRAIN_IMAGES.iterdir()
    if p.is_file() and p.suffix.lower() in image_extensions
]

random.seed(42)
random.shuffle(images)

# 20% validation
val_count = round(len(images) * 0.20)

val_images = images[:val_count]

moved = 0

for image in val_images:
    label = TRAIN_LABELS / f"{image.stem}.txt"

    if not label.exists():
        print(f"WARNING: Missing label for {image.name}")
        continue

    shutil.move(str(image), str(VAL_IMAGES / image.name))
    shutil.move(str(label), str(VAL_LABELS / label.name))

    moved += 1

print()
print("Dataset split complete!")
print(f"Training images: {len(images) - moved}")
print(f"Validation images: {moved}")