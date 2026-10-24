"""Read-only YuNet investigation; output goes to build/reports/yunet-white-shirts.

Run: python scripts/investigate_yunet_white_shirts.py [--sessions N]
The model and 0.85 threshold match the test-only Kotlin YuNetFaceDetector;
OpenCV's FaceDetectorYN may differ in preprocessing/postprocessing.
"""
import argparse
import csv
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / 'build/Cloudinary_Archive_2026-10-19_12_38_162_Originals'
SESSIONS = Path('C:/Users/ASUS/AppData/Local/LeSouvenirDesktop/data/sessions')
OUT = ROOT / 'build/reports/yunet-white-shirts'
MODEL = ROOT / 'app-resources/models/face_detection_yunet_2023mar.onnx'
REQUESTED = ('Photo_1-3.jpg', 'Photo_2-5.jpg', 'Photo_3-1.jpg',
             'Photo_4-8.jpg', 'Photo_5-9.jpg', 'Photo_6-10.jpg')


def variants(image):
    h, w = image.shape[:2]
    yield 'stretch640', cv2.resize(image, (640, 640)), (0, 0, w / 640, h / 640)
    scale = min(640 / w, 640 / h)
    nw, nh = round(w * scale), round(h * scale)
    ox, oy = (640 - nw) // 2, (640 - nh) // 2
    canvas = np.zeros((640, 640, 3), dtype=np.uint8)
    canvas[oy:oy + nh, ox:ox + nw] = cv2.resize(image, (nw, nh))
    yield 'letterbox640', canvas, (-ox / scale, -oy / scale, 1 / scale, 1 / scale)
    # Model has fixed 640x640 input; this is a 1280-wide pre-scale then
    # independently resized to model input, not true 1280-resolution inference.
    enlarged = cv2.resize(image, (1280, round(h * 1280 / w)))
    yield 'prescale1280_then_stretch640', cv2.resize(enlarged, (640, 640)), (0, 0, w / 640, h / 640)
    x0, y0, x1, y1 = int(w * .08), int(h * .10), int(w * .92), int(h * .88)
    yield ('foreground_crop', cv2.resize(image[y0:y1, x0:x1], (640, 640)),
           (x0, y0, (x1 - x0) / 640, (y1 - y0) / 640))


def analyze(path, detector, writer, index):
    image = cv2.imread(str(path))
    if image is None:
        return
    h, w = image.shape[:2]
    thumbs = []
    for label, source, (ox, oy, sx, sy) in variants(image):
        detector.setInputSize((640, 640))
        _, found = detector.detect(source)
        found = [] if found is None else sorted(found, key=lambda f: -f[14])
        overlay = image.copy()
        for threshold in (.85, .50, .30, .10):
            faces = [f for f in found if f[14] >= threshold]
            writer.writerow([str(path), w, h, label, threshold, len(faces),
                             '; '.join('%.3f:(%d,%d,%d,%d)' %
                                       (f[14], ox + f[0] * sx, oy + f[1] * sy,
                                        f[2] * sx, f[3] * sy) for f in faces)])
        for f in found:
            if f[14] < .10:
                continue
            x, y, bw, bh = (round(v) for v in (ox + f[0] * sx, oy + f[1] * sy,
                                                f[2] * sx, f[3] * sy))
            color = (0, 255, 0) if f[14] >= .85 else (0, 180, 255)
            cv2.rectangle(overlay, (x, y), (x + bw, y + bh), color, max(2, w // 500))
            cv2.putText(overlay, '%.2f' % f[14], (x, max(15, y - 4)),
                        cv2.FONT_HERSHEY_SIMPLEX, .6, color, 2)
        preview = Image.fromarray(cv2.cvtColor(overlay, cv2.COLOR_BGR2RGB))
        preview.thumbnail((320, 310))
        thumbs.append((label, preview.copy()))
    sheet = Image.new('RGB', (640, 2 * 350), 'white')
    draw = ImageDraw.Draw(sheet)
    for k, (label, thumb) in enumerate(thumbs):
        x, y = (k % 2) * 320, (k // 2) * 350
        sheet.paste(thumb, (x, y + 25))
        draw.text((x + 4, y + 4), label, fill='black')
    sheet.save(OUT / ('%04d_%s.jpg' % (index, path.stem)), quality=85)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sessions', type=int, default=0,
                        help='number of session JPGs to inspect (0 = archive only; -1 = all)')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    detector = cv2.FaceDetectorYN_create(str(MODEL), '', (640, 640),
                                         score_threshold=.10, nms_threshold=.30)
    archive = [ARCHIVE / n for n in REQUESTED]
    archive += [p for p in sorted(ARCHIVE.glob('*.jpg')) if p.name not in REQUESTED]
    sessions = sorted(SESSIONS.rglob('*.jpg')) if args.sessions else []
    if args.sessions > 0:
        sessions = sessions[:args.sessions]
    with (OUT / 'detections.csv').open('w', newline='', encoding='utf-8') as file:
        writer = csv.writer(file)
        writer.writerow(['path', 'width', 'height', 'variant', 'threshold', 'count', 'score:xywh'])
        for index, path in enumerate(archive + sessions):
            analyze(path, detector, writer, index)
            if index % 100 == 0:
                print(index, '/', len(archive) + len(sessions), flush=True)
    print('Saved results to', OUT)


if __name__ == '__main__':
    main()
