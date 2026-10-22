#!/usr/bin/env python3
"""Fail closed if requested release does not advance both name and Android version code."""
import json, os, subprocess, sys
name, code = sys.argv[1], int(sys.argv[2])
repo = os.environ['GITHUB_REPOSITORY']
result = subprocess.run(['gh','api',f'repos/{repo}/releases/latest'], capture_output=True, text=True)
if result.returncode:
    # Only accept 404 from the API for a fresh new repository.
    if 'HTTP 404' in result.stderr or 'Not Found' in result.stderr:
        print('No previous release found.')
        sys.exit(0)
    raise SystemExit('Cannot verify previous release; refusing to publish. ' + result.stderr)
release = json.loads(result.stdout)
previous = release['tag_name'].lstrip('v')
try:
    oldnums = [int(n) for n in previous.split('.')]
    newnums = [int(n) for n in name.split('.')]
except ValueError:
    raise SystemExit('Unrecognised older release version; manual inspection required')
if newnums <= oldnums:
    raise SystemExit(f'Release version {name} must be greater than {previous}')
# Android versionCode isn't in the GitHub release metadata; this workflow records it in release notes.
import re
match = re.search(r'Android versionCode:\s*(\d+)', release.get('body') or '')
if not match:
    raise SystemExit('Previous release does not record versionCode; inspect before publishing')
if code <= int(match.group(1)):
    raise SystemExit(f'Android versionCode {code} must exceed {match.group(1)}')
print('Version moves forward:', previous,'->',name)
