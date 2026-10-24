"""Read-only IMG_004 YuNet resolution/threshold comparison (run from repo root)."""
import csv
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'build/reports/beauty-phase43-realworld'
MODEL = ROOT / 'app-resources/models/face_detection_yunet_2023mar.onnx'


def describe(faces, scale, offset):
    if faces is None:
        print('  no faces')
        return
    faces = sorted(faces, key=lambda f: float(f[2] * f[3]), reverse=True)
    for index, face in enumerate(faces, 1):
        box = (face[:4] - np.array([offset[0], offset[1], 0, 0])) / scale
        points = (face[4:14].reshape(5, 2) - offset) / scale
        print(f'  area-rank={index} xywh={tuple(round(float(v), 1) for v in box)} '
              f'score={float(face[14]):.5f} landmarks='
              f'{[tuple(round(float(v), 1) for v in point) for point in points]}')


def main():
    with (REPORT / 'dataset_manifest.csv').open(newline='', encoding='utf-8') as stream:
        row = next(row for row in csv.DictReader(stream) if row['anonymous_id'] == 'IMG_004')
    print('IMG_004:', row['original_filename'], row['relative_path'])
    image = cv2.imdecode(np.fromfile(str(ROOT / row['relative_path']), np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise RuntimeError('Unable to decode IMG_004')
    h, w = image.shape[:2]
    print('source dimensions:', w, h)
    for label, frame, scale, offset in [('full', image, 1., (0., 0.))]:
        run(label, frame, scale, offset)
    for width, height in [(640, 640), (960, 640), (1280, 854)]:
        scale = min(width / w, height / h)
        nw, nh = round(w * scale), round(h * scale)
        left, top = (width - nw) // 2, (height - nh) // 2
        frame = np.zeros((height, width, 3), np.uint8)
        frame[top:top + nh, left:left + nw] = cv2.resize(image, (nw, nh), interpolation=cv2.INTER_AREA)
        run(f'letterbox {width}x{height}', frame, scale, (left, top))
    stretched = cv2.resize(image, (640, 640), interpolation=cv2.INTER_LINEAR)
    print('stretch 640x640: x scale', 640 / w, 'y scale', 640 / h)
    # Stretch has independent x/y coordinates; pass the inverse mapping explicitly below.
    for threshold in (.85, .70, .50, .30):
        detector = cv2.FaceDetectorYN.create(str(MODEL), '', (640, 640), threshold, .30)
        faces = detector.detect(stretched)[1]
        print(f'  threshold {threshold}:')
        if faces is None:
            print('  no faces')
        else:
            mapped = faces.copy()
            mapped[:, [0, 2, 4, 6, 8, 10, 12]] *= w / 640
            mapped[:, [1, 3, 5, 7, 9, 11, 13]] *= h / 640
            describe(mapped, 1., (0., 0.))


def run(label, frame, scale, offset):
    print(label, 'input dimensions:', frame.shape[1], frame.shape[0])
    for threshold in (.85, .70, .50, .30):
        detector = cv2.FaceDetectorYN.create(str(MODEL), '', (frame.shape[1], frame.shape[0]), threshold, .30)
        print(f'  threshold {threshold}:')
        describe(detector.detect(frame)[1], scale, offset)


if __name__ == '__main__':
    main()
