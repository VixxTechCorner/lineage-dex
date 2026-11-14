# Browser-based publication

The repository is now connected through the GitHub integration. Original APKs are stored under `release-assets/`, with known-bug notes and checksums. The **Publish archived versions** Actions workflow publishes six prereleases without a terminal login. It runs on relevant changes to `main` and can be rerun from GitHub Actions → Publish archived versions → Run workflow. Assets remain drafts until their downloaded checksums match. Historical tags point to archive-metadata commits; the current version points to its source publication commit.

The workflow uses the repository-scoped automatic GitHub token with contents-write permission. No personal token or signing key is stored in this project.

## Original local CLI fallback

The older CLI publisher below applies to the original local archive history. The website-created repository has a different initial commit: use the Actions workflow for this repository instead of force-pushing the old local history.

# Publish the archived versions

All six APKs were found locally, their embedded version names checked, their signatures verified, and their SHA-256 hashes recorded. They use the same development signing certificate. See [RELEASES.md](RELEASES.md) for known defects and source provenance. None should be presented as a stable production release.

The prepared local bundle is in `.release-bundle/`. It is excluded from source control, along with SDKs, firmware, logs, developer-settings backups, signing keys and generated outputs. The publisher uploads APKs as release assets. The current source is published on `main`; historical tags record archive metadata only because their original source snapshots were not preserved.

The connected GitHub integration cannot create repositories or upload release assets. Run the publisher from your Arch terminal, using GitHub CLI authentication:

```bash
sudo pacman -Syu github-cli
gh auth login --hostname github.com --git-protocol https --web
cd ~/lineage-dex
python scripts/publish-versions.py --check
python scripts/publish-versions.py
```

Sign in as **VixxTechCorner** in the browser. Do not paste tokens or passwords into a chat. The script creates public `VixxTechCorner/lineage-dex` if absent, pushes `main` and the six local tags without force, then creates six prereleases. Each new release stays a draft until every uploaded asset is downloaded and checksum-verified. If interrupted, rerun the script; it checks existing assets and refuses to overwrite different bytes or release notes. It does not change the visibility of an existing private repository.

Version 0.3.0 is explicitly labeled as a historical build with a known startup crash. Version 0.4.0 documents the reported fullscreen multitasking limitation. Version 0.4.1 is the current experimental source snapshot and needs device validation after setup. GitHub's source ZIPs for older tags contain archival metadata, not the original build sources.

CLI references: https://cli.github.com/manual/gh_repo_create and https://cli.github.com/manual/gh_release_create
