#!/usr/bin/env python3
"""Apply the minimal fixed Lounge TV production identity; fail closed on upstream refactors."""
from pathlib import Path
import re, sys
ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / 'app/build.gradle.kts'
APPLICATION = ROOT / 'app/src/main/java/tv/own/owntv/OwnTVApp.kt'
STRINGS = ROOT / 'app/src/main/res/values/strings.xml'


def replace_exact_pattern(path: Path, pat: str, repl: str, label: str):
    text = path.read_text()
    changed, count = re.subn(pat, repl, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise SystemExit(f'FAIL: {label} not found exactly once in {path}; inspect the upstream change.')
    path.write_text(changed)
    print(f'OK: {label}')


replace_exact_pattern(GRADLE, r'(\bapplicationId\s*=\s*)"[^"]+"',
                      r'\g<1>"com.loungetv.player"', 'production application ID')
replace_exact_pattern(APPLICATION,
                      r'(?m)^(\s*(?:tv\.own\.owntv\.core\.)?CoreBuildInfo\.releaseRepo\s*=\s*)"[^"]+"',
                      r'\g<1>"Gazzamoo111/lounge-tv-player"', 'independent update repository')
if STRINGS.is_file():
    src = STRINGS.read_text()
    if re.search(r'<string\s+name="app_name"', src):
        target, count = re.subn(r'(<string\s+name="app_name"[^>]*>)[^<]*(</string>)',
                                r'\g<1>Lounge TV\2', src, count=1)
        if count != 1:
            raise SystemExit('FAIL: app_name exists but cannot be replaced safely')
    else:
        if '</resources>' not in src:
            raise SystemExit('FAIL: Android strings resource layout changed')
        target = src.replace('</resources>', '    <string name="app_name">Lounge TV</string>\n</resources>', 1)
    STRINGS.write_text(target)
    print('OK: app_name = Lounge TV')
else:
    raise SystemExit('FAIL: app/src/main/res/values/strings.xml missing')
print('Production identity applied; branded artwork remains from your source overlay.')
