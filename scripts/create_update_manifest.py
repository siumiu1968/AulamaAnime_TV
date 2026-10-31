#!/usr/bin/env python3
"""Create the public update manifest for a verified Android TV release APK."""

import argparse
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import quote


def read_version(gradle_path: Path) -> tuple[str, int]:
    source = gradle_path.read_text(encoding="utf-8")
    names = re.findall(r'^\s*versionName\s*=\s*"([^"\n]+)"\s*(?://[^\n]*)?$', source, re.MULTILINE)
    codes = re.findall(r"^\s*versionCode\s*=\s*(\d+)\s*(?://[^\n]*)?$", source, re.MULTILINE)
    if len(names) != 1 or len(codes) != 1 or int(codes[0]) <= 0:
        raise ValueError("Gradle must declare one literal versionName and positive versionCode")
    return names[0], int(codes[0])


def create_manifest(apk: Path, gradle: Path, repository: str, tag: str, notes: str = "") -> dict:
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("repository must be an owner/name pair")
    if not tag.strip():
        raise ValueError("release tag must not be empty")
    version, version_code = read_version(gradle)
    digest = hashlib.sha256()
    size = 0
    with apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    if size == 0:
        raise ValueError("release APK must not be empty")
    manifest = {
        "status": "published",
        "platform": "android-tv",
        "version": version,
        "versionCode": version_code,
        "apk": f"https://github.com/{repository}/releases/download/{quote(tag, safe='')}/{quote(apk.name, safe='')}",
        "sha256": digest.hexdigest(),
        "size": size,
    }
    if notes.strip():
        manifest["notes"] = notes.strip()
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--gradle", type=Path, default=Path("app/build.gradle.kts"))
    parser.add_argument("--output", type=Path, default=Path("tv-update-manifest.json"))
    parser.add_argument("--notes-file", type=Path)
    args = parser.parse_args()
    try:
        notes = args.notes_file.read_text(encoding="utf-8") if args.notes_file else ""
        manifest = create_manifest(args.apk, args.gradle, args.repository, args.tag, notes)
        args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
