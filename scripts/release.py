"""Version, Obtainium link and signed distribution checks. Python standard library only."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent.parent
REPO = "https://github.com/jeezab/ritela"
CONFIG = {"id": "app.ritela", "url": REPO, "author": "jeezab", "name": "Ritela"}


def version_code(name):
    if not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", name, re.ASCII):
        raise ValueError("Version must be a canonical major.minor.patch")
    major, minor, patch = map(int, name.split("."))
    if major > 2000 or minor > 999 or patch > 999:
        raise ValueError("Version components exceed Android versionCode bounds")
    code = major * 1_000_000 + minor * 1000 + patch
    if code == 0:
        raise ValueError("Version code must be positive")
    return code


def obtainium_link():
    payload = quote(json.dumps(CONFIG, separators=(",", ":")), safe="")
    return "https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/" + payload


def flatten_release_pages(pages):
    if not isinstance(pages, list) or any(not isinstance(page, list) for page in pages):
        raise ValueError("Expected an array of GitHub release pages from gh api --paginate --slurp")
    releases = [item for page in pages for item in page]
    if any(not isinstance(item, dict) or not isinstance(item.get("tag_name"), str) for item in releases):
        raise ValueError("Each GitHub release must have a tag_name")
    return releases


def check(root=ROOT, tag=None, releases=None):
    text = (root / "version.properties").read_text(encoding="utf-8-sig").strip()
    match = re.fullmatch(r"versionName=(\S+)", text)
    if not match:
        raise ValueError("version.properties must contain only versionName=major.minor.patch")
    name = match[1]
    code = version_code(name)
    if tag is not None and tag != "v" + name:
        raise ValueError("Git tag must equal v" + name)
    if releases is not None:
        for release in releases:
            if release.get("draft") or release.get("prerelease"):
                continue
            previous = release["tag_name"].removeprefix("v")
            if version_code(previous) >= code:
                raise ValueError("Version must exceed every existing stable release")
    if obtainium_link() not in (root / "README.md").read_text(encoding="utf-8-sig"):
        raise ValueError("README Obtainium link is missing or differs from the app config")
    return name, code


def validate_apk(badging, signature, name, code, certificate):
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ("app.ritela", str(code), name):
        raise ValueError("APK package/version does not match release metadata")
    if re.search(r"^application-debuggable", badging, re.M):
        raise ValueError("A debug APK cannot be distributed as a release")
    if re.search(r"android\.permission\.(INTERNET|ACCESS_NETWORK_STATE)", badging):
        raise ValueError("Unexpected network permissions")
    fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$", signature, re.M)
    expected = certificate.replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected) or fingerprints != [expected]:
        raise ValueError("APK must have exactly the configured distribution signing certificate")


def prepare(apk, sdk, output, name, code, certificate):
    tools = sdk / "build-tools" / "36.0.0"
    suffix = ".bat" if os.name == "nt" else ""
    signature = subprocess.check_output(
        [str(tools / ("apksigner" + suffix)), "verify", "--print-certs", str(apk)], text=True
    )
    badging = subprocess.check_output(
        [str(tools / ("aapt2.exe" if os.name == "nt" else "aapt2")), "dump", "badging", str(apk)], text=True
    )
    validate_apk(badging, signature, name, code, certificate)
    output.mkdir(parents=True, exist_ok=True)
    target = output / f"ritela-{name}.apk"
    if target.exists():
        raise ValueError("Output APK already exists; use a fresh output directory")
    shutil.copyfile(apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (output / (target.name + ".sha256")).write_text(f"{digest}  {target.name}\n", encoding="ascii")
    print(f"Verified {target.name}, versionCode={code}, SHA-256={digest}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["check", "link", "prepare"])
    parser.add_argument("--tag")
    sources = parser.add_mutually_exclusive_group()
    sources.add_argument("--releases", type=Path)
    sources.add_argument("--release-pages", type=Path)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--sdk", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "app/build/distribution")
    args = parser.parse_args()
    if args.action == "link":
        print(obtainium_link())
        return
    releases = json.loads(args.releases.read_text()) if args.releases else None
    if args.release_pages:
        releases = flatten_release_pages(json.loads(args.release_pages.read_text()))
    name, code = check(tag=args.tag, releases=releases)
    if args.action == "prepare":
        certificate = os.environ.get("ANDROID_SIGNING_CERT_SHA256", "")
        if not args.apk or not args.sdk or not certificate:
            parser.error("prepare requires --apk, --sdk and ANDROID_SIGNING_CERT_SHA256")
        prepare(args.apk.resolve(), args.sdk.resolve(), args.output, name, code, certificate)
    else:
        print(f"PASS: v{name}, versionCode={code}, Obtainium link")


if __name__ == "__main__":
    main()
