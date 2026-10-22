#!/usr/bin/env python3
"""Record modified static logo/splash image assets relative to the pinned upstream baseline."""
import subprocess, hashlib, json
from pathlib import Path
R = Path(__file__).resolve().parents[1]
base = (R/'.loungetv/upstream-version').read_text().strip()
changed = subprocess.check_output(['git','diff','--name-only',f'{base}...HEAD','--','app/src/main'],cwd=R,text=True).splitlines()
exts = {'.png','.webp','.jpg','.jpeg','.svg','.gif','.avif'}
assets = {}
for path in changed:
    p = R / path
    if p.is_file() and p.suffix.lower() in exts:
        assets[path] = hashlib.sha256(p.read_bytes()).hexdigest()
(R/'branding').mkdir(exist_ok=True)
(R/'branding/asset-sha256.json').write_text(json.dumps(assets,indent=2,sort_keys=True)+'\n')
print(f'Protected {len(assets)} changed branded image assets.')
