"""Direct-mask beauty evaluation for the frozen Phase 4.3.2 archive sample."""
import hashlib
import json
import random
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort

import evaluate_phase432_random20 as base
from evaluate_phase431_foreground_detection import box

OUT = base.ROOT / 'build/reports/beauty-phase432-random20-beauty'
BG = (246, 249, 251)
WHITE = (255, 255, 255)
BORDER = (220, 226, 232)
SAND = (234, 239, 244)
INK = (36, 36, 36)
SECONDARY = (84, 88, 92)
BRAND = (87, 115, 194)
MINT = (87, 139, 46)
PRESETS = {'studio': (60, 50, 25, 65), 'bright': (70, 75, 20, 55),
           'strong': (85, 95, 25, 40)}


def save(path, image):
    base.save(path, image)


def beauty(image, mask, preset):
    smoothing, whitening, rosy, detail = preset
    lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB).astype(np.float32)
    y = lab[:, :, 0] / 255.0
    small = cv2.resize(y, (max(1, image.shape[1] // 4), max(1, image.shape[0] // 4)), interpolation=cv2.INTER_AREA)
    smooth = cv2.bilateralFilter(small, 9, .08, 6)
    smooth = cv2.resize(smooth, (image.shape[1], image.shape[0]), interpolation=cv2.INTER_LINEAR)
    value = y - smoothing / 100 * (1 - .70 * detail / 100) * (y - smooth)
    highlight = np.clip((.98 - value) / .23, 0, 1)
    value = np.clip(value + .28 * whitening / 100 * value * (1 - value) * highlight, 0, 1)
    lab[:, :, 0] = value * 255
    lab[:, :, 1] += rosy / 100 * 3
    enhanced = cv2.cvtColor(np.clip(lab, 0, 255).astype(np.uint8), cv2.COLOR_LAB2BGR)
    alpha = mask[:, :, None].astype(np.float32)
    result = np.clip(image * (1 - alpha) + enhanced * alpha, 0, 255).astype(np.uint8)
    result[mask == 0] = image[mask == 0]
    return result


def sheet(rows, path, labels):
    width, cell, header, row_height = 300, 290, 42, 228
    canvas = np.full((header + len(rows) * row_height, len(labels) * width, 3), BG, np.uint8)
    for col, label in enumerate(labels):
        x = col * width
        cv2.rectangle(canvas, (x, 0), (x + width - 1, header - 1), SAND, -1)
        cv2.putText(canvas, label, (x + 12, 27), cv2.FONT_HERSHEY_SIMPLEX, .52, INK, 1, cv2.LINE_AA)
    for row, (caption, images) in enumerate(rows):
        y = header + row * row_height
        for col, image in enumerate(images):
            x = col * width + 5
            cv2.rectangle(canvas, (x, y + 5), (x + cell, y + row_height - 5), WHITE, -1)
            cv2.rectangle(canvas, (x, y + 5), (x + cell, y + row_height - 5), BORDER, 1)
            if col == 0:
                cv2.putText(canvas, caption[:39], (x + 9, y + 22), cv2.FONT_HERSHEY_SIMPLEX, .42, SECONDARY, 1, cv2.LINE_AA)
            h, w = image.shape[:2]
            factor = min(274 / w, 187 / h)
            tile = cv2.resize(image, (max(1, round(w * factor)), max(1, round(h * factor))), interpolation=cv2.INTER_AREA if factor < 1 else cv2.INTER_LINEAR)
            px = x + (cell - tile.shape[1]) // 2
            py = y + 30 + (187 - tile.shape[0]) // 2
            canvas[py:py + tile.shape[0], px:px + tile.shape[1]] = tile
    save(path, canvas)


def comparison(images, labels, face=None):
    width, height = 480, 540
    canvas = np.full((height, width * len(images), 3), BG, np.uint8)
    for i, (image, label) in enumerate(zip(images, labels)):
        if face is not None:
            x, y, w, h = map(int, face[:4])
            side = max(w, h, 1)
            cx, cy = x + w // 2, y + h // 2
            x0 = max(0, min(image.shape[1] - side, cx - side // 2))
            y0 = max(0, min(image.shape[0] - side, cy - side // 2))
            image = image[y0:y0 + side, x0:x0 + side]
        ih, iw = image.shape[:2]
        scale = min(460 / iw, 490 / ih)
        tile = cv2.resize(image, (max(1, round(iw * scale)), max(1, round(ih * scale))))
        px = i * width + (width - tile.shape[1]) // 2
        py = 44 + (490 - tile.shape[0]) // 2
        canvas[py:py + tile.shape[0], px:px + tile.shape[1]] = tile
        cv2.putText(canvas, label, (i * width + 14, 29), cv2.FONT_HERSHEY_SIMPLEX, .65, BRAND if i else INK, 2, cv2.LINE_AA)
    return canvas


def main():
    cv2.setNumThreads(1)
    files = sorted(base.ARCHIVE.rglob('*.jpg'), key=lambda p: (p.name, str(p.relative_to(base.ARCHIVE))))
    if len(files) != 125:
        raise RuntimeError(f'Expected 125 JPG photographs, found {len(files)}')
    selected = random.Random(base.SEED).sample(files, 20)
    models = base.ROOT / 'app-resources/models'
    for name in ('face_detection_yunet_2023mar.onnx', 'bisenet_face_parsing.onnx'):
        if not (models / name).is_file():
            raise FileNotFoundError(models / name)
    run = OUT / ('run_' + datetime.now().strftime('%Y%m%d_%H%M%S'))
    run.mkdir(parents=True, exist_ok=False)
    manifest = {'seed': base.SEED, 'total_eligible_files': len(files), 'cases': []}
    detector = cv2.FaceDetectorYN.create(str(models / 'face_detection_yunet_2023mar.onnx'), '', (640, 640), .10, .30)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    parsing = ort.InferenceSession(str(models / 'bisenet_face_parsing.onnx'), options, providers=['CPUExecutionProvider'])
    panels, presets = [], []
    for i, path in enumerate(selected, 1):
        image = cv2.imdecode(np.fromfile(str(path), np.uint8), cv2.IMREAD_COLOR)
        if image is None:
            raise RuntimeError(f'Decode failed: {path}')
        h, w = image.shape[:2]
        case = run / f'cases/case_{i:02d}'
        case.mkdir(parents=True)
        input_image, scale, dx, dy = base.letterbox(image)
        detector.setInputSize((640, 640))
        raw = detector.detect(input_image)[1]
        faces = [] if raw is None else [f.copy() for f in raw]
        for f in faces:
            f[[0, 4, 6, 8, 10, 12]] = (f[[0, 4, 6, 8, 10, 12]] - dx) / scale
            f[[1, 5, 7, 9, 11, 13]] = (f[[1, 5, 7, 9, 11, 13]] - dy) / scale
            f[[2, 3]] /= scale
        roles = base.select(faces, w, h)
        detected, selection = image.copy(), image.copy()
        composite = np.zeros((h, w), np.float32)
        warnings, details = [], []
        if not faces:
            warnings.append('NO_DETECTIONS')
        if not any(r['role'] == 'FOREGROUND_CUSTOMER' for r in roles):
            warnings.append('NO_FOREGROUND_CUSTOMERS')
        for j, (f, role) in enumerate(zip(faces, roles), 1):
            box(detected, f, (255, 200, 0), f'F{j} {f[14]:.2f}', landmarks=True)
            color = {'FOREGROUND_CUSTOMER': (30, 250, 35), 'BACKGROUND_BYSTANDER': (0, 155, 255), 'REJECTED': (0, 0, 255)}[role['role']]
            box(selection, f, color, f'F{j} {role["role"]} {f[14]:.2f}', dashed=role['role'] == 'BACKGROUND_BYSTANDER')
            entry = {'face_id': f'F{j}', 'bounds_xywh': [float(x) for x in f[:4]], 'landmarks_xy': f[4:14].reshape(5, 2).tolist(), 'confidence': float(f[14]), **role}
            if role['role'] == 'FOREGROUND_CUSTOMER':
                try:
                    mask, metrics, stages = base.mask_face(image, f, parsing)
                    np.maximum(composite, mask, out=composite)
                    entry['mask_metrics'] = metrics
                    entry['mask_timings_ms'] = stages
                except Exception as exc:
                    entry['mask_warning'] = str(exc)
                    warnings.append(f'F{j}: MASK_FAILED: {exc}')
            details.append(entry)
        green = np.array((60, 210, 40), np.float32)
        overlay = np.clip(image.astype(np.float32) * (1 - composite[:, :, None] * .40) + green * composite[:, :, None] * .40, 0, 255).astype(np.uint8)
        gray = np.uint8(np.clip(composite * 255, 0, 255))
        studio, bright, strong = (beauty(image, composite, PRESETS[name]) for name in PRESETS)
        primary = next((f for f, r in zip(faces, roles) if r['role'] == 'FOREGROUND_CUSTOMER'), None)
        outputs = {'01_original.jpg': image, '02_detected_faces.jpg': detected, '03_foreground_selection.jpg': selection,
                   '04_composite_mask.jpg': gray, '05_green_overlay.jpg': overlay, '06_beauty_preview_bright.jpg': bright,
                   '07_beauty_preview_studio.jpg': studio, '08_beauty_preview_strong.jpg': strong,
                   '09_before_vs_after_bright.jpg': comparison([image, overlay, bright], ['Original', 'Skin overlay', 'Beauty Bright']),
                   '10_zoomed_face_comparison.jpg': comparison([image, bright], ['Original 1:1', 'Beauty Bright 1:1'], primary) if primary is not None else comparison([image, bright], ['Original', 'Beauty Bright'])}
        for name, bitmap in outputs.items():
            save(case / name, bitmap)
        metadata = {'image_id': f'R20_{i:02d}', 'source_filename': path.name, 'image_dimensions': [w, h],
                    'detected_faces': len(faces), 'selected_foreground_customers': sum(r['role'] == 'FOREGROUND_CUSTOMER' for r in roles),
                    'faces': details, 'warnings': warnings, 'mask_coverage': float(composite.mean()),
                    'presets': PRESETS, 'outside_mask_exact': all(np.array_equal(result[composite == 0], image[composite == 0]) for result in (studio, bright, strong))}
        base.write_json(case / 'case_metadata.json', metadata)
        manifest['cases'].append({'anonymous_id': f'R20_{i:02d}', 'case_dir': f'cases/case_{i:02d}', 'original_filename': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        caption = f'R20_{i:02d} | {path.name}'
        panels.append((caption, [image, selection, cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR), overlay, bright]))
        presets.append((caption, [image, studio, bright, strong]))
        print(f'{i}/20 {path.name}: {len(faces)} detected, {metadata["selected_foreground_customers"]} customers, {warnings}', flush=True)
    base.write_json(run / 'selection_manifest.json', manifest)
    for name, rows, labels in (
        ('contact_sheet_beauty_20.jpg', panels, ['1. Original', '2. Detections & Selection', '3. Skin Alpha Mask', '4. Green Overlay', '5. Beauty Bright Preview']),
        ('contact_sheet_beauty_part1.jpg', panels[:10], ['1. Original', '2. Detections & Selection', '3. Skin Alpha Mask', '4. Green Overlay', '5. Beauty Bright Preview']),
        ('contact_sheet_beauty_part2.jpg', panels[10:], ['1. Original', '2. Detections & Selection', '3. Skin Alpha Mask', '4. Green Overlay', '5. Beauty Bright Preview']),
        ('contact_sheet_presets_comparison.jpg', presets, ['Original', 'Studio', 'Bright', 'Strong'])):
        sheet(rows, OUT / name, labels)
    base.write_json(run / 'beauty_summary.json', {'seed': base.SEED, 'cases': len(panels), 'presets': PRESETS})
    print(f'COMPLETE {run}', flush=True)


if __name__ == '__main__':
    try:
        main()
    finally:
        # Release any HighGUI windows even if report generation fails.
        cv2.destroyAllWindows()
