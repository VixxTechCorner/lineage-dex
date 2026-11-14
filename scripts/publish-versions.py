#!/usr/bin/env python3
"""Publish the six prepared prereleases using the maintainer's GitHub CLI login."""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "VixxTechCorner/lineage-dex"
VERSIONS = ("0.1.0", "0.2.0", "0.3.0", "0.3.1", "0.4.0", "0.4.1")


def run(*args, check=True):
    return subprocess.run(list(args), cwd=ROOT, check=check, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def checked_file(relative):
    path = (ROOT / relative).resolve()
    if not path.is_relative_to(ROOT) or not path.is_file():
        raise ValueError("Missing or invalid release file: " + relative)
    return path


def verify_artifacts():
    manifest = json.loads((ROOT / "release-manifest.json").read_text())
    rows = manifest["versions"]
    if manifest["repository"] != REPOSITORY or tuple(r["version"] for r in rows) != VERSIONS:
        raise ValueError("Unexpected release manifest")
    for row in rows:
        apk = checked_file(row["asset"])
        if not apk.is_relative_to(ROOT / ".release-bundle") or apk.suffix != ".apk":
            raise ValueError("APK must come from the prepared release bundle")
        if apk.stat().st_size != row["bytes"] or hashlib.sha256(apk.read_bytes()).hexdigest() != row["sha256"]:
            raise ValueError("APK checksum mismatch for " + row["version"])
        checked_file(row["notes"])
        checksum = apk.parent / "SHA256SUMS"
        if checksum.read_text() != row["sha256"] + "  " + apk.name + "\n":
            raise ValueError("Checksum file mismatch for " + row["version"])
        if (apk.parent / "RELEASE-NOTES.md").read_text() != checked_file(row["notes"]).read_text():
            raise ValueError("Bundled release notes mismatch for " + row["version"])
    source = checked_file(".release-bundle/v0.4.1/lineage-dex-0.4.1-source.zip")
    expected_source = hashlib.sha256(source.read_bytes()).hexdigest() + "  " + source.name + "\n"
    if (source.parent / "SOURCE-SHA256SUMS").read_text() != expected_source:
        raise ValueError("Source archive checksum mismatch")
    return rows


def upload_verified(row):
    tag = row["tag"]
    result = run("gh", "release", "view", tag, "--repo", REPOSITORY,
                 "--json", "assets,isDraft,isPrerelease,body", check=False)
    if result.returncode:
        # A transport/authentication error must not be mistaken for a missing release.
        error = result.stderr.lower()
        if "not found" not in error and "http 404" not in error:
            raise RuntimeError(result.stderr.strip())
        run("gh", "release", "create", tag, "--repo", REPOSITORY, "--verify-tag",
            "--draft", "--prerelease", "--latest=false", "--title", row["title"],
            "--notes-file", str(checked_file(row["notes"])))
        result = run("gh", "release", "view", tag, "--repo", REPOSITORY,
                     "--json", "assets,isDraft,isPrerelease,body")
    release = json.loads(result.stdout)
    if not release["isPrerelease"]:
        raise RuntimeError("Existing stable release will not be overwritten: " + tag)
    if release["body"].strip() != checked_file(row["notes"]).read_text().strip():
        raise RuntimeError("Existing release notes differ; inspect before continuing: " + tag)
    folder = checked_file(row["asset"]).parent
    files = [folder / ("lineage-dex-" + row["version"] + ".apk"), folder / "SHA256SUMS", folder / "RELEASE-NOTES.md"]
    if row["version"] == "0.4.1":
        files += [checked_file(".release-bundle/v0.4.1/lineage-dex-0.4.1-source.zip"),
                  checked_file(".release-bundle/v0.4.1/SOURCE-SHA256SUMS")]
    existing = {asset["name"] for asset in release["assets"]}
    for file in files:
        if not file.is_file():
            raise ValueError("Missing asset: " + str(file))
        if file.name in existing:
            with tempfile.TemporaryDirectory(prefix="lineagedex-verify-") as temp:
                run("gh", "release", "download", tag, "--repo", REPOSITORY,
                    "--pattern", file.name, "--dir", temp)
                if hashlib.sha256((Path(temp) / file.name).read_bytes()).digest() != hashlib.sha256(file.read_bytes()).digest():
                    raise RuntimeError("Existing release asset differs; no overwrite: " + file.name)
        else:
            run("gh", "release", "upload", tag, str(file), "--repo", REPOSITORY)
            # Verify uploaded bytes before making a draft public.
            with tempfile.TemporaryDirectory(prefix="lineagedex-verify-") as temp:
                run("gh", "release", "download", tag, "--repo", REPOSITORY,
                    "--pattern", file.name, "--dir", temp)
                if hashlib.sha256((Path(temp) / file.name).read_bytes()).digest() != hashlib.sha256(file.read_bytes()).digest():
                    raise RuntimeError("Uploaded asset checksum mismatch: " + file.name)
    if release["isDraft"]:
        run("gh", "release", "edit", tag, "--repo", REPOSITORY,
            "--draft=false", "--prerelease", "--latest=false")
    print("Published and verified: https://github.com/" + REPOSITORY + "/releases/tag/" + tag, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify local files only; no network or publication")
    args = parser.parse_args()
    rows = verify_artifacts()
    if args.check:
        print("Verified all six original APK checksums and release notes. No publication attempted.")
        return
    if not shutil.which("gh"):
        raise RuntimeError("Install GitHub CLI first: sudo pacman -Syu github-cli")
    login = run("gh", "api", "user", "--jq", ".login").stdout.strip()
    if login.casefold() != "vixxtechcorner":
        raise RuntimeError("GitHub CLI must be signed in as VixxTechCorner; current account: " + login)
    # Verify local tags before creating any remote resource.
    for row in rows:
        run("git", "rev-parse", "--verify", "refs/tags/" + row["tag"])
    probe = run("gh", "repo", "view", REPOSITORY, "--json", "nameWithOwner,isPrivate", check=False)
    if probe.returncode:
        if not any(text in probe.stderr.lower() for text in ("could not resolve to a repository", "not found", "http 404")):
            raise RuntimeError(probe.stderr.strip())
        run("gh", "repo", "create", REPOSITORY, "--public", "--description",
            "Experimental Lineage DeX desktop for Lenovo TB-X306X; historical APK prereleases and current source.")
    elif json.loads(probe.stdout)["isPrivate"]:
        raise RuntimeError("An existing private repository has this name; its visibility was not changed")
    expected = "https://github.com/" + REPOSITORY + ".git"
    remote = run("git", "remote", "get-url", "origin", check=False)
    if remote.returncode:
        run("git", "remote", "add", "origin", expected)
    elif remote.stdout.strip() not in (expected, expected[:-4], "git@github.com:" + REPOSITORY + ".git"):
        raise RuntimeError("Existing origin points elsewhere; no remote was changed")
    # No force push and no global credential configuration changes.
    push = ["git", "-c", "credential.helper=", "-c", "credential.helper=!gh auth git-credential",
            "push", "--atomic", expected, "main", *["refs/tags/" + r["tag"] for r in rows]]
    run(*push)
    for row in rows:
        upload_verified(row)
    print("All six experimental releases are published: https://github.com/" + REPOSITORY + "/releases")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.SubprocessError) as error:
        print("Publication stopped: " + str(error), file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr.strip(), file=sys.stderr)
        sys.exit(1)
