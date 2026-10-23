#!/usr/bin/env python3
"""Stable monotonically sortable numeric Android versionCode for a four-part version."""
import re
import sys


def version_code(version: str) -> int:
    if not re.fullmatch(r'\d+\.\d+\.\d+\.\d+', version):
        raise ValueError('Specify version like 5.2.1.0')
    major, minor, patch, revision = map(int, version.split('.'))
    if not (0 <= major <= 2000 and 0 <= minor < 100 and 0 <= patch < 100 and 0 <= revision < 100):
        raise ValueError('Version segments exceed supported ranges')
    code = major * 1_000_000 + minor * 10_000 + patch * 100 + revision
    if not 0 < code < 2_100_000_000:
        raise ValueError('Android versionCode out of range')
    return code


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        raise SystemExit('Specify version like 5.2.1.0')
    try:
        print(version_code(argv[1]))
    except ValueError as error:
        raise SystemExit(str(error)) from error
    return 0


if __name__ == '__main__':
    main(sys.argv)
