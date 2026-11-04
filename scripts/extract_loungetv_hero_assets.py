#!/usr/bin/env python3
"""Materialize reviewed Lounge TV QA hero artwork from the checked-in sprite."""
from __future__ import annotations
import hashlib
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SPRITE = ROOT / "assets" / "hero-sprite" / "sprite.webp"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
EXPECTED_SHA256 = "ddbc91cf98b9c0bc2f54ce1a255b71ae6273e5c2bd06b3f40468caf7c571a9ed"
CELLS = [
    ("lounge_hero_boxing.webp", 0, 0),
    ("lounge_hero_wwe.webp", 1, 0),
    ("lounge_hero_tennis.webp", 2, 0),
    ("lounge_hero_golf.webp", 0, 1),
    ("lounge_hero_baseball.webp", 1, 1),
    ("lounge_hero_hockey.webp", 2, 1),
]

def main() -> None:
    raw = SPRITE.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(f"hero sprite digest mismatch: {digest}")
    image = Image.open(SPRITE).convert("RGB")
    if image.size != (3072, 1152):
        raise SystemExit(f"unexpected hero sprite size: {image.size}")
    OUT.mkdir(parents=True, exist_ok=True)
    for name, col, row in CELLS:
        crop = image.crop((col * 1024, row * 576, (col + 1) * 1024, (row + 1) * 576))
        crop.save(OUT / name, "WEBP", quality=60, method=6)
    print("Lounge TV hero assets ready:", ", ".join(name for name, _, _ in CELLS))

if __name__ == "__main__":
    main()
