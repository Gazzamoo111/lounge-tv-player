#!/usr/bin/env python3
"""Build gate: reject incorrect package/update channel or changed protected brand images."""
import hashlib, json, re
from pathlib import Path
R = Path(__file__).resolve().parents[1]
app = (R/'app/build.gradle.kts').read_text()
code = (R/'app/src/main/java/tv/own/owntv/OwnTVApp.kt').read_text()
assert re.search(r'applicationId\s*=\s*"com\.loungetv\.player"', app), 'Production package changed'
assert re.search(r'CoreBuildInfo\.releaseRepo\s*=\s*"Gazzamoo111/lounge-tv-player"', code), 'Update repo wrong or missing'
strings = R/'app/src/main/res/values/strings.xml'
if strings.exists():
    assert re.search(r'<string\s+name="app_name"[^>]*>Lounge TV</string>', strings.read_text()), 'Wrong app name'
for filename, digest in json.loads((R/'branding/asset-sha256.json').read_text()).items():
    file = R / filename
    assert file.is_file(), f'Missing branded asset: {filename}'
    assert hashlib.sha256(file.read_bytes()).hexdigest() == digest, f'Changed brand asset: {filename}'
allowed = {'ci.yml', 'upstream-sync.yml', 'publish.yml'}
actual = {x.name for x in (R/'.github/workflows').glob('*.yml')}
assert actual <= allowed, f'Unexpected upstream workflows: {actual - allowed}'
assert (R/'LICENSE').exists(), 'GPLv3 license file missing'
print('PASS: branding, app identity, protected assets, release channel and workflows')
