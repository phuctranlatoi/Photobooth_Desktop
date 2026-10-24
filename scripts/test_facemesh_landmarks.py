"""Inspect the MediaPipe FaceMesh ONNX model on beauty-phase42 originals.

Run: python scripts/test_facemesh_landmarks.py
The downloaded model stays outside the repository; outputs go to the temp folder.
"""

from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort


ROOT = Path(__file__).resolve().parents[1]
TEMP = Path(r"C:/Users/ASUS/AppData/Local/Temp/antigravity")
MODEL = TEMP / "face_landmarks.onnx"
if not MODEL.exists():
    MODEL = TEMP / "fernandotonon_QtMeshEditor-facemesh-onnx/face_landmarks.onnx"
DETECTOR = ROOT / "app-resources/models/face_detection_yunet_2023mar.onnx"
REPORTS = ROOT / "build/reports/beauty-phase42/latest"

GROUPS = {
    "upper_lip": [61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291],
    "lower_lip": [61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291],
    "inner_upper": [78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308],
    "inner_lower": [78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308],
    "left_eye": [33, 160, 158, 133, 153, 144, 33],
    "right_eye": [362, 385, 387, 263, 373, 380, 362],
    "philtrum": [2, 94, 164, 0],
}


def main():
    if not MODEL.exists():
        raise FileNotFoundError(MODEL)
    session = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    print("model:", MODEL)
    print("inputs:", [(i.name, i.shape) for i in session.get_inputs()])
    print("outputs:", [(o.name, o.shape) for o in session.get_outputs()])
    for name in ("session_13", "session_01"):
        original = REPORTS / name / "01_original.png"
        image = cv2.imread(str(original))
        if image is None:
            print(name, "missing:", original)
            continue
        height, width = image.shape[:2]
        scale = min(1.0, 640 / max(width, height))
        detection_image = cv2.resize(image, None, fx=scale, fy=scale)
        detector = cv2.FaceDetectorYN.create(str(DETECTOR), "", (detection_image.shape[1], detection_image.shape[0]))
        _, faces = detector.detect(detection_image)
        if faces is None or not len(faces):
            print(name, "YuNet found no face; using existing 03_aligned_crop.png")
            aligned = cv2.imread(str(REPORTS / name / "03_aligned_crop.png"))
            if aligned is None:
                continue
            crop = cv2.resize(aligned, (256, 256))
        else:
            face = max(faces, key=lambda f: f[2] * f[3])
            x, y, w, h = face[:4] / scale
        # Approximate MediaPipe-style square ROI; no rotation for these frontal photos.
            side = max(w, h) * 1.5
            cx, cy = x + w / 2, y + h / 2
            transform = cv2.getRotationMatrix2D((cx, cy), 0, 256 / side)
            transform[:, 2] += (128 - cx, 128 - cy)
            crop = cv2.warpAffine(image, transform, (256, 256), flags=cv2.INTER_LINEAR)
        rgb = cv2.cvtColor(crop, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        outputs = session.run(None, {session.get_inputs()[0].name: rgb[None]})
        points = outputs[0].reshape(478, 3)
        print(f"\n{name}: image={width}x{height}, source={'YuNet box ' + str(face[:4] / scale) if faces is not None and len(faces) else 'aligned crop'}")
        print("face presence outputs:", [v.ravel().tolist() for v in outputs[1:]])
        print("XY range:", points[:, :2].min(axis=0), points[:, :2].max(axis=0))
        print("XY units: pixels in 256x256 crop" if points[:, :2].max() > 2 else "XY units: normalized")
        for group, indices in GROUPS.items():
            print(group, " ".join(f"{idx}=({points[idx, 0]:.1f},{points[idx, 1]:.1f})" for idx in indices))
        canvas = cv2.resize(crop, (1024, 1024), interpolation=cv2.INTER_LINEAR)
        for i, pt in enumerate(points):
            cv2.circle(canvas, tuple(np.round(pt[:2] * 4).astype(int)), 2, (160, 170, 190), -1)
        colors = {"upper_lip": (0, 0, 255), "lower_lip": (255, 0, 255),
                  "inner_upper": (0, 255, 255), "inner_lower": (0, 255, 255),
                  "left_eye": (0, 255, 0), "right_eye": (0, 255, 0),
                  "philtrum": (255, 255, 0)}
        for group, indices in GROUPS.items():
            pts = np.round(points[indices, :2] * 4).astype(np.int32)
            if group != "philtrum":
                cv2.polylines(canvas, [pts], False, colors[group], 2)
            for idx, pt in zip(indices, pts):
                cv2.circle(canvas, tuple(pt), 4, colors[group], -1)
                cv2.putText(canvas, str(idx), tuple(pt + (5, -5)), cv2.FONT_HERSHEY_SIMPLEX,
                            0.32, colors[group], 1, cv2.LINE_AA)
        output = TEMP / f"facemesh_test_{name.replace('_', '')}.png"
        cv2.imwrite(str(output), canvas)
        print("visualization:", output)


if __name__ == "__main__":
    main()
