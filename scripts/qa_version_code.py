#!/usr/bin/env python3
"""Monotonic Android versionCode reserved for signed Lounge TV QA artifacts."""
import sys

QA_VERSION_BASE = 1_000_000_000
MAX_ANDROID_VERSION_CODE = 2_100_000_000


def qa_version_code(run_number: int, run_attempt: int) -> int:
    if not 0 < run_number < 11_000_000:
        raise ValueError('QA run number must be between 1 and 10,999,999')
    if not 0 < run_attempt < 100:
        raise ValueError('QA run attempt must be between 1 and 99')
    code = QA_VERSION_BASE + run_number * 100 + run_attempt
    if code >= MAX_ANDROID_VERSION_CODE:
        raise ValueError('QA versionCode exceeds Android limit')
    return code


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        raise SystemExit('Usage: qa_version_code.py RUN_NUMBER RUN_ATTEMPT')
    try:
        print(qa_version_code(int(argv[1]), int(argv[2])))
    except ValueError as error:
        raise SystemExit(str(error)) from error
    return 0


if __name__ == '__main__':
    main(sys.argv)
