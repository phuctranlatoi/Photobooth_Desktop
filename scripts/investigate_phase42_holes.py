"""Inventory enclosed zero-valued components in phase-42 aligned skin masks."""
import json
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

ROOT = Path('build/reports/beauty-phase42/latest')
OUT = Path('build/reports/beauty-phase43-realworld/hole_investigation.json')


def inspect(session):
    folder = ROOT / session
    mask = np.asarray(Image.open(folder / '08_reconstructed_skin_mask.png').convert('RGB'))
    original = np.asarray(Image.open(folder / '01_original.png').convert('RGB'))
    parsing = np.asarray(Image.open(folder / '04_raw_semantic_parsing.png').convert('RGB'))
    height, width = mask.shape[:2]
    zeros = np.all(mask == 0, axis=2).astype(np.uint8)
    count, labels, stats, centers = cv2.connectedComponentsWithStats(zeros, 8)
    holes = []
    for label in range(1, count):
        x, y, w, h, area = map(int, stats[label])
        if x == 0 or y == 0 or x + w == width or y + h == height:
            continue
        pixels = np.argwhere(labels == label)
        colors, color_counts = np.unique(parsing[labels == label], axis=0, return_counts=True)
        colors_sorted = sorted(zip(colors.tolist(), color_counts.tolist()), key=lambda entry: -entry[1])
        holes.append({
            'center_xy': [round(float(v), 3) for v in centers[label]],
            'size_pixels': area,
            'bbox_xywh': [x, y, w, h],
            'bbox_xyxy_inclusive': [x, y, x + w - 1, y + h - 1],
            'parsing_rgb_counts': [{'rgb': rgb, 'count': n} for rgb, n in colors_sorted],
            'aligned_crop_center_rgb': np.asarray(Image.open(folder / '03_aligned_crop.png').convert('RGB'))[round(centers[label][1]), round(centers[label][0])].tolist(),
        })
    return {
        'mask_dimensions_wh': [width, height],
        'original_dimensions_wh': [original.shape[1], original.shape[0]],
        'coordinate_space': 'aligned 512x512 crop; original is a different coordinate space',
        'interior_zero_component_count': len(holes),
        'diagnostic_definition': 'These components are enclosed RGB=(0,0,0) pixels with 8-connectivity, NOT the diagnostic interiorHolesCount (which uses unexported geometry/protected/cropFinal/probability arrays and 4-connectivity).',
        'holes': sorted(holes, key=lambda hole: (hole['bbox_xywh'][1], hole['bbox_xywh'][0])),
    }


if __name__ == '__main__':
    result = {session: inspect(session) for session in ('session_01', 'session_12')}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    for session, data in result.items():
        print(session, data['interior_zero_component_count'])
        for hole in data['holes']:
            print(hole['bbox_xywh'], hole['size_pixels'], hole['center_xy'], hole['parsing_rgb_counts'][:3])
