#!/usr/bin/env python3
"""Generate couch-only transparent Lounge TV launcher/TV-banner variants.

Source is the approved transparent original Lounge TV full lockup; the wordmark
is excluded from the couch crop. The source PNG remains untouched. This runs
before both QA and signed releases so their launcher artwork is identical.
"""
from pathlib import Path
from PIL import Image, ImageEnhance
import hashlib, json

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
MASTER = RES / "drawable-nodpi/loungetv_original_full_logo.png"
HASHES = ROOT / "branding/asset-sha256.json"
STYLES = {
    "eggshell": "original",
    "petrol": "aqua",
    "sunflower": "ice",
    "cobalt": "blue",
    "tomato": "sport",
    "board": "chrome",
    "olive": "midnight",
    "olive_cream": "silver",
    "pixel": "violet",
}
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

def tinted(source: Image.Image, style: str) -> Image.Image:
    if style == "original":
        return source.copy()
    out = source.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            if style == "aqua":
                rr, gg, bb = r * .69, g * 1.14, b * 1.02
            elif style == "ice":
                lum = .23*r + .66*g + .11*b
                rr, gg, bb = lum*.70, lum*.96, min(255, lum*1.27)
            elif style == "blue":
                rr, gg, bb = r*.75, g*.83, b*1.24
            elif style == "sport":
                if x < source.width*.49:
                    rr, gg, bb = max(r, b*.83)*1.25, g*.54, b*.45
                else:
                    rr, gg, bb = r*.47, g*.75, b*1.31
            elif style == "chrome":
                lum = .24*r + .63*g + .13*b
                rr, gg, bb = lum*.94, lum*1.04, lum*1.21
            elif style == "midnight":
                rr, gg, bb = r*.43, g*.53, b*.78
            elif style == "silver":
                lum = .25*r + .63*g + .12*b
                rr, gg, bb = lum*1.10, lum*1.13, lum*1.16
            elif style == "violet":
                rr, gg, bb = b*1.04, g*.53, max(r,b)*1.12
            else:
                raise ValueError(style)
            pixels[x, y] = (min(255,int(rr)),min(255,int(gg)),min(255,int(bb)),a)
    return out

def fit(source: Image.Image, w: int, h: int, fill: float = .84) -> Image.Image:
    frame = Image.new("RGBA", (w, h), (0,0,0,0))
    sw, sh = source.size
    scale = min(w*fill/sw, h*fill/sh)
    im = source.resize((max(1,round(sw*scale)),max(1,round(sh*scale))),Image.Resampling.LANCZOS)
    frame.alpha_composite(im, ((w-im.width)//2,(h-im.height)//2))
    return frame

def write_image(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)

def main():
    saved_hashes = json.loads(HASHES.read_text())
    master_hash = hashlib.sha256(MASTER.read_bytes()).hexdigest()
    if saved_hashes.get(str(MASTER.relative_to(ROOT))) != master_hash:
        raise SystemExit("Approved original Lounge TV full logo changed: refusing to regenerate")

    full = Image.open(MASTER).convert("RGBA")
    w, h = full.size
    # The approved logo is couch on top with LOUNGE TV beneath it. Exclude the wordmark.
    crop = full.crop((int(w*.13), int(h*.09), int(w*.89), int(h*.685)))
    bbox = crop.getchannel("A").getbbox()
    if not bbox:
        raise SystemExit("Couch artwork appears empty")
    crop = crop.crop(bbox)
    # Fail closed if the master does not have a genuine transparent backdrop.
    corner_alpha = [crop.getpixel((i,j))[3] for i,j in
                    ((0,0),(crop.width-1,0),(0,crop.height-1),(crop.width-1,crop.height-1))]
    if any(a > 60 for a in corner_alpha):
        raise SystemExit("Couch crop isn't transparent at corners. Inspect source instead of adding a box.")

    generated = []
    for name, style in STYLES.items():
        couch = tinted(crop, style)
        # A dedicated preview image for the icon-choice UI, same mark as the launcher.
        path = RES / "drawable-nodpi" / f"loungetv_icon_variant_{name}.png"
        write_image(fit(couch, 512, 512, .89), path)
        generated.append(path)

        for density, factor in DENSITIES.items():
            n = round(108 * factor)
            path = RES / f"mipmap-{density}" / f"owntv_icon_{name}_foreground.png"
            write_image(fit(couch, n, n, .89), path)
            generated.append(path)
            bw, bh = round(320*factor), round(180*factor)
            path = RES / f"drawable-{density}" / f"owntv_banner_{name}.png"
            write_image(fit(couch, bw, bh, .86), path)
            generated.append(path)

    for path in generated:
        saved_hashes[str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
    HASHES.write_text(json.dumps(saved_hashes, indent=2, sort_keys=True)+"\n")
    print(f"Lounge TV: regenerated {len(generated)} transparent couch icon/banner files; source hash verified")

if __name__ == "__main__":
    main()
