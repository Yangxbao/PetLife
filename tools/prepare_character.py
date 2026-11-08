# -*- coding: utf-8 -*-
"""
星伴角色资产管线 v2：GrabCut 抠图（cv2）+ 面部锚点检测 + 预览
"""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import json, os

SRC = r'C:/Users/MR/.zcode/cli/image-cache/sess_36cb36ab-4530-4074-8470-53ff01ceb142/image-28ec60502adbe586bb3bf2f23d7d5aac.png'
OUT_DIR = r'D:/.zcode/workspace/draft-12d814e8-8316-4d22-bc79-eef9cab619e0/PetLife/app/src/main/assets/character'
PREVIEW = r'D:/.zcode/workspace/draft-12d814e8-8316-4d22-bc79-eef9cab619e0/docs/preview_face.jpg'
os.makedirs(OUT_DIR, exist_ok=True)

import cv2
im = Image.open(SRC).convert('RGB')
W, H = im.size
print(f'源图 {W}x{H}')

# ---------- 1) GrabCut 抠图 ----------
img = cv2.cvtColor(np.asarray(im), cv2.COLOR_RGB2BGR)
mask = np.zeros((H, W), np.uint8)
bgd = np.zeros((1, 65), np.float64)
fgd = np.zeros((1, 65), np.float64)
# 角色主体大致位于画面中央偏下（留出上边家具/他人）
rect = (int(W*0.06), int(H*0.10), int(W*0.92), int(H*0.88))
cv2.grabCut(img, mask, rect, bgd, fgd, 8, cv2.GC_INIT_WITH_RECT)
fg = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), True, False)
print(f'GrabCut 前景占比 {fg.mean()*100:.1f}%')

# 形态学闭运算 + 最大连通块（去孤立误判）
fg_u8 = (fg * 255).astype(np.uint8)
kernel = np.ones((5, 5), np.uint8)
fg_u8 = cv2.morphologyEx(fg_u8, cv2.MORPH_CLOSE, kernel, iterations=2)
n, lbl, stats, _ = cv2.connectedComponentsWithStats(fg_u8)
if n > 2:
    sizes = stats[1:, cv2.CC_STAT_AREA]
    keep = 1 + int(np.argmax(sizes))
    fg = lbl == keep
print(f'最大连通块保留')

# ---------- 2) 裁剪 + 羽化 + RGBA ----------
ys, xs = np.where(fg)
y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
pad = 8
y0 = max(0, y0-pad); x0 = max(0, x0-pad); y1 = min(H-1, y1+pad); x1 = min(W-1, x1+pad)
crop = im.crop((x0, y0, x1+1, y1+1))
crop_fg = fg[y0:y1+1, x0:x1+1]
alpha = (crop_fg * 255).astype(np.uint8)
alpha = cv2.GaussianBlur(alpha, (3, 3), 1.0)
rgba = crop.convert('RGBA')
rgba.putalpha(Image.fromarray(alpha, 'L'))
ratio = 1024 / rgba.height
rgba = rgba.resize((max(1, int(rgba.width * ratio)), 1024), Image.LANCZOS)
rgba.save(os.path.join(OUT_DIR, 'maid_base.png'), optimize=True)
cw, ch = rgba.size
print(f'立绘输出 {cw}x{ch}')

# ---------- 3) 面部锚点（矢量检测：蓝色虹膜簇 ×2 + 红色口部簇） ----------
px = np.asarray(crop.convert('RGB')).astype(np.int16)
ch2, cw2 = px.shape[0], px.shape[1]
r = px[:,:,0]; g = px[:,:,1]; b = px[:,:,2]

# 面部搜索区：立绘上部中央
ry0, ry1 = int(ch2*0.10), int(ch2*0.46)
rx0, rx1 = int(cw2*0.22), int(cw2*0.78)
blue = (b > 130) & (b > r + 25) & (g > 80) & (g < b)
blue[:ry0] = False; blue[ry1:] = False; blue[:, :rx0] = False; blue[:, rx1:] = False

n, lbl, stats, centroids = cv2.connectedComponentsWithStats(blue.astype(np.uint8))
blobs = [(stats[i, cv2.CC_STAT_AREA], centroids[i][0]/cw2, centroids[i][1]/ch2)
         for i in range(1, n) if stats[i, cv2.CC_STAT_AREA] > 200]
blobs.sort(reverse=True)
assert len(blobs) >= 2, f'眼睛簇不足: {len(blobs)}'
(_, lx, ly), (_, rx_, ry_) = blobs[0], blobs[1]
if lx > rx_: lx, rx_ = rx_, lx
eyeL = (lx, ly); eyeR = (rx_, ry_)
face_cx = (lx + rx_) / 2
face_cy = (ly + ry_) / 2 + 0.01
print(f'眼心 L=({eyeL[0]:.3f},{eyeL[1]:.3f}) R=({eyeR[0]:.3f},{eyeR[1]:.3f})')

# 嘴：眼部下方红色/深红簇
my0 = int(ch2 * (max(ly, ry_) + 0.06)); my1 = int(ch2 * (max(ly, ry_) + 0.14))
red = (r > 140) & (r > b + 30) & (g < r - 10)
red[:my0] = False; red[my1:] = False
red[:, :int(cw2*0.30)] = False; red[:, int(cw2*0.70):] = False
ys2, xs2 = np.where(red)
mouth = (xs2.mean()/cw2, ys2.mean()/ch2) if len(xs2) > 30 else (face_cx, max(ly, ry_) + 0.085)
print(f'嘴心 = ({mouth[0]:.3f},{mouth[1]:.3f})')

# ---------- 4) face_anchor.json ----------
eye_dx = abs(eyeR[0] - eyeL[0]) / 2
anchor = {
    'sprite': 'maid_base.png',
    'sprite_w': cw, 'sprite_h': ch,
    'eye_l': {'x': round(eyeL[0], 4), 'y': round(eyeL[1], 4)},
    'eye_r': {'x': round(eyeR[0], 4), 'y': round(eyeR[1], 4)},
    'mouth': {'x': round(mouth[0], 4), 'y': round(mouth[1], 4)},
    'face_cx': round(face_cx, 4),
    'face_cy': round(face_cy, 4),
    'eye_dx': round(eye_dx, 4),
    'eye_w': round(eye_dx * 0.95, 4),
    'eye_h': round(0.052, 4),
    'patch': {
        'cx': round(face_cx, 4),
        'cy': round(face_cy - 0.006, 4),
        'rx': round(eye_dx * 2.15, 4),
        'ry': round(0.068, 4),
    },
    'blush_dx': round(eye_dx * 1.45, 4),
    'stage_scale': {'BABY': 0.90, 'GROWING': 0.96, 'ADULT': 1.0, 'EVOLVED': 1.05},
}
json.dump(anchor, open(os.path.join(OUT_DIR, 'face_anchor.json'), 'w'), ensure_ascii=False, indent=1)
print('face_anchor.json 完成')

# ---------- 5) 预览：贴表情补丁 + 示例五官 ----------
prev = rgba.copy()
d = ImageDraw.Draw(prev)
def X(fx): return fx * cw
def Y(fy): return fy * ch
skin = (255, 237, 224)
d.ellipse([X(anchor['patch']['cx'] - anchor['patch']['rx']), Y(anchor['patch']['cy'] - anchor['patch']['ry']),
           X(anchor['patch']['cx'] + anchor['patch']['rx']), Y(anchor['patch']['cy'] + anchor['patch']['ry'])], fill=skin)
for ex in (anchor['eye_l']['x'], anchor['eye_r']['x']):
    ex_, ey_ = X(ex), Y(anchor['eye_l']['y'])
    d.arc([ex_ - anchor['eye_w']*cw, ey_ - 16, ex_ + anchor['eye_w']*cw, ey_ + 20], 200, 340, fill=(61,46,32), width=8)
mx, my = X(anchor['mouth']['x']), Y(anchor['mouth']['y'])
d.arc([mx - 36, my - 14, mx + 36, my + 24], 10, 170, fill=(122,74,58), width=8)
for sx in (X(anchor['face_cx']) - anchor['blush_dx']*cw, X(anchor['face_cx']) + anchor['blush_dx']*cw):
    d.ellipse([sx - 30, Y(anchor['patch']['cy']) + 10, sx + 30, Y(anchor['patch']['cy']) + 40], fill=(255,179,173,180))
prev.thumbnail((460, 660))
prev.convert('RGB').save(PREVIEW, quality=88)
print('预览输出:', PREVIEW)
