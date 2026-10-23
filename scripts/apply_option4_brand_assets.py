#!/usr/bin/env python3
"""Resize/crop the approved 23 Oct 2026 Option 4 art; never redraw or recolour it."""
from pathlib import Path
import hashlib, json
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
LOCK = ROOT / 'branding/locked'
ICON = LOCK / 'option4-app-icon-original.png'
HORIZONTAL = LOCK / 'option4-horizontal-original.png'
EXPECTED_ICON = '6ea873abbcd21e364c90e96ac19f85fcd80b736e1a49176b151374a91f2c1087'
EXPECTED_HORIZONTAL = '29ecbdef5d2e76a8fa68ca8deac7f5375f13f44375102ff00018dd617211b404'
for original, expected in ((ICON, EXPECTED_ICON), (HORIZONTAL, EXPECTED_HORIZONTAL)):
    if hashlib.sha256(original.read_bytes()).hexdigest() != expected:
        raise SystemExit(f'Approved original artwork changed: {original}')

resample = Image.Resampling.LANCZOS
original_logo = Image.open(HORIZONTAL).convert('RGB')
original_icon = Image.open(ICON).convert('RGB')

def clear_background(im, crop, threshold=63, upper=122):
    """Extract visible pixels only from the original artwork; preserve original RGB."""
    part = im.crop(crop).convert('RGBA')
    source = part.convert('RGB')
    # Colour isn't modified: only opacity of the original dark navy background.
    r, g, b = source.split()
    intensity = ImageChops.lighter(ImageChops.lighter(r, g), b)
    alpha = intensity.point([int(max(0,min(255,(k-threshold)*255.0/(upper-threshold)))) for k in range(256)])
    part.putalpha(alpha)
    bbox = part.getchannel('A').point(lambda q: 255 if q>10 else 0).getbbox()
    if not bbox:
        raise SystemExit('Source artwork extraction is empty')
    return part.crop(bbox)

logo = clear_background(original_logo, (65, 275, 1600, 675))
couch = clear_background(original_logo, (85, 285, 613, 665))
wordmark = clear_background(original_logo, (609, 360, 1575, 590))
# White rounded-square icon within the navy photograph: preserve the approved
# white tile and blue couch, only eliminate the unrelated dark outer border.
app_icon = clear_background(original_icon, (110, 100, 1150, 1135), threshold=100, upper=150)

def fit(im, width, height, fill):
    canvas = Image.new('RGBA',(width,height),(0,0,0,0))
    factor = min(width*fill/im.width,height*fill/im.height)
    scaled = im.resize((max(1,round(im.width*factor)),max(1,round(im.height*factor))),resample)
    canvas.alpha_composite(scaled,((width-scaled.width)//2,(height-scaled.height)//2))
    return canvas

outputs = {}
def write(im, path):
    path.parent.mkdir(parents=True,exist_ok=True)
    im.save(path,'PNG',optimize=True)
    outputs[str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()

write(logo, RES/'drawable-nodpi/loungetv_locked_horizontal.png')
write(wordmark, RES/'drawable-nodpi/loungetv_locked_wordmark.png')
write(couch, RES/'drawable-nodpi/loungetv_locked_couch.png')
write(app_icon, RES/'drawable-nodpi/loungetv_locked_app_icon.png')
write(fit(logo,768,768,.57),RES/'drawable-nodpi/loungetv_system_launch_art.png')
styles = ['eggshell','petrol','sunflower','cobalt','tomato','board','olive','olive_cream','pixel']
for name in styles:
    write(fit(couch,512,512,.86),RES/f'drawable-nodpi/loungetv_icon_variant_{name}.png')
    for density,factor in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
        n = round(108*factor)
        write(fit(app_icon,n,n,.81),RES/f'mipmap-{density}/owntv_icon_{name}_foreground.png')
        write(fit(logo,round(320*factor),round(180*factor),.88),RES/f'drawable-{density}/owntv_banner_{name}.png')
# Keep GitHub's existing protected source hashes and extend the record with
# the locked original masters plus deterministic deployment derivatives.
hash_file = ROOT/'branding/asset-sha256.json'
hashes = json.loads(hash_file.read_text())
hashes.update(outputs)
for src in (ICON,HORIZONTAL):
    hashes[str(src.relative_to(ROOT))] = hashlib.sha256(src.read_bytes()).hexdigest()
hash_file.write_text(json.dumps(hashes,indent=2,sort_keys=True)+'\n')
print(f'Option 4: {len(outputs)} production artwork derivatives rendered from SHA-pinned approved originals')
