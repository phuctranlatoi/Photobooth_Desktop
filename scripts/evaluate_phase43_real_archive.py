"""Serial, bounded-memory Phase 4.3 evaluation of the read-only photo archive."""
import csv
import gc
import json
import time
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT / 'build/Cloudinary_Archive_2026-10-19_12_38_162_Originals'
OUT = ROOT / 'build/reports/beauty-phase43-realworld'
GROUPS = {'lips': [61,185,40,39,37,0,267,269,270,409,291,375,321,405,314,17,84,181,91,146],
          'left_eye': [33,160,158,133,153,144], 'right_eye': [362,385,387,263,373,380],
          'left_brow': [70,63,105,66,107], 'right_brow': [336,296,334,293,300],
          'jaw': [10,338,297,332,284,251,389,356,454,323,361,288,397,365,379,378,400,377,152,148,176,149,150,136,172,58,132,93,234,127,162,21,54,103,67,109]}


def clock():
    return time.perf_counter()


def ms(start):
    return round((clock() - start) * 1000, 3)


def save(path, image):
    ok, data = cv2.imencode('.png', image)
    if not ok:
        raise RuntimeError(f'Cannot encode {path}')
    path.write_bytes(data.tobytes())


def ellipse(shape, center, rx, ry):
    mask = np.zeros(shape, np.uint8)
    cv2.ellipse(mask, (int(center[0]), int(center[1])), (max(1,int(rx)), max(1,int(ry))), 0, 0, 360, 1, -1)
    return mask.astype(bool)


def guided(image, source):
    small = (256, 256)
    I = cv2.resize(image, small, interpolation=cv2.INTER_AREA).astype(np.float32)
    p = cv2.resize(source, small, interpolation=cv2.INTER_AREA).astype(np.float32)
    blur = lambda x: cv2.boxFilter(x, -1, (9,9), normalize=True)
    mi, mp = blur(I), blur(p)
    a = (blur(I*p)-mi*mp)/(blur(I*I)-mi*mi+1e-3)
    b = mp-a*mi
    return np.clip(cv2.resize(blur(a), (512,512))*image + cv2.resize(blur(b), (512,512)), 0, 1)


def project(alpha, matrix, shape, soft):
    inverse = cv2.invertAffineTransform(matrix)
    result = cv2.warpAffine(alpha.astype(np.float32), inverse, (shape[1], shape[0]), flags=cv2.INTER_LINEAR)
    return np.clip(result, 0, 1) if soft else result


def process(image, face, parsing, mesh):
    timings = {}
    t = clock()
    x,y,w,h = face[:4]
    eyes = face[4:8].reshape(2,2)
    mouth = face[8:10]
    angle = np.arctan2(eyes[1,1]-eyes[0,1], eyes[1,0]-eyes[0,0])
    center = (eyes[0]+eyes[1])*0.5
    span = max(float(w), float(h))*1.65
    scale = 512/span
    c,s = np.cos(-angle)*scale, np.sin(-angle)*scale
    matrix = np.array([[c,-s,256-c*center[0]+s*center[1]], [s,c,205-s*center[0]-c*center[1]]], np.float32)
    crop = cv2.warpAffine(image, matrix, (512,512), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT_101)
    transform = lambda p: matrix[:,:2]@p + matrix[:,2]
    mc = transform(mouth)
    d = max(30., np.linalg.norm(transform(eyes[1])-transform(eyes[0])))
    timings['tAlignCropMs'] = ms(t)
    t = clock()
    rgb = cv2.cvtColor(crop, cv2.COLOR_BGR2RGB).astype(np.float32)/255
    inp = ((rgb - np.array([.485,.456,.406],np.float32))/np.array([.229,.224,.225],np.float32)).transpose(2,0,1)[None]
    logits = parsing.run([parsing.get_outputs()[0].name], {parsing.get_inputs()[0].name: inp})[0][0]
    if logits.shape[0] != 19:
        logits = logits.transpose(2,0,1)
    logits -= logits.max(axis=0, keepdims=True)
    np.exp(logits, out=logits)
    logits /= logits.sum(axis=0, keepdims=True)
    labels = np.argmax(logits, axis=0).astype(np.uint8)
    skin = logits[1]; lips = logits[12]+logits[13]; oral = logits[11]
    guidance = rgb[:,:,0]*.2126+rgb[:,:,1]*.7152+rgb[:,:,2]*.0722
    timings['tParsingMs'] = ms(t)
    t = clock()
    mesh_rgb = cv2.resize(rgb, (256,256), interpolation=cv2.INTER_AREA)
    points = mesh.run([mesh.get_outputs()[0].name], {mesh.get_inputs()[0].name: mesh_rgb[None]})[0].reshape(478,3)
    points = points[:,:2].copy()
    if points.max() <= 2:
        points *= 512
    else:
        points *= 2
    timings['tFaceMeshMs'] = ms(t)
    t = clock()
    old_core = ellipse(skin.shape, mc, .12*d, .06*d)
    old_binary = (skin >= .5) & ~old_core
    n, components, stats, _ = cv2.connectedComponentsWithStats((~old_binary).astype(np.uint8), 8)
    old_repaired = 0
    for k in range(1,n):
        if stats[k,cv2.CC_STAT_AREA] <= 1500 and not (stats[k,cv2.CC_STAT_LEFT] == 0 or stats[k,cv2.CC_STAT_TOP] == 0 or stats[k,cv2.CC_STAT_LEFT]+stats[k,cv2.CC_STAT_WIDTH] == 512 or stats[k,cv2.CC_STAT_TOP]+stats[k,cv2.CC_STAT_HEIGHT] == 512):
            component = components == k
            old_repaired += int(component.sum())
            old_binary[component] = True
    old_binary[old_core] = False
    old_alpha = cv2.blur(old_binary.astype(np.float32), (3,3))
    old_alpha[old_core] = 0
    timings['tMaskBuildOldMs'] = ms(t)
    t = clock()
    core = ellipse(skin.shape, mc, .14*d, .06*d)
    lip_gate = lips > .30
    mouth_gate = oral > .30
    protected = core & (lip_gate | mouth_gate)
    yy = np.arange(512)[:,None]
    phil_skin = (yy < mc[1]-.02*d) & (skin >= .60) & (lips < .15) & (oral < .15)
    protected[phil_skin] = False
    candidate = (skin >= .5) & ~protected
    holes = (~candidate).astype(np.uint8)
    n, components, stats, _ = cv2.connectedComponentsWithStats(holes, 8)
    repaired = 0; count = 0
    for k in range(1,n):
        area = int(stats[k,cv2.CC_STAT_AREA]); left,top,width,height = stats[k,:4]
        if area > 150 or left == 0 or top == 0 or left+width == 512 or top+height == 512:
            continue
        component = (components[top:top+height,left:left+width] == k).astype(np.uint8)
        border = cv2.dilate(component, np.ones((3,3),np.uint8)).astype(bool) & ~component.astype(bool)
        surrounding = (skin[top:top+height,left:left+width] >= .5) & ~protected[top:top+height,left:left+width]
        if np.all(surrounding[border]):
            candidate[top:top+height,left:left+width][component.astype(bool)] = True
            repaired += area; count += 1
    timings['tMaskBuildNewMs'] = ms(t)
    t = clock()
    new_alpha = guided(guidance, candidate.astype(np.float32))
    new_alpha[protected] *= .15
    timings['tGuidedFilterMs'] = ms(t)
    t = clock()
    # Project with bilinear attenuation; unlike the baseline, never recut the mouth in image space.
    _ = project(old_alpha, matrix, image.shape, False)
    _ = project(new_alpha, matrix, image.shape, True)
    timings['tReprojectMs'] = ms(t)
    px = int(np.clip(mc[0], 0,511)); py = int(np.clip(mc[1]-.04*d,0,511))
    patch = (slice(max(0,py-3),min(512,py+4)),slice(max(0,px-3),min(512,px+4)))
    old_p = float(old_alpha[patch].mean()); new_p = float(new_alpha[patch].mean())
    def coverage(region):
        return {'old': round(float(old_alpha[region].mean()),4), 'new':round(float(new_alpha[region].mean()),4)} if np.any(region) else {'old':None,'new':None}
    regions = {'chin':ellipse(skin.shape,(256,390),.55*d,.24*d), 'cheeks':ellipse(skin.shape,(256,280),1.5*d,.35*d),
               'forehead':ellipse(skin.shape,(256,125),1.1*d,.35*d), 'eyes':ellipse(skin.shape,(256,205),1.3*d,.15*d),
               'lips':ellipse(skin.shape,mc,.52*d,.13*d), 'neck':ellipse(skin.shape,(256,490),.6*d,.12*d)}
    metrics = {'philtrum_alpha_old':round(old_p,4),'philtrum_alpha_new':round(new_p,4),
               'philtrum_deficit':round(max(0,new_p-old_p),4), 'mouth_cutout_severity':round(float((new_alpha[core]-old_alpha[core]).clip(0).mean()),4),
               'holes_count':count,'repaired_pixels_count':repaired,'old_repaired_pixels_count':old_repaired,
               'regions':{name:coverage(region) for name,region in regions.items()}, **timings}
    return metrics, (crop, labels, skin, old_alpha, protected, candidate, new_alpha, points, matrix, mc)


def panel(image, title, width=400):
    view = cv2.resize(image,(width,width),interpolation=cv2.INTER_AREA)
    cv2.putText(view,title,(10,26),cv2.FONT_HERSHEY_SIMPLEX,.65,(255,255,255),2,cv2.LINE_AA)
    return view


def overlay(crop, alpha):
    result = crop.astype(np.float32).copy()
    green = np.zeros_like(result); green[:]=(60,210,40)
    result = result*(1-alpha[:,:,None]*.45)+green*alpha[:,:,None]*.45
    return result.astype(np.uint8)


def artifacts(directory, original, face, metrics, data):
    directory.mkdir(parents=True,exist_ok=True)
    crop,labels,skin,old,protected,reconstructed,new,points,matrix,mc = data
    detected = original.copy()
    cv2.rectangle(detected,tuple(face[:2].astype(int)),tuple((face[:2]+face[2:4]).astype(int)),(0,255,0),3)
    for p in face[4:14].reshape(5,2): cv2.circle(detected,tuple(p.astype(int)),5,(0,200,255),-1)
    color = cv2.applyColorMap((labels*13).astype(np.uint8),cv2.COLORMAP_TURBO)
    def mask(m): return np.uint8(np.clip(m*255,0,255))
    old_view=overlay(crop,old); new_view=overlay(crop,new)
    save(directory/'01_original.png',original)
    save(directory/'02_detected_landmarks.png',detected)
    save(directory/'03_aligned_crop.png',crop)
    save(directory/'04_raw_semantic_parsing.png',color)
    save(directory/'05_raw_skin_probability.png',mask(skin))
    save(directory/'06_old_mask.png',mask(old))
    save(directory/'07_protected_regions.png',mask(protected))
    save(directory/'08_reconstructed_skin_mask.png',mask(reconstructed))
    save(directory/'09_final_soft_alpha.png',mask(new))
    save(directory/'10_final_green_overlay.png',new_view)
    focus=[]
    for name,cx,cy,side in [('LIPS / PHILTRUM',int(mc[0]),int(mc[1]),110),('EYES / BROWS',256,205,170),('CHEEKS / GLASSES',256,285,220),('CHIN / JAW',256,390,150)]:
        x0=max(0,cx-side); x1=min(512,cx+side); y0=max(0,cy-side//2); y1=min(512,cy+side//2)
        focus.append(cv2.hconcat([panel(old_view[y0:y1,x0:x1],name+' OLD',280),panel(new_view[y0:y1,x0:x1],name+' NEW',280)]))
    save(directory/'11_focused_face_regions.png',cv2.vconcat(focus))
    save(directory/'12_old_vs_new_comparison.png',cv2.hconcat([panel(crop,'ORIGINAL'),panel(old_view,'OLD MASK'),panel(new_view,'NEW MASK')]))
    dense=crop.copy(); sparse=crop.copy()
    for p in points:
        if 0<=p[0]<512 and 0<=p[1]<512: cv2.circle(dense,tuple(p.astype(int)),1,(255,180,20),-1)
    for group, indices in GROUPS.items():
        cv2.polylines(dense,[points[indices].astype(np.int32)],group=='jaw',(0,220,255) if group=='lips' else (50,220,80),2)
    for p in face[4:14].reshape(5,2):
        q=matrix[:,:2]@p+matrix[:,2]; cv2.circle(sparse,tuple(q.astype(int)),7,(0,200,255),-1)
    save(directory/'13_dense_landmark_comparison.png',cv2.hconcat([panel(dense,'FACEMESH 478'),panel(sparse,'YUNET 5')]))
    (directory/'14_metrics.json').write_text(json.dumps(metrics,indent=2)+'\n',encoding='utf-8')
    return cv2.hconcat([panel(crop,'ORIGINAL',260),panel(old_view,'OLD MASK',260),panel(new_view,'NEW MASK',260),panel(cv2.hconcat([old_view[max(0,int(mc[1])-50):min(512,int(mc[1])+50),max(0,int(mc[0])-65):min(512,int(mc[0])+65)],new_view[max(0,int(mc[1])-50):min(512,int(mc[1])+50),max(0,int(mc[0])-65):min(512,int(mc[0])+65)]]),'PHILTRUM OLD / NEW',260)])


def write_csv(path, rows):
    if not rows: return
    fields=list(rows[0])
    with path.open('w',newline='',encoding='utf-8') as f:
        writer=csv.DictWriter(f,fieldnames=fields); writer.writeheader(); writer.writerows(rows)


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    files=sorted(p for p in ARCHIVE.rglob('*') if p.is_file())
    if len(files)!=125: raise ValueError(f'Expected 125 images, found {len(files)}')
    for path in (ROOT/'app-resources/models/face_detection_yunet_2023mar.onnx',ROOT/'app-resources/models/bisenet_face_parsing.onnx',ROOT/'build/diagnostic-models/face_landmarks_478.onnx'):
        if not path.is_file(): raise FileNotFoundError(path)
    detector=cv2.FaceDetectorYN.create(str(ROOT/'app-resources/models/face_detection_yunet_2023mar.onnx'),'',(320,320),.85,.3)
    opts=ort.SessionOptions(); opts.intra_op_num_threads=2
    parsing=ort.InferenceSession(str(ROOT/'app-resources/models/bisenet_face_parsing.onnx'),opts,providers=['CPUExecutionProvider'])
    mesh=ort.InferenceSession(str(ROOT/'build/diagnostic-models/face_landmarks_478.onnx'),opts,providers=['CPUExecutionProvider'])
    records=[]; skipped=[]
    def detect(image):
        detector.setInputSize((image.shape[1],image.shape[0])); return detector.detect(image)[1]
    for index,path in enumerate(files,1):
        image=cv2.imdecode(np.fromfile(str(path),dtype=np.uint8),cv2.IMREAD_COLOR)
        if image is None:
            skipped.append({'anonymous_id':f'IMG_{index:03d}','reason':'decode failed'}); continue
        t=clock(); faces=detect(image); detection_ms=ms(t)
        if faces is None:
            skipped.append({'anonymous_id':f'IMG_{index:03d}','reason':'no face detected'}); print(f'{index}/125 no face',flush=True); continue
        faces=sorted(faces,key=lambda f:float(f[2]*f[3]),reverse=True)
        for face_id,face in enumerate(faces,1):
            try:
                metrics,_=process(image,face,parsing,mesh)
                records.append({'anonymous_id':f'IMG_{index:03d}','face_id':face_id,'face_count':len(faces),'face_area':round(float(face[2]*face[3]),2),'face_score':round(float(face[14]),4),'tDetectMs':detection_ms,**metrics})
            except Exception as exc:
                skipped.append({'anonymous_id':f'IMG_{index:03d}','face_id':face_id,'reason':str(exc)})
        print(f'{index}/125 {len(faces)} faces, evaluated {len(records)}',flush=True)
        del image
    rankings=sorted(records,key=lambda r:(r['philtrum_deficit'],r['mouth_cutout_severity']),reverse=True)
    # Diversify across photos; prioritize failures and add groups and small faces explicitly.
    selected=[]; seen=set()
    def add(row):
        if row['anonymous_id'] not in seen and len(selected)<25:
            selected.append(row);seen.add(row['anonymous_id'])
    for row in rankings[:10]: add(row)
    for row in sorted(records,key=lambda r:r['face_count'],reverse=True):
        if len(selected)>=13: break
        add(row)
    for row in sorted(records,key=lambda r:r['face_area']):
        if len(selected)>=16: break
        add(row)
    for row in rankings: add(row)
    rows=[]
    for idx,row in enumerate(selected,1):
        image_index=int(row['anonymous_id'].split('_')[1])-1
        image=cv2.imdecode(np.fromfile(str(files[image_index]),dtype=np.uint8),cv2.IMREAD_COLOR)
        faces=sorted(detect(image),key=lambda f:float(f[2]*f[3]),reverse=True)
        metrics,data=process(image,faces[row['face_id']-1],parsing,mesh)
        row_dir=OUT/'cases'/f"{row['anonymous_id']}__face_{row['face_id']}"
        panel_image=artifacts(row_dir,image,faces[row['face_id']-1],row,data)
        if row in rankings[:10]: rows.append((rankings.index(row),panel_image))
        print(f'artifact {idx}/25 {row_dir.name}',flush=True)
        del image,data,panel_image
        gc.collect()
    if rows:
        rows.sort(key=lambda item:item[0]); save(OUT/'worst_cases_contact_sheet.png',cv2.vconcat([panel for _,panel in rows]))
    base=[{k:v for k,v in r.items() if k!='regions' and not k.startswith('t') and k not in ('philtrum_alpha_new','philtrum_deficit','holes_count','repaired_pixels_count','mouth_cutout_severity') }|{'philtrum_alpha':r['philtrum_alpha_old'],'repaired_pixels_count':r['old_repaired_pixels_count'],**{f'{name}_coverage':values['old'] for name,values in r['regions'].items()}} for r in records]
    candidate=[{k:v for k,v in r.items() if k!='regions' and not k.startswith('t') and k not in ('philtrum_alpha_old','old_repaired_pixels_count')}|{'philtrum_alpha':r['philtrum_alpha_new'],**{f'{name}_coverage':values['new'] for name,values in r['regions'].items()}} for r in records]
    write_csv(OUT/'baseline_results.csv',base);write_csv(OUT/'candidate_results.csv',candidate)
    write_csv(OUT/'latency_breakdown.csv',[{k:v for k,v in r.items() if k.startswith('t') or k in ('anonymous_id','face_id')} for r in records])
    (OUT/'evaluation_summary.json').write_text(json.dumps({'evaluated_faces':len(records),'evaluated_photos':len({r['anonymous_id'] for r in records}),'skipped':skipped,'top_reported_failure_candidate':{k:v for k,v in rankings[0].items() if k!='regions'} if rankings else None,'ranked_failures':[{k:r[k] for k in ('anonymous_id','face_id','philtrum_deficit','mouth_cutout_severity')} for r in rankings[:25]],'selected_cases':[f"{r['anonymous_id']}__face_{r['face_id']}" for r in selected]},indent=2)+'\n',encoding='utf-8')
    print(f'COMPLETE: {len(records)} faces, {len(selected)} artifact cases; {OUT}',flush=True)

if __name__=='__main__': main()
