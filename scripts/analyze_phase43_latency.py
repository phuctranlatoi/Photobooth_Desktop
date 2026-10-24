"""Summarize recorded Phase 4.3 latency; do not rerun image inference."""
import csv
import ctypes
from ctypes import wintypes
import json
import math
from pathlib import Path
import statistics

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'build/reports/beauty-phase43-realworld'
FIELDS = ('tDetectMs', 'tAlignCropMs', 'tParsingMs', 'tFaceMeshMs',
          'tMaskBuildOldMs', 'tMaskBuildNewMs', 'tGuidedFilterMs',
          'tReprojectMs', 'tTotalPipelineMs')
CANDIDATE = tuple(field for field in FIELDS[:-1] if field != 'tMaskBuildOldMs')


def stats(values):
    ordered = sorted(values)

    def percentile(p):
        position = (len(ordered) - 1) * p
        lo = int(position)
        return ordered[lo] + (ordered[min(lo + 1, len(ordered) - 1)] - ordered[lo]) * (position - lo)

    return {key: round(value, 3) for key, value in {
        'mean': statistics.mean(ordered), 'median': statistics.median(ordered),
        'p90': percentile(.90), 'p95': percentile(.95),
        'min': ordered[0], 'max': ordered[-1],
        'std': statistics.stdev(ordered) if len(ordered) > 1 else 0,
    }.items()}


def memory_snapshot():
    # A post-hoc CSV cannot recover peak memory of the original evaluation.
    if not hasattr(ctypes, 'windll'):
        return {'original_evaluation_peak_rss_bytes': None,
                'note': 'No RSS measurements in CSV; historical evaluation peak cannot be recovered.'}
    class Counters(ctypes.Structure):
        _fields_ = [('cb', wintypes.DWORD), ('PageFaultCount', wintypes.DWORD),
                    ('PeakWorkingSetSize', ctypes.c_size_t), ('WorkingSetSize', ctypes.c_size_t),
                    ('QuotaPeakPagedPoolUsage', ctypes.c_size_t), ('QuotaPagedPoolUsage', ctypes.c_size_t),
                    ('QuotaPeakNonPagedPoolUsage', ctypes.c_size_t), ('QuotaNonPagedPoolUsage', ctypes.c_size_t),
                    ('PagefileUsage', ctypes.c_size_t), ('PeakPagefileUsage', ctypes.c_size_t)]
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    psapi = ctypes.WinDLL('psapi', use_last_error=True)
    kernel.GetCurrentProcess.restype = wintypes.HANDLE
    psapi.GetProcessMemoryInfo.argtypes = (wintypes.HANDLE, ctypes.POINTER(Counters), wintypes.DWORD)
    psapi.GetProcessMemoryInfo.restype = wintypes.BOOL
    counters = Counters()
    counters.cb = ctypes.sizeof(counters)
    if not psapi.GetProcessMemoryInfo(kernel.GetCurrentProcess(), ctypes.byref(counters), counters.cb):
        raise OSError(ctypes.get_last_error(), 'GetProcessMemoryInfo failed')
    return {'analysis_process_peak_rss_bytes': counters.PeakWorkingSetSize,
            'analysis_process_rss_at_snapshot_bytes': counters.WorkingSetSize,
            'original_evaluation_peak_rss_bytes': None,
            'note': 'Measured RSS belongs to this CSV analysis process, not the original inference run.'}


def main():
    with (REPORT / 'latency_breakdown.csv').open(newline='', encoding='utf-8-sig') as stream:
        reader = csv.DictReader(stream)
        missing = set(FIELDS[:-1]) - set(reader.fieldnames or ())
        if missing:
            raise ValueError(f'Missing CSV columns: {sorted(missing)}')
        has_total = 'tTotalPipelineMs' in reader.fieldnames
        rows = []
        for line, row in enumerate(reader, 2):
            record = {'anonymous_id': row['anonymous_id'], 'face_id': int(row['face_id'])}
            for field in FIELDS[:-1]:
                value = float(row[field])
                if not math.isfinite(value) or value < 0:
                    raise ValueError(f'Invalid {field} at CSV line {line}')
                record[field] = value
            record['tTotalPipelineMs'] = float(row['tTotalPipelineMs']) if has_total else sum(record[f] for f in CANDIDATE)
            rows.append(record)
    if not rows:
        raise ValueError('Empty latency CSV')
    cold, warm = rows[:5], rows[5:]
    if not warm:
        raise ValueError('At least six face records required for warm comparison')
    result = {
        'source': str((REPORT / 'latency_breakdown.csv').relative_to(ROOT)).replace('\\', '/'),
        'face_records': len(rows), 'images_with_faces': len({r['anonymous_id'] for r in rows}),
        'cold_definition': 'first five CSV face records in file order (may span images)',
        'cold_face_records': [{'anonymous_id': r['anonymous_id'], 'face_id': r['face_id']} for r in cold],
        'warm_face_count': len(warm),
        'total_pipeline_source': ('recorded CSV column' if has_total else
            'derived candidate sum: detect + align/crop + parsing + face mesh + new mask + guided filter + reproject; excludes old mask, image decode, I/O and unrecorded overhead'),
        'detection_note': 'Detection runs once per image but its time is repeated on every face row; per-face sums are not wall-clock per-image times.',
        'units': 'milliseconds',
        'overall': {f: stats([r[f] for r in rows]) for f in FIELDS},
        'cold': {f: stats([r[f] for r in cold]) for f in FIELDS},
        'warm': {f: stats([r[f] for r in warm]) for f in FIELDS},
        'memory': memory_snapshot(),
    }
    output = REPORT / 'performance_summary.json'
    output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(f'Faces: {len(rows)}; images with faces: {result["images_with_faces"]}; cold: 5; warm: {len(warm)}')
    print(result['total_pipeline_source'])
    print(f'{"Metric":<22} {"Group":<8} {"Mean":>9} {"Median":>9} {"P90":>9} {"P95":>9} {"Min":>9} {"Max":>9} {"Std":>9}')
    for field in FIELDS:
        for group in ('overall', 'cold', 'warm'):
            s = result[group][field]
            print(f'{field:<22} {group:<8} ' + ' '.join(f'{s[k]:9.3f}' for k in ('mean', 'median', 'p90', 'p95', 'min', 'max', 'std')))
    print('Memory:', result['memory'])
    print('Saved:', output)


if __name__ == '__main__':
    main()
