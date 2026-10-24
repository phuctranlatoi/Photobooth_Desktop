"""Inventory the read-only Phase 4.3 archive and detect faces with YuNet."""

import csv
import hashlib
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import cv2
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / "build/Cloudinary_Archive_2026-10-19_12_38_162_Originals"
REPORTS = ROOT / "build/reports/beauty-phase43-realworld"
MODEL = ROOT / "app-resources/models/face_detection_yunet_2023mar.onnx"
FIELDS = (
    "anonymous_id", "original_filename", "relative_path", "sha256",
    "file_size_bytes", "width", "height", "decode_status", "detected_faces",
)


def main():
    files = sorted((path for path in ARCHIVE.rglob("*") if path.is_file()),
                   key=lambda path: path.relative_to(ARCHIVE).as_posix())
    if len(files) != 125:
        raise ValueError(f"Expected 125 archive files, found {len(files)}")
    detector = cv2.FaceDetectorYN.create(str(MODEL), "", (320, 320), 0.85, 0.3)
    rows = []
    dimensions = Counter()
    face_counts = []
    for index, path in enumerate(files, 1):
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        image = cv2.imdecode(np.fromfile(str(path), dtype=np.uint8), cv2.IMREAD_COLOR)
        width, height = (image.shape[1], image.shape[0]) if image is not None else (0, 0)
        faces_count = 0
        if image is not None:
            dimensions[f"{width}x{height}"] += 1
            detector.setInputSize((width, height))
            _, faces = detector.detect(image)
            faces_count = 0 if faces is None else len(faces)
            face_counts.append(faces_count)
        rows.append({
            "anonymous_id": f"IMG_{index:03d}",
            "original_filename": path.name,
            "relative_path": path.relative_to(ROOT).as_posix(),
            "sha256": digest.hexdigest(),
            "file_size_bytes": path.stat().st_size,
            "width": width,
            "height": height,
            "decode_status": "SUCCESS" if image is not None else "FAIL",
            "detected_faces": faces_count,
        })
        print(f"{index}/125 {path.name}: {faces_count} faces", flush=True)

    failures = [row for row in rows if row["decode_status"] == "FAIL"]
    summary = {
        "total_files": len(files),
        "jpg_files": sum(path.suffix.lower() in (".jpg", ".jpeg") for path in files),
        "png_files": sum(path.suffix.lower() == ".png" for path in files),
        "successfully_decoded": len(rows) - len(failures),
        "failed_decoding": len(failures),
        "dimension_distribution": dict(sorted(dimensions.items())),
        "total_detected_faces": sum(face_counts),
        "images_with_0_faces": face_counts.count(0),
        "images_with_1_face": face_counts.count(1),
        "images_with_multiple_faces": sum(count > 1 for count in face_counts),
        "max_faces_in_single_image": max(face_counts, default=0),
        "evaluated_images": len(face_counts),
        "skipped_images": len(failures),
        "reasons_for_skipping": {"decoding_failed": len(failures)} if failures else {},
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
    REPORTS.mkdir(parents=True, exist_ok=True)
    with (REPORTS / "dataset_manifest.csv").open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    (REPORTS / "dataset_summary.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print("First five rows:")
    for row in rows[:5]:
        print(row)
    print("Summary:", json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
