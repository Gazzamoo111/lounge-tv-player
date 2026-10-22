#!/usr/bin/env python3
"""Verify APK identity, signer and monotonic version without installing it."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def normalize_digest(value: str) -> str:
    digest = re.sub(r'[^0-9a-fA-F]', '', value).lower()
    if len(digest) != 64:
        raise ValueError('Expected a SHA-256 certificate digest')
    return digest


def parse_badging(output: str) -> tuple[str, int, str]:
    match = re.search(
        r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'",
        output,
        re.MULTILINE,
    )
    if not match:
        raise ValueError('Could not read package identity from aapt output')
    return match.group(1), int(match.group(2)), match.group(3)


def parse_signer_digest(output: str) -> str:
    matches = re.findall(
        r'^(?:Signer #\d+|V\d+(?:\.\d+)? Signer):? certificate SHA-256 digest:\s*(\S+)',
        output,
        re.MULTILINE,
    )
    digests = {normalize_digest(value) for value in matches}
    if not digests:
        raise ValueError('Could not read signer certificate from apksigner output')
    if len(digests) != 1:
        raise ValueError('APK has more than one signer certificate')
    return digests.pop()


def find_build_tool(name: str) -> str:
    direct = shutil.which(name)
    if direct:
        return direct
    android_home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if android_home:
        candidates = list((Path(android_home) / 'build-tools').glob(f'*/{name}'))
        if candidates:
            return str(max(candidates, key=lambda path: tuple(int(x) for x in re.findall(r'\d+', path.parent.name))))
    raise FileNotFoundError(f'{name} not found; set ANDROID_HOME or add Android build-tools to PATH')


def tool_output(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or f'{command[0]} failed')
    return result.stdout


def inspect_apk(apk: Path, aapt: str, apksigner: str) -> tuple[str, int, str, str]:
    if not apk.is_file():
        raise FileNotFoundError(apk)
    package_name, version_code, version_name = parse_badging(tool_output([aapt, 'dump', 'badging', str(apk)]))
    signer = parse_signer_digest(tool_output([apksigner, 'verify', '--print-certs', str(apk)]))
    return package_name, version_code, version_name, signer


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('--previous', type=Path)
    parser.add_argument('--expected-package', required=True)
    parser.add_argument('--expected-cert-sha256', required=True)
    parser.add_argument('--expected-version-code', type=int)
    args = parser.parse_args(argv[1:])

    aapt = find_build_tool('aapt')
    apksigner = find_build_tool('apksigner')
    candidate = inspect_apk(args.candidate, aapt, apksigner)
    package_name, version_code, version_name, signer = candidate
    expected_signer = normalize_digest(args.expected_cert_sha256)

    if package_name != args.expected_package:
        raise SystemExit(f'Wrong package: {package_name}; expected {args.expected_package}')
    if signer != expected_signer:
        raise SystemExit('Candidate signer certificate does not match the pinned expected certificate')
    if args.expected_version_code is not None and version_code != args.expected_version_code:
        raise SystemExit(f'Wrong versionCode: {version_code}; expected {args.expected_version_code}')

    if args.previous:
        previous = inspect_apk(args.previous, aapt, apksigner)
        previous_package, previous_code, _, previous_signer = previous
        if previous_package != package_name:
            raise SystemExit(f'Package changed: {previous_package} -> {package_name}')
        if previous_signer != signer:
            raise SystemExit('Candidate signer certificate differs from the installed-channel APK')
        if version_code <= previous_code:
            raise SystemExit(f'versionCode did not advance: {previous_code} -> {version_code}')
        print(f'PASS: update-compatible {package_name} {previous_code} -> {version_code}; signer SHA-256 {signer}')
    else:
        print(f'PASS: {package_name} versionCode {version_code} ({version_name}); signer SHA-256 {signer}')
    return 0


if __name__ == '__main__':
    main(sys.argv)
