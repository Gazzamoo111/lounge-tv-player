#!/usr/bin/env python3
"""Resolve ONLY the known OwnTVApp release-repository conflict during upstream merges.

Fails closed if Lounge TV changed anything else in that source file relative
to the original merge base, or if any other file conflicts.
"""
import re
import subprocess
import sys

PATH = "app/src/main/java/tv/own/owntv/OwnTVApp.kt"
OWN_REPO = "Gazzamoo111/lounge-tv-player"
UPSTREAM_REPO = "ahXN00/OwnTV"

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

if len(sys.argv) != 2:
    sys.exit("Provide the upstream tag")
tag = sys.argv[1]
conflicts = git("diff", "--name-only", "--diff-filter=U").splitlines()
if conflicts != [PATH]:
    sys.exit("Unsafe merge conflicts, must resolve manually: " + ", ".join(conflicts))

base = git("merge-base", "HEAD", tag)
ours = git("show", "HEAD:" + PATH) + "\n"
original = git("show", base + ":" + PATH) + "\n"
pattern = r'(?m)^(\s*tv\.own\.owntv\.core\.CoreBuildInfo\.releaseRepo\s*=\s*)"' + re.escape(OWN_REPO) + r'"'
restored, count = re.subn(pattern, r'\g<1>"' + UPSTREAM_REPO + '"', ours)
if count != 1 or restored != original:
    sys.exit("OwnTVApp.kt has additional local changes: refusing automatic resolution")

subprocess.run(["git", "checkout", "--theirs", "--", PATH], check=True)
subprocess.run(["git", "add", "--", PATH], check=True)
subprocess.run(["git", "commit", "--no-edit"], check=True)
print("Resolved only the known updater-repository conflict; rebranding must follow.")
