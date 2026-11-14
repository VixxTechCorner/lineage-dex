#!/usr/bin/env python3
"""Publish checksum-verified repository assets in GitHub Actions, without a CLI login."""
import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "VixxTechCorner/lineage-dex"
EXPECTED = ("0.1.0", "0.2.0", "0.3.0", "0.3.1", "0.4.0", "0.4.1")


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        request = super().redirect_request(req, fp, code, msg, headers, newurl)
        if request is not None:
            request.remove_header("Authorization")
        return request


class GitHub:
    def __init__(self, token):
        self.token = token
        self.opener = urllib.request.build_opener(SafeRedirect())

    def request(self, method, path, data=None, binary=False, download=False, missing_ok=False):
        url = path if path.startswith("https://") else "https://api.github.com/repos/" + REPOSITORY + "/" + path
        if urllib.parse.urlparse(url).hostname not in ("api.github.com", "uploads.github.com"):
            raise ValueError("Unexpected GitHub API host")
        headers = {"Authorization": "Bearer " + self.token, "User-Agent": "Lineage-DeX-archive-publisher",
                   "Accept": "application/octet-stream" if download else "application/vnd.github+json",
                   "X-GitHub-Api-Version": "2022-11-28"}
        if data is not None:
            headers["Content-Type"] = "application/octet-stream" if binary else "application/json"
            if not binary:
                data = json.dumps(data).encode()
        request = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with self.opener.open(request, timeout=60) as response:
                content = response.read()
                return content if download else json.loads(content) if content else None
        except urllib.error.HTTPError as error:
            if missing_ok and error.code == 404:
                return None
            raise RuntimeError("GitHub API " + method + " failed with HTTP " + str(error.code)) from error


def safe_file(relative):
    path = (ROOT / relative).resolve()
    if not path.is_relative_to(ROOT) or not path.is_file():
        raise ValueError("Missing or invalid archive file: " + relative)
    return path


def assets(row):
    apk = safe_file(row["repository_asset"])
    if not apk.is_relative_to(ROOT / "release-assets"):
        raise ValueError("Asset outside release-assets")
    result = [apk, apk.parent / "SHA256SUMS", apk.parent / "RELEASE-NOTES.md"]
    if row["version"] == "0.4.1":
        result += [apk.parent / "lineage-dex-0.4.1-source.zip", apk.parent / "SOURCE-SHA256SUMS"]
    if not all(path.is_file() for path in result):
        raise ValueError("Incomplete assets for " + row["version"])
    return result


def verify():
    manifest = json.loads((ROOT / "release-manifest.json").read_text())
    rows = manifest["versions"]
    if manifest["repository"] != REPOSITORY or tuple(row["version"] for row in rows) != EXPECTED:
        raise ValueError("Unexpected manifest")
    for row in rows:
        apk = assets(row)[0]
        digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        if digest != row["sha256"] or apk.stat().st_size != row["bytes"]:
            raise ValueError("APK checksum mismatch for " + row["version"])
        if (apk.parent / "SHA256SUMS").read_text() != digest + "  " + apk.name + "\n":
            raise ValueError("Checksum file mismatch")
        if (apk.parent / "RELEASE-NOTES.md").read_text() != safe_file(row["notes"]).read_text():
            raise ValueError("Release notes mismatch")
    source = assets(rows[-1])[-2]
    if assets(rows[-1])[-1].read_text() != hashlib.sha256(source.read_bytes()).hexdigest() + "  " + source.name + "\n":
        raise ValueError("Source archive checksum mismatch")
    return rows


def publish_one(api, row, target):
    tag = row["tag"]
    ref = api.request("GET", "git/ref/tags/" + tag, missing_ok=True)
    if ref is None:
        api.request("POST", "git/refs", {"ref": "refs/tags/" + tag, "sha": target})
    elif ref["object"]["sha"] != target:
        raise RuntimeError("Existing tag points elsewhere; refusing to move " + tag)
    release = api.request("GET", "releases/tags/" + tag, missing_ok=True)
    notes = safe_file(row["notes"]).read_text()
    if release is None:
        release = api.request("POST", "releases", {"tag_name": tag, "target_commitish": target,
            "name": row["title"], "body": notes, "draft": True, "prerelease": True, "make_latest": "false"})
    elif not release["prerelease"] or release["body"].strip() != notes.strip():
        raise RuntimeError("Existing release differs; refusing to overwrite " + tag)
    existing = {asset["name"]: asset for asset in api.request("GET", "releases/" + str(release["id"]) + "/assets?per_page=100")}
    for path in assets(row):
        local = path.read_bytes()
        asset = existing.get(path.name)
        if asset is None:
            url = release["upload_url"].split("{", 1)[0] + "?name=" + urllib.parse.quote(path.name)
            asset = api.request("POST", url, local, binary=True)
        downloaded = api.request("GET", "releases/assets/" + str(asset["id"]), download=True)
        if hashlib.sha256(downloaded).digest() != hashlib.sha256(local).digest():
            raise RuntimeError("Uploaded or existing asset differs: " + path.name)
    if release["draft"]:
        api.request("PATCH", "releases/" + str(release["id"]), {"draft": False, "prerelease": True, "make_latest": "false"})
    print("Published and checksum-verified " + tag, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    rows = verify()
    if args.check:
        print("All six APKs and the source ZIP match their saved checksums.")
        return
    refs = json.loads((ROOT / "archive-refs.json").read_text())
    target = os.environ.get("ARCHIVE_TARGET_SHA", "")
    token = os.environ.get("GH_TOKEN", "")
    if not re.fullmatch(r"[0-9a-f]{40}", target) or not token:
        raise ValueError("Run publication in the GitHub Actions workflow")
    for row in rows:
        sha = refs.get(row["tag"], "")
        if not re.fullmatch(r"[0-9a-f]{40}", sha):
            raise ValueError("Missing archival commit for " + row["tag"])
        publish_one(GitHub(token), row, sha)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError, urllib.error.URLError) as error:
        print("Publication stopped: " + str(error), file=sys.stderr)
        sys.exit(1)
