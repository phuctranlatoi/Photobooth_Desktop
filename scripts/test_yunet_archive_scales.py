"""Compare YuNet full-resolution and downscaled detections across archive JPGs.

Run: python scripts/test_yunet_archive_scales.py
Writes JSON to build/reports/yunet-scale/results.json. No application files change.
"""
import json
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / 'build/Cloudinary_Archive_2026-10-19_12_38_162_Originals'
MODEL = ROOT / 'app-resources/models/face_detection_yunet_2023mar.onnx'
OUTPUT = ROOT / 'build/reports/yunet-scale/results.json'


def detect(detector, image, width, height, letterbox=False):
    h, w = image.shape[:2]
    if (width, height) == (w, h):
        source, sx, sy, dx, dy = image, 1., 1., 0, 0
    elif letterbox:
        scale = min(width / w, height / h)
        nw, nh = round(w * scale), round(h * scale)
        dx, dy = (width - nw) // 2, (height - nh) // 2
        source = np.zeros((height, width, 3), dtype=np.uint8)
        source[dy:dy + nh, dx:dx + nw] = cv2.resize(image, (nw, nh))
        sx = sy = 1 / scale
    else:
        source = cv2.resize(image, (width, height))
        sx, sy, dx, dy = w / width, h / height, 0, 0
    detector.setInputSize((width, height))
    _, faces = detector.detect(source)
    if faces is None:
        return []
    return [{'xywh': [round((float(f[0]) - dx) * sx, 1),
                      round((float(f[1]) - dy) * sy, 1),
                      round(float(f[2]) * sx, 1), round(float(f[3]) * sy, 1)],
             'score': round(float(f[14]), 5)} for f in sorted(faces, key=lambda f: -f[14])]


def main():
    cv2.setNumThreads(1)
    detector = cv2.FaceDetectorYN_create(str(MODEL), '', (640, 640),
                                         score_threshold=.10, nms_threshold=.30)
    files = sorted(ARCHIVE.glob('*.jpg'))
    assert len(files) == 125, f'Expected 125 JPGs; found {len(files)}'
    results = []
    for index, path in enumerate(files, 1):
        image = cv2.imread(str(path))
        if image is None:
            raise RuntimeError(f'Cannot decode {path}')
        h, w = image.shape[:2]
        variants = {'full': detect(detector, image, w, h)}
        for name, size, letterbox in [('letterbox640', (640, 640), True),
                                      ('resize640', (640, 640), False),
                                      ('resize960', (960, 640), False),
                                      ('resize1280', (1280, 854), False)]:
            variants[name] = detect(detector, image, *size, letterbox=letterbox)
        results.append({'file': path.name, 'size': [w, h], 'faces': variants})
        print(f'{index}/{len(files)} {path.name}: ' +
              ', '.join(f'{k}={sum(f["score"] >= .85 for f in v)}' for k, v in variants.items()),
              flush=True)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps({'model': str(MODEL.relative_to(ROOT)),
                                  'score_threshold': .10, 'nms_threshold': .30,
                                  'images': results}, indent=2), encoding='utf-8')
    print('Saved', OUTPUT)


if __name__ == '__main__':
    main()
