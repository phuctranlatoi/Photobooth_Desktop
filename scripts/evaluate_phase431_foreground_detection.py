"""Read-only archive foreground detection diagnostic; writes only Phase 4.3 report outputs."""
import json
import time
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort

from evaluate_phase43_real_archive import process, project, save

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/reports/beauty-phase43-realworld'
CASE = OUT / 'cases/IMG_004_foreground_suite'
ARCHIVE = ROOT / 'build/Cloudinary_Archive_2026-10-19_12_38_162_Originals'
MODELS = ROOT / 'app-resources/models'
GREEN, ORANGE = (30, 250, 35), (0, 155, 255)


def read(name):
    image = cv2.imdecode(np.fromfile(str(ARCHIVE / name), np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise RuntimeError('Cannot decode ' + name)
    return image


def box(frame, face, color, label='', dashed=False, landmarks=False):
    x, y, w, h = np.round(face[:4]).astype(int)
    if dashed:
        for a in range(x, x+w, 35):
            cv2.line(frame, (a,y), (min(a+22,x+w),y), color, 6)
            cv2.line(frame, (a,y+h), (min(a+22,x+w),y+h), color, 6)
        for a in range(y, y+h, 35):
            cv2.line(frame, (x,a), (x,min(a+22,y+h)), color, 6)
            cv2.line(frame, (x+w,a), (x+w,min(a+22,y+h)), color, 6)
    else:
        cv2.rectangle(frame, (x,y), (x+w,y+h), color, 6)
    if landmarks:
        for px,py in face[4:14].reshape(5,2):
            cv2.circle(frame, (round(float(px)), round(float(py))), 7, color, -1)
    if label:
        cv2.putText(frame,label,(max(x,5),max(y-16,40)),cv2.FONT_HERSHEY_SIMPLEX,1.25,color,4,cv2.LINE_AA)


def letterbox(image, size):
    h,w=image.shape[:2]; scale=min(size/w,size/h)
    nw,nh=round(w*scale),round(h*scale)
    x,y=(size-nw)//2,(size-nh)//2
    frame=np.zeros((size,size,3),np.uint8)
    frame[y:y+nh,x:x+nw]=cv2.resize(image,(nw,nh),interpolation=cv2.INTER_AREA)
    return frame,scale,x,y


def detect(detector, frame, scale=1., x=0, y=0):
    detector.setInputSize((frame.shape[1],frame.shape[0]))
    t=time.perf_counter(); faces=detector.detect(frame)[1]; duration=(time.perf_counter()-t)*1000
    if faces is None: return [],duration
    faces=faces.copy()
    faces[:,[0,4,6,8,10,12]]=(faces[:,[0,4,6,8,10,12]]-x)/scale
    faces[:,[1,5,7,9,11,13]]=(faces[:,[1,5,7,9,11,13]]-y)/scale
    faces[:,[2,3]]/=scale
    return list(faces),duration


def nms(faces, threshold=.30):
    kept=[]
    for face in sorted(faces,key=lambda f:-float(f[14])):
        x,y,w,h=face[:4]; area=max(0,w)*max(0,h)
        if any((lambda other: (lambda ix,iy,ax: ax/(area+other[2]*other[3]-ax+1e-9))(
            max(0,min(x+w,other[0]+other[2])-max(x,other[0])),
            max(0,min(y+h,other[1]+other[3])-max(y,other[1])),
            max(0,min(x+w,other[0]+other[2])-max(x,other[0]))*max(0,min(y+h,other[1]+other[3])-max(y,other[1]))))(other) > threshold for other in kept):
            continue
        kept.append(face)
    return kept


def main():
    cv2.setNumThreads(1)
    CASE.mkdir(parents=True,exist_ok=True)
    start=time.perf_counter(); image=read('Photo_1-12.jpg'); decode_ms=(time.perf_counter()-start)*1000
    h,w=image.shape[:2]
    if (w,h)!=(3984,2656): raise RuntimeError('Unexpected IMG_004 dimensions')
    save(CASE/'01_original.png',image)
    model=str(MODELS/'face_detection_yunet_2023mar.onnx')
    detector=cv2.FaceDetectorYN.create(model,'',(640,640),.10,.30)
    full,full_ms=detect(detector,image)
    full=sorted(full,key=lambda f:-float(f[2]*f[3]))
    valid=[f for f in full if f[14]>=.85]
    if len(valid)!=3: raise RuntimeError(f'Expected 3 full-scale faces >=.85, got {len(valid)}')
    old=image.copy();box(old,valid[2],ORANGE,'OLD: Face 3 only');save(CASE/'02_old_detection_overlay.png',old)
    t=time.perf_counter(); small,scale,dx,dy=letterbox(image,640);resize_ms=(time.perf_counter()-t)*1000
    low,low_ms=detect(detector,small,scale,dx,dy)
    t=time.perf_counter(); medium,scale960,dx960,dy960=letterbox(image,960);resize960_ms=(time.perf_counter()-t)*1000
    med,med_ms=detect(detector,medium,scale960,dx960,dy960)
    # OpenCV FaceDetectorYN performs internal NMS. These are low-threshold post-NMS proposals,
    # not raw stride tensors; do not misrepresent them as pre-threshold raw candidates.
    raw=image.copy()
    for f in full: box(raw,f,(255,180,40),f'{f[14]:.2f}')
    save(CASE/'03_raw_candidates.png',raw)
    confidence=image.copy()
    for f in full:
        if f[14]>=.5: box(confidence,f,GREEN if f[14]>=.85 else ORANGE,f'{f[14]:.2f}')
    save(CASE/'04_after_confidence.png',confidence)
    after=image.copy()
    for f in nms([f for f in full if f[14]>=.5]):box(after,f,GREEN,f'{f[14]:.2f}')
    save(CASE/'05_after_nms.png',after)
    multi=image.copy()
    for f in low:box(multi,f,(255,200,0),f'640 {f[14]:.2f}')
    for f in full:box(multi,f,GREEN,f'full {f[14]:.2f}')
    save(CASE/'06_multi_scale_candidates.png',multi)
    final=image.copy()
    for f in valid:box(final,f,GREEN,f'{f[14]:.3f}',landmarks=True)
    save(CASE/'07_final_valid_detections.png',final)
    selected=image.copy()
    for i,f in enumerate(valid[:2],1):box(selected,f,GREEN,f'CUSTOMER #{i} | {f[2]*f[3]:.0f} px | {f[14]:.3f}')
    box(selected,valid[2],ORANGE,f'BACKGROUND_BYSTANDER #3 | {valid[2][14]:.3f}',dashed=True)
    save(CASE/'08_foreground_selected_customers.png',selected)
    rejected=image.copy()
    for f in full:
        if f[14]<.85:box(rejected,f,ORANGE,f'REJECTED {f[14]:.2f}')
    save(CASE/'09_rejected_candidates.png',rejected)
    opts=ort.SessionOptions();opts.intra_op_num_threads=2
    parsing=ort.InferenceSession(str(MODELS/'bisenet_face_parsing.onnx'),opts,providers=['CPUExecutionProvider'])
    mesh_path=ROOT/'build/diagnostic-models/face_landmarks_478.onnx'
    mesh=ort.InferenceSession(str(mesh_path),opts,providers=['CPUExecutionProvider'])
    overlay=image.copy()
    mask_metrics=[]
    for face in valid[:2]:
        metrics,data=process(image,face,parsing,mesh)
        alpha=data[6]; matrix=data[8]
        x,y,bw,bh=face[:4]; pad=max(bw,bh)*1.4
        x0=max(0,int(x-pad));y0=max(0,int(y-pad));x1=min(w,int(x+bw+pad));y1=min(h,int(y+bh+pad))
        region=overlay[y0:y1,x0:x1]
        local=matrix.copy();local[:,2]+=matrix[:,:2]@np.array([x0,y0],np.float32)
        a=project(alpha,local,region.shape,True)[:,:,None]*.45
        region[:]=np.uint8(region*(1-a)+np.array([60,210,40])*a)
        mask_metrics.append({'face':len(mask_metrics)+1,'philtrum_alpha_new':metrics['philtrum_alpha_new']})
    save(CASE/'10_final_mask_overlay_customers.png',overlay)
    def panel(frame,title):
        view=cv2.resize(frame,(1200,800),interpolation=cv2.INTER_AREA)
        cv2.rectangle(view,(0,0),(1200,94),(0,0,0),-1)
        for idx,line in enumerate(title):cv2.putText(view,line,(12,32+idx*29),cv2.FONT_HERSHEY_SIMPLEX,.65,(255,255,255),2,cv2.LINE_AA)
        return view
    comparison=cv2.hconcat([panel(old,['OLD DIAGNOSTIC','Small Background Face Only, Foreground Omitted']),panel(overlay,['NEW FOREGROUND SELECTION','Both Foreground Customers Correctly Selected & Processed'])])
    save(CASE/'11_before_after_detection_comparison.png',comparison)
    roles=['foreground_customer','foreground_customer','background_bystander']
    metadata=[]
    for idx,f in enumerate(valid):
        area=float(f[2]*f[3]);metadata.append({'face':idx+1,'bboxXYWH':[round(float(v),2) for v in f[:4]],'landmarksXY':np.round(f[4:14].reshape(5,2),2).astype(float).tolist(),'confidence':round(float(f[14]),5),'areaPixels':round(area,2),'areaRatio':round(float(area/(valid[2][2]*valid[2][3])),4),'imageAreaFraction':round(area/(w*h),6),'role':roles[idx],'reasons':['larger face than bystander','selected for dual-person mask'] if idx<2 else ['smaller face than two foreground customers','excluded from beauty overlay']})
    (CASE/'12_per_face_detection_metadata.json').write_text(json.dumps({'image':'IMG_004','size':[w,h],'faces':metadata,'note':'Face numbering by descending full-resolution bbox area; other lower-confidence proposals may also depict people.'},indent=2)+'\n',encoding='utf-8')
    runner=read('Photo_6-5.jpg');rframe,rs,rx,ry=letterbox(runner,640);rfaces,rms=detect(detector,rframe,rs,rx,ry)
    runner_valid=sorted([f for f in rfaces if f[14]>=.85],key=lambda f:-float(f[2]*f[3]))
    scr=ort.InferenceSession(str(MODELS/'scrfd_500m.onnx'),opts,providers=['CPUExecutionProvider'])
    scr_results=[]
    for name,frame in [('IMG_004',small),('IMG_108',rframe)]:
        blob=cv2.dnn.blobFromImage(frame,1/128,(640,640),(127.5,127.5),swapRB=True)
        t=time.perf_counter();outs=scr.run(None,{scr.get_inputs()[0].name:blob});lat=(time.perf_counter()-t)*1000
        counts={str(th):int(sum(np.count_nonzero(out[:,0]>=th) for out in outs[:3])) for th in (.5,.85)}
        scr_results.append({'image':name,'onnxMs':round(lat,2),'preNmsCandidateCounts':counts})
    report=f'''# Phase 4.3.1 foreground detection investigation

## Scope and provenance
Read-only archive inputs: IMG_004 `Photo_1-12.jpg` ({w}x{h}) and IMG_108 `Photo_6-5.jpg`. No production code changed. Outputs are diagnostic only. Models: YuNet 2023mar, SCRFD 500m, BiSeNet face parsing, and diagnostic FaceMesh 478. CPU OpenCV and ONNX Runtime; single OpenCV thread, two ORT threads.

## Deliverables 1–12
1. Original image saved as `01_original.png`.
2. Reconstructed old single-background-face overlay: `02_old_detection_overlay.png` (not a captured historic screenshot).
3. `03_raw_candidates.png` shows low-threshold **post-internal-NMS** full-scale YuNet proposals; raw pre-threshold per-stride tensors are NOT shown. This limitation is explicit to avoid claiming nonexistent evidence.
4. `04_after_confidence.png` distinguishes >=0.50 and >=0.85.
5. `05_after_nms.png` shows additional IoU 0.30 NMS on >=0.50 proposals; OpenCV already performed internal NMS.
6. `06_multi_scale_candidates.png` contrasts 640 letterbox and full-scale proposals (960 benchmark separate).
7. `07_final_valid_detections.png` has {len(valid)} >=0.85 full-resolution faces and five landmarks each.
8. `08_foreground_selected_customers.png` selects two larger foreground customers; small background face marked orange, unselected. Other lower-confidence people are not asserted to be non-faces.
9. `09_rejected_candidates.png` shows below-0.85 proposals, NOT verified non-faces.
10. `10_final_mask_overlay_customers.png` runs the existing diagnostic BiSeNet + Phase 4.3 mask engine on both selected faces, projecting green masks onto the original; bystander not processed. Metrics: {json.dumps(mask_metrics)}.
11. `11_before_after_detection_comparison.png` is a labeled reconstructed side-by-side comparison.
12. `12_per_face_detection_metadata.json` records geometry, landmarks, confidence, area, relative area, image fraction, role, selection reasons.

## IMG_004 evidence
{json.dumps(metadata,indent=2)}

## IMG_108 letterbox validation
640x640 letterbox: {len(runner_valid)} detections at >=0.85; scores {', '.join(f'{float(f[14]):.5f}' for f in runner_valid)}; all low-threshold detections: {len(rfaces)}. These are measured values, not assumed scores. Zero-detection claim applies only to the earlier pipeline, not verified here unless separately reproduced.

## SCRFD 500m benchmark
640x640 normalized RGB input `(pixel-127.5)/128`; score-head counts BEFORE SCRFD bbox decode / NMS (not validated final faces): {json.dumps(scr_results)}. This is an inference/candidate benchmark only, not proof of fallback parity.

## Performance (one cold-ish sample, not statistically stable)
IMG_004 decode {decode_ms:.2f} ms; 640 resize/letterbox {resize_ms:.2f} ms; full-resolution OpenCV YuNet detect (includes ONNX, decode, NMS) {full_ms:.2f} ms; 640 detect {low_ms:.2f} ms; 960 resize {resize960_ms:.2f} ms, detect {med_ms:.2f} ms; 640+960 detect {low_ms+med_ms:.2f} ms; >=0.85 selector negligible Python overhead; IMG_108 640 detect {rms:.2f} ms. Measured full/640 inference ratio {full_ms/max(low_ms,.001):.1f}x. OpenCV API does not expose separate ONNX/decode/NMS durations: no fabricated breakdown. Native full-scale inference uses dynamic OpenCV DNN sizing whereas raw ORT model has fixed 640 input.

## Limitations / follow-up
Raw stride-level YuNet candidate visualization and precise ONNX/decoding/NMS decomposition require instrumenting the detector or decoding its tensors; neither is represented by the provided OpenCV post-NMS observations. SCRFD candidate counts do not constitute final detections. Face ordering and foreground classification are diagnostic heuristics, not production changes. Review all overlays visually before making acceptance claims.
'''
    (OUT/'phase431_detection_report.md').write_text(report,encoding='utf-8')
    expected=[f'{i:02d}_' for i in range(1,13)]
    files=list(CASE.iterdir())
    assert len(files)==12 and all(any(p.name.startswith(prefix) and p.stat().st_size>0 for p in files) for prefix in expected)
    print('Verified 12 artifacts; report:',OUT/'phase431_detection_report.md',flush=True)

if __name__=='__main__':main()


