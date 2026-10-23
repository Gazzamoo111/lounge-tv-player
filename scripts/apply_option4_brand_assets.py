#!/usr/bin/env python3
"""Render approved Option 4 artwork with distinct selectable icon colours."""
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
# Android's native splash masks the image to a circular safe area. .625 is
# the largest reliable fit for this wide logo (about 10% larger, no clipped L).
write(fit(logo,768,768,.625),RES/'drawable-nodpi/loungetv_system_launch_art.png')
# Keep Eggshell the exact approved Option 4 blue. The remaining choices are
# intentionally distinct, recolouring the approved couch rather than replacing it.
ICON_COLOURS = {
    'eggshell': None,
    'petrol': (12, 215, 192),       # teal
    'sunflower': (255, 185, 35),    # gold
    'cobalt': (62, 101, 255),       # royal blue
    'tomato': (255, 80, 94),        # coral red
    'board': (205, 220, 230),       # platinum
    'olive': (125, 192, 66),        # green
    'olive_cream': (225, 154, 212), # rose
    'pixel': (185, 88, 255),        # violet
}
def coloured(im, colour):
    if colour is None:
        return im.copy()
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            # Only recolour blue couch pixels, not the approved white launcher tile.
            if b < r + 12 or b < g + 7:
                continue
            strength = max(0.0, min(1.0, (b - min(r, g)) / 85.0))
            brightness = max(r, g, b) / 255.0
            scale = .30 + .70 * brightness
            desired = tuple(min(255, round(v * scale)) for v in colour)
            px[x, y] = (
                round(r * (1-strength) + desired[0] * strength),
                round(g * (1-strength) + desired[1] * strength),
                round(b * (1-strength) + desired[2] * strength),
                a,
            )
    return out

for name, colour in ICON_COLOURS.items():
    variant_couch = coloured(couch, colour)
    variant_app_icon = coloured(app_icon, colour)
    write(fit(variant_couch,512,512,.86),RES/f'drawable-nodpi/loungetv_icon_variant_{name}.png')
    for density,factor in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
        n = round(108*factor)
        write(fit(variant_app_icon,n,n,.81),RES/f'mipmap-{density}/owntv_icon_{name}_foreground.png')
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
