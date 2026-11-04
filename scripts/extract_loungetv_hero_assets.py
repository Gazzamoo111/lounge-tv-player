#!/usr/bin/env python3
"""Materialize the reviewed Lounge TV full-frame hero artwork pack."""
from __future__ import annotations

import base64
import hashlib
import io
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PARTS = ROOT / "assets" / "hero-fullframe"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
EXPECTED_ZIP_SHA256 = "168917404ec5a121a0a1837dfd5f8debb79f63a8bd12482212b452549465279c"
EXPECTED_SIZE = (1672, 941)
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
            with Image.open(io.BytesIO(payload)) as image:
                if image.size != EXPECTED_SIZE:
                    raise SystemExit(f"{name} has wrong size {image.size}; expected {EXPECTED_SIZE}")
            (OUT / name).write_bytes(payload)

    print("Lounge TV full-frame hero assets ready:", ", ".join(sorted(EXPECTED)))


if __name__ == "__main__":
    main()
