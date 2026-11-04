#!/usr/bin/env python3
"""Materialize reviewed Lounge TV hero masters into native 1920x520 full-bleed hero assets."""
from __future__ import annotations

import base64
import hashlib
import io
import zipfile
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
PARTS = ROOT / "assets" / "hero-fullframe"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
EXPECTED_ZIP_SHA256 = "168917404ec5a121a0a1837dfd5f8debb79f63a8bd12482212b452549465279c"
MASTER_SIZE = (1672, 941)
TARGET_SIZE = (1920, 520)
BLEND = 96
EXPECTED = {
    "lounge_hero_league.webp",
    "lounge_hero_rugby.webp",
    "lounge_hero_boxing.webp",
    "lounge_hero_combat.webp",
    "lounge_hero_wwe.webp",
    "lounge_hero_cricket.webp",
    "lounge_hero_nba.webp",
    "lounge_hero_soccer.webp",
    "lounge_hero_tennis.webp",
    "lounge_hero_golf.webp",
    "lounge_hero_nfl.webp",
    "lounge_hero_f1.webp",
    "lounge_hero_baseball.webp",
    "lounge_hero_hockey.webp",
}


def native_hero(master: Image.Image) -> Image.Image:
    target_w, target_h = TARGET_SIZE
    sharp_w = round(master.width * target_h / master.height)
    sharp = master.resize((sharp_w, target_h), Image.Resampling.LANCZOS).convert("RGB")
    sharp_x = target_w - sharp_w

    # Extend only the master's atmospheric left side so the hero can be ultra-wide
    # without duplicating or enlarging the focal object on the right.
    left_crop_w = max(1, round(master.width * 0.42))
    atmosphere = master.crop((0, 0, left_crop_w, master.height)).convert("RGB")
    atmosphere = atmosphere.resize((sharp_x + BLEND, target_h), Image.Resampling.LANCZOS)
    atmosphere = atmosphere.filter(ImageFilter.GaussianBlur(radius=22))
    atmosphere = ImageEnhance.Brightness(atmosphere).enhance(0.62)
    atmosphere = ImageEnhance.Contrast(atmosphere).enhance(0.92)

    canvas = Image.new("RGB", TARGET_SIZE, (4, 10, 18))
    canvas.paste(atmosphere, (0, 0))

    mask = Image.new("L", sharp.size, 255)
    for x in range(min(BLEND, sharp.width)):
        alpha = round(255 * x / max(1, BLEND - 1))
        for y in range(sharp.height):
            mask.putpixel((x, y), alpha)
    canvas.paste(sharp, (sharp_x, 0), mask)
    return canvas


def main() -> None:
    parts = sorted(PARTS.glob("part*.b64"))
    if not parts:
        raise SystemExit("missing Lounge TV full-frame hero parts")
    encoded = "".join(part.read_text(encoding="ascii") for part in parts)
    raw = base64.b64decode(encoded, validate=True)
    digest = hashlib.sha256(raw).hexdigest()
    if digest != EXPECTED_ZIP_SHA256:
        raise SystemExit(f"hero pack digest mismatch: {digest}")

    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(raw)) as bundle:
        names = set(bundle.namelist())
        if names != EXPECTED:
            raise SystemExit(f"hero pack names mismatch: {sorted(names)}")
        for name in sorted(EXPECTED):
            payload = bundle.read(name)
            with Image.open(io.BytesIO(payload)) as source:
                master = source.convert("RGB")
                if master.size != MASTER_SIZE:
                    raise SystemExit(f"{name} has wrong master size {master.size}; expected {MASTER_SIZE}")
                hero = native_hero(master)
                if hero.size != TARGET_SIZE:
                    raise SystemExit(f"{name} has wrong hero size {hero.size}; expected {TARGET_SIZE}")
                hero.save(OUT / name, "WEBP", quality=82, method=6)

    print("Lounge TV native hero assets ready:", ", ".join(sorted(EXPECTED)))


if __name__ == "__main__":
    main()
