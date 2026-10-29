#!/usr/bin/env python3
"""Build gate: reject incorrect package/update channel or changed protected brand images."""
import hashlib, json, re
from pathlib import Path
from PIL import Image
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
# A second Compose intro previously appeared several seconds after the small
# system splash and visibly resized the logo. Enforce one consistent launch.
main = (R/'app/src/main/java/tv/own/owntv/MainActivity.kt').read_text()
assert 'LoungeOptionBIntroScreen' not in main, 'Duplicate Compose splash transition is back'
assert 'loungeOptionBIntroVisible' not in main, 'Delayed splash transition is back'
logo = R/'app/src/main/res/drawable-nodpi/loungetv_system_launch_art.png'
assert logo.is_file(), 'Missing approved native splash logo'
with Image.open(logo) as splash:
    splash = splash.convert('RGBA')
    width, height = splash.size
    assert (width, height) == (768, 768), f'Native splash must be 768x768, got {width}x{height}'
    alpha = splash.getchannel('A')
    bbox = alpha.getbbox()
    assert bbox, 'Native splash logo is transparent'
    assert all(alpha.getpixel(corner) == 0 for corner in ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1))), 'Native splash needs transparent corners'
    # Theme.SplashScreen has no icon background. Android masks its 288dp square icon to a
    # centred 192dp circle. Keep every antialiased pixel inside 32% of the canvas radius,
    # leaving tolerance inside the platform's one-third radius instead of merely touching it.
    center_x = width / 2
    center_y = height / 2
    safe_radius_squared = (width * .32) ** 2
    outside_safe_circle = sum(
        1
        for y in range(height)
        for x in range(width)
        if alpha.getpixel((x, y)) > 0
        and ((x + .5 - center_x) ** 2 + (y + .5 - center_y) ** 2) > safe_radius_squared
    )
    assert outside_safe_circle == 0, f'Native splash has {outside_safe_circle} pixels outside its safe circle'
    visible_center_x = (bbox[0] + bbox[2]) / 2
    visible_center_y = (bbox[1] + bbox[3]) / 2
    assert abs(visible_center_x - center_x) <= width * .01, 'Native splash is not horizontally centred'
    assert abs(visible_center_y - center_y) <= height * .01, 'Native splash is not vertically centred'

# The icon picker offers different palettes. The locked Eggshell icon is unchanged,
# but the other choices must not all be the same copied PNG.
variants = [
    R/'app/src/main/res/drawable-nodpi'/f'loungetv_icon_variant_{name}.png'
    for name in ('eggshell', 'petrol', 'sunflower', 'cobalt', 'tomato', 'board',
                 'olive', 'olive_cream', 'pixel')
]
assert len({hashlib.sha256(path.read_bytes()).hexdigest() for path in variants}) >= 6, (
    'Lounge TV icon choices are not distinct colours'
)
for theme in ['values/themes.xml', 'values-v31/themes.xml']:
    theme_text = (R/'app/src/main/res'/theme).read_text()
    assert theme_text.count('@drawable/loungetv_system_launch_art') >= 9, f'Native splash missing on some icon styles: {theme}'
    assert '@drawable/loungetv_system_launch_logo' not in theme_text, 'Old undersized system splash is back'

allowed = {'ci.yml', 'upstream-sync.yml', 'publish.yml', 'playback-hotfix-qa.yml', 'playback-tests.yml', 'vod-navigation-qa.yml'}
actual = {x.name for x in (R/'.github/workflows').glob('*.yml')}
assert actual <= allowed, f'Unexpected upstream workflows: {actual - allowed}'
assert (R/'LICENSE').exists(), 'GPLv3 license file missing'
print('PASS: branding, app identity, protected assets, release channel and workflows')
