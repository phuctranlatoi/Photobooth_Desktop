"""Reproducible, read-only random archive audit. Requires opencv, numpy, onnxruntime."""
import csv
import hashlib
import json
import random
import shutil
import statistics
import time
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort

from evaluate_phase431_foreground_detection import box

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / 'build/Cloudinary_Archive_2026-10-19_12_38_162_Originals'
OUT = ROOT / 'build/reports/beauty-phase432-random20'
SEED = 20261024
FIELDS = ('image_id,source_filename,detected_faces,selected_foreground_customers,background_bystanders,'
          'detection_warnings,selection_warnings,mask_warnings,fallback_count,processing_time_ms,'
          'human_review_status,diagnostic_folder').split(',')


def elapsed(start):
    return round((time.perf_counter() - start) * 1000, 3)


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def save(path, image):
    ok, encoded = cv2.imencode('.jpg', image, [cv2.IMWRITE_JPEG_QUALITY, 95])
    if not ok:
        raise RuntimeError(f'JPEG encoding failed: {path}')
    path.write_bytes(encoded.tobytes())


def letterbox(image):
    h, w = image.shape[:2]
    scale = min(640 / w, 640 / h)
    dx, dy = (640 - w * scale) / 2, (640 - h * scale) / 2
    transform = np.array([[scale, 0, dx], [0, scale, dy]], np.float32)
    return cv2.warpAffine(image, transform, (640, 640), flags=cv2.INTER_LINEAR,
                          borderMode=cv2.BORDER_CONSTANT), scale, dx, dy


def select(faces, width, height):
    valid = [f for f in faces if np.isfinite(f).all() and f[14] >= .80 and f[2] > 0 and f[3] > 0]
    largest = max((float(f[2] * f[3]) for f in valid), default=0)
    metrics = []
    for f in faces:
        good = any(f is v for v in valid)
        area = float(f[2] * f[3]) if good else 0
        ratio = area / largest if largest else 0
        fraction = area / (width * height)
        dist = np.hypot((f[0] + f[2]/2 - width/2)/(width/2),
                        (f[1] + f[3]/2 - height/2)/(height/2))
        centrality = float(1 - np.clip(dist / np.sqrt(2), 0, 1))
        score = .60 * np.sqrt(ratio) + .25 * centrality + .15 * float(f[14]) if good else None
        metrics.append(dict(areaRatio=ratio, imageAreaFraction=fraction, centrality=centrality,
                            priorityScore=score, valid=good))
    primary = [i for i, m in enumerate(metrics) if m['valid'] and m['areaRatio'] >= .28 and m['imageAreaFraction'] >= .0035]
    for i, m in enumerate(metrics):
        cluster = m['valid'] and m['areaRatio'] >= .22 and any(
            j != i and abs(faces[i][1]+faces[i][3]/2-faces[j][1]-faces[j][3]/2)
            <= max(faces[i][3], faces[j][3]) for j in primary)
        m['role'] = ('REJECTED' if not m['valid'] else 'FOREGROUND_CUSTOMER' if i in primary or cluster
                     else 'BACKGROUND_BYSTANDER')
        m['reason'] = ('confidence < 0.80 or invalid geometry' if not m['valid'] else
                       'primary foreground' if i in primary else 'clustered with primary' if cluster else
                       'below foreground size thresholds')
        del m['valid']
    return metrics


def guided(image, source):
    # Subsample by two; radius eight on the reduced grid, epsilon 1e-3.
    small = (256, 256)
    guide = cv2.resize(image, small, interpolation=cv2.INTER_AREA).astype(np.float32)
    target = cv2.resize(source, small, interpolation=cv2.INTER_AREA).astype(np.float32)
    blur = lambda a: cv2.boxFilter(a, -1, (17, 17), normalize=True)
    mean_g, mean_p = blur(guide), blur(target)
    a = (blur(guide * target) - mean_g * mean_p) / (blur(guide * guide) - mean_g ** 2 + 1e-3)
    b = mean_p - a * mean_g
    return np.clip(cv2.resize(blur(a), (512, 512)) * image + cv2.resize(blur(b), (512, 512)), 0, 1)


def mask_face(image, face, parsing):
    timings = {}
    start = time.perf_counter()
    eyes = face[4:8].reshape(2, 2)
    mouth = face[8:10]
    delta = eyes[1] - eyes[0]
    distance = float(np.linalg.norm(delta))
    if distance <= 2:
        raise ValueError('eye distance <= 2 pixels')
    angle = float(np.arctan2(delta[1], delta[0]))
    scale = 512 / (3.6 * distance)
    c, s = np.cos(-angle) * scale, np.sin(-angle) * scale
    center = (eyes[0] + eyes[1]) / 2
    matrix = np.array([[c, -s, 256-c*center[0]+s*center[1]],
                       [s, c, 512*.38-s*center[0]-c*center[1]]], np.float32)
    crop = cv2.warpAffine(image, matrix, (512, 512), flags=cv2.INTER_LINEAR,
                          borderMode=cv2.BORDER_REFLECT_101)
    mc = matrix[:, :2] @ mouth + matrix[:, 2]
    d = 512 / 3.6
    timings['alignment_ms'] = elapsed(start)
    start = time.perf_counter()
    rgb = cv2.cvtColor(crop, cv2.COLOR_BGR2RGB).astype(np.float32)/255
    inp = ((rgb - np.array([.485, .456, .406], np.float32)) /
           np.array([.229, .224, .225], np.float32)).transpose(2, 0, 1)[None]
    logits = parsing.run(None, {parsing.get_inputs()[0].name: inp})[0][0]
    if logits.shape[0] != 19:
        logits = logits.transpose(2, 0, 1)
    logits -= logits.max(axis=0, keepdims=True)
    np.exp(logits, out=logits)
    logits /= logits.sum(axis=0, keepdims=True)
    skin = logits[1]
    lips = logits[12] + logits[13]
    oral = logits[11]
    timings['parsing_ms'] = elapsed(start)
    start = time.perf_counter()
    yy, xx = np.ogrid[:512, :512]
    core = ((xx-mc[0])/(.14*d))**2 + ((yy-mc[1])/(.06*d))**2 <= 1
    philtrum = (yy < mc[1]-.02*d) & (skin >= .60) & (lips < .15)
    protected = ((lips > .30) | (oral > .30) | core) & ~philtrum
    candidate = (skin >= .5) & ~protected
    n, labels, stats, _ = cv2.connectedComponentsWithStats((~candidate).astype(np.uint8), 8)
    repaired = 0
    for k in range(1, n):
        left, top, width, height, area = map(int, stats[k])
        if area > 150 or left == 0 or top == 0 or left+width == 512 or top+height == 512:
            continue
        region = labels[top:top+height, left:left+width] == k
        border = cv2.dilate(region.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool) & ~region
        if np.all((skin[top:top+height, left:left+width] >= .5)[border]) and not np.any(protected[top:top+height, left:left+width][border]):
            candidate[top:top+height, left:left+width][region] = True
            repaired += area
    timings['mask_build_ms'] = elapsed(start)
    start = time.perf_counter()
    luminance = rgb[:,:,0]*.2126 + rgb[:,:,1]*.7152 + rgb[:,:,2]*.0722
    alpha = guided(luminance, candidate.astype(np.float32))
    alpha[protected] *= .15
    timings['guided_filter_ms'] = elapsed(start)
    start = time.perf_counter()
    projected = cv2.warpAffine(alpha, cv2.invertAffineTransform(matrix),
                               (image.shape[1], image.shape[0]), flags=cv2.INTER_LINEAR)
    projected = np.clip(projected, 0, 1)
    timings['reprojection_ms'] = elapsed(start)
    return projected, {'repaired_pixels': repaired, 'crop_mask_coverage': float(alpha.mean()),
                       'full_frame_mask_coverage': float(projected.mean())}, timings


def sheet(rows, path):
    canvas = np.full((len(rows)*214, 4*300, 3), 25, np.uint8)
    for row, (caption, images) in enumerate(rows):
        y = row*214
        cv2.putText(canvas, caption[:120], (5, y+18), cv2.FONT_HERSHEY_SIMPLEX, .42, (255,255,255), 1, cv2.LINE_AA)
        for col, image in enumerate(images):
            h, w = image.shape[:2]
            factor = min(295/w, 187/h)
            tile = cv2.resize(image, (max(1, round(w*factor)), max(1, round(h*factor))), interpolation=cv2.INTER_AREA)
            canvas[y+24:y+24+tile.shape[0], col*300:col*300+tile.shape[1]] = tile
    save(path, canvas)


def main():
    cv2.setNumThreads(1)
    files = sorted(ARCHIVE.rglob('*.jpg'), key=lambda p: (p.name, str(p.relative_to(ARCHIVE))))
    if len(files) != 125:
        raise RuntimeError(f'Expected 125 JPG photographs, found {len(files)}')
    selected = random.Random(SEED).sample(files, 20)
    for model in ('face_detection_yunet_2023mar.onnx', 'bisenet_face_parsing.onnx'):
        if not (ROOT/'app-resources/models'/model).is_file():
            raise FileNotFoundError(model)
    run = OUT / ('run_' + datetime.now().strftime('%Y%m%d_%H%M%S'))
    run.mkdir(parents=True, exist_ok=False)
    manifest = {'seed': SEED, 'total_eligible_files': len(files), 'cases': [
        {'anonymous_id': f'R20_{i:02d}', 'case_dir': f'cases/case_{i:02d}',
         'original_filename': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
        for i, path in enumerate(selected, 1)]}
    write_json(run/'selection_manifest.json', manifest)
    detector = cv2.FaceDetectorYN.create(str(ROOT/'app-resources/models/face_detection_yunet_2023mar.onnx'), '', (640,640), .10, .30)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    parsing = ort.InferenceSession(str(ROOT/'app-resources/models/bisenet_face_parsing.onnx'), options,
                                   providers=['CPUExecutionProvider'])
    rows, panels, timing_rows = [], [], []
    for i, path in enumerate(selected, 1):
        start_total = time.perf_counter()
        timing = {}
        t = time.perf_counter()
        image = cv2.imdecode(np.fromfile(str(path), np.uint8), cv2.IMREAD_COLOR)
        if image is None:
            raise RuntimeError(f'Decode failed: {path}')
        timing['decode_ms'] = elapsed(t)
        h, w = image.shape[:2]
        case = run/f'cases/case_{i:02d}'
        case.mkdir(parents=True)
        t = time.perf_counter()
        input_image, scale, dx, dy = letterbox(image)
        detector.setInputSize((640,640))
        raw = detector.detect(input_image)[1]
        faces = [] if raw is None else [f.copy() for f in raw]
        for f in faces:
            f[[0,4,6,8,10,12]] = (f[[0,4,6,8,10,12]]-dx)/scale
            f[[1,5,7,9,11,13]] = (f[[1,5,7,9,11,13]]-dy)/scale
            f[[2,3]] /= scale
        timing['detection_ms'] = elapsed(t)
        t = time.perf_counter()
        roles = select(faces, w, h)
        timing['selection_ms'] = elapsed(t)
        detected, selection = image.copy(), image.copy()
        composite = np.zeros((h,w), np.float32)
        warnings = []
        if not faces:
            warnings.append('NO_DETECTIONS')
        if not any(r['role'] == 'FOREGROUND_CUSTOMER' for r in roles):
            warnings.append('NO_FOREGROUND_CUSTOMERS')
        details = []
        for j, (f, role) in enumerate(zip(faces, roles), 1):
            box(detected, f, (255,200,0), f'F{j} {f[14]:.2f}', landmarks=True)
            color = {'FOREGROUND_CUSTOMER': (30,250,35), 'BACKGROUND_BYSTANDER': (0,155,255),
                     'REJECTED': (0,0,255)}[role['role']]
            box(selection, f, color, f'F{j} {role["role"]} {f[14]:.2f}',
                dashed=role['role'] == 'BACKGROUND_BYSTANDER')
            entry = {'face_id': f'F{j}', 'bounds_xywh': [float(x) for x in f[:4]],
                     'landmarks_xy': f[4:14].reshape(5,2).tolist(), 'confidence': float(f[14]), **role}
            if role['role'] == 'FOREGROUND_CUSTOMER':
                try:
                    mask, metrics, stages = mask_face(image, f, parsing)
                    np.maximum(composite, mask, out=composite)
                    entry['mask_metrics'] = metrics
                    for key, value in stages.items():
                        timing[key] = timing.get(key, 0) + value
                except Exception as exc:
                    entry['mask_warning'] = str(exc)
                    warnings.append(f'F{j}: MASK_FAILED: {exc}')
            details.append(entry)
        t = time.perf_counter()
        green = np.empty_like(image)
        green[:] = (60,210,40)
        overlay = np.clip(image.astype(np.float32)*(1-composite[:,:,None]*.45) +
                          green.astype(np.float32)*composite[:,:,None]*.45, 0, 255).astype(np.uint8)
        save(case/'01_original.jpg', image)
        save(case/'02_all_faces_detected.jpg', detected)
        save(case/'03_foreground_selection.jpg', selection)
        save(case/'04_final_mask_overlay.jpg', overlay)
        save(case/'05_original_vs_mask.jpg', cv2.hconcat([image, overlay]))
        timing['export_ms'] = elapsed(t)
        timing['processing_time_ms'] = elapsed(start_total)
        counts = {role: sum(r['role'] == role for r in roles) for role in
                  ('FOREGROUND_CUSTOMER', 'BACKGROUND_BYSTANDER', 'REJECTED')}
        write_json(case/'06_case_metadata.json', {'image_id': f'R20_{i:02d}', 'source_filename': path.name,
            'image_dimensions': [w,h], 'detected_faces': len(faces), 'role_counts': counts,
            'faces': details, 'warnings': warnings, 'latency_ms': timing})
        rows.append(dict(image_id=f'R20_{i:02d}', source_filename=path.name, detected_faces=len(faces),
            selected_foreground_customers=counts['FOREGROUND_CUSTOMER'], background_bystanders=counts['BACKGROUND_BYSTANDER'],
            detection_warnings='; '.join(x for x in warnings if 'DETECTION' in x),
            selection_warnings='; '.join(x for x in warnings if 'FOREGROUND' in x),
            mask_warnings='; '.join(x for x in warnings if 'MASK_FAILED' in x), fallback_count=0,
            processing_time_ms=timing['processing_time_ms'], human_review_status='NEEDS_REVIEW',
            diagnostic_folder=f'cases/case_{i:02d}'))
        timing_rows.append(timing)
        panels.append((f'R20_{i:02d} | {path.name} | faces {len(faces)} | customers {counts["FOREGROUND_CUSTOMER"]} | {"; ".join(warnings) or "OK"}',
                       [image, detected, selection, overlay]))
        print(f'{i}/20 {path.name}: {len(faces)} detected, {counts["FOREGROUND_CUSTOMER"]} customers, {warnings}', flush=True)
    with (run/'random20_audit.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    sheet(panels[:10], run/'random20_contact_sheet_part1.jpg')
    sheet(panels[10:], run/'random20_contact_sheet_part2.jpg')
    sheet(panels, run/'random20_contact_sheet.jpg')
    keys = sorted(set().union(*(r.keys() for r in timing_rows)))
    write_json(run/'performance_summary.json', {key: {'mean_ms': round(statistics.mean(r.get(key,0) for r in timing_rows),3),
        'median_ms': round(statistics.median(r.get(key,0) for r in timing_rows),3)} for key in keys})
    for file in run.iterdir():
        if file.is_file():
            shutil.copy2(file, OUT/file.name)
    print(f'COMPLETE {run}', flush=True)


if __name__ == '__main__':
    main()
