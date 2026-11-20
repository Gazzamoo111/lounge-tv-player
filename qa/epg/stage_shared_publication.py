"""Stage an approved shared package in an epg-data checkout."""
import hashlib
import re
import shutil
import sys
import tarfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
import json


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def stage(package, approved_digest, checkout):
    if sha256(package) != approved_digest:
        raise ValueError("Shared release package digest differs from approval")
    paths = {"guide.xml.gz", "manifest.json", "canonical.sha256"}
    with TemporaryDirectory() as temporary, tarfile.open(package, "r:gz") as archive:
        root = Path(temporary)
        members = archive.getmembers()
        names = {member.name for member in members}
        if len(members) != len(names) or not paths.issubset(names):
            raise ValueError("Missing or duplicate shared release paths")
        for member in members:
            if member.isdir() and member.name == "short":
                continue
            if not member.isfile() or not (
                member.name in paths or re.fullmatch(r"short/[a-f0-9]{8}\.json", member.name)
            ):
                raise ValueError("Unexpected path in shared release package")
        archive.extractall(root, members=members, filter="data")
        manifest = json.loads((root / "manifest.json").read_text())
        age = datetime.now(timezone.utc) - datetime.fromisoformat(manifest["generated_at"])
        if not timedelta(0) <= age <= timedelta(hours=24):
            raise ValueError("Shared candidate needs a fresh guarded build")
        canonical = checkout / "epg" / "guide.xml.gz"
        if sha256(canonical) != (root / "canonical.sha256").read_text().strip():
            raise ValueError("Canonical guide changed after candidate validation")
        bulk = checkout / "epg" / "bulk"
        bulk.mkdir(exist_ok=True)
        short = bulk / "short"
        if short.exists():
            shutil.rmtree(short)
        shutil.copytree(root / "short", short)
        for name in ("guide.xml.gz", "manifest.json"):
            shutil.copyfile(root / name, bulk / name)


if __name__ == "__main__":
    stage(Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3]))
