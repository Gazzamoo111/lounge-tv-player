#!/usr/bin/env python3
"""Stable monotonically sortable numeric Android versionCode for a four-part version."""
import re, sys
if len(sys.argv) != 2 or not re.fullmatch(r'\d+\.\d+\.\d+\.\d+', sys.argv[1]):
    raise SystemExit('Specify version like 5.2.1.0')
major, minor, patch, revision = map(int, sys.argv[1].split('.'))
if not (0 <= major <= 2000 and 0 <= minor < 100 and 0 <= patch < 100 and 0 <= revision < 100):
    raise SystemExit('Version segments exceed supported ranges')
code = major * 1_000_000 + minor * 10_000 + patch * 100 + revision
if not 0 < code < 2_100_000_000:
    raise SystemExit('Android versionCode out of range')
print(code)
