#!/usr/bin/env python3
"""Publish the current experimental build; never replace existing tags or asset bytes."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('archive_publisher', ROOT / 'scripts/publish-archive.py')
archive = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive)


def verify():
    row = json.loads((ROOT / 'release-current.json').read_text())
    version = row['version']
    if not re.fullmatch(r'\d+\.\d+\.\d+', version) or row['tag'] != 'v' + version:
        raise ValueError('Invalid version')
    if row['repository'] != archive.REPOSITORY:
        raise ValueError('Unexpected repository')
    folder = ROOT / 'releases' / row['tag']
    files = []
    for name, digest in row['files'].items():
        if Path(name).name != name or not re.fullmatch(r'[0-9a-f]{64}', digest):
            raise ValueError('Invalid asset record')
        path = folder / name
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError('Asset checksum mismatch: ' + name)
        files.append(path)
    expected = f'lineage-dex-{version}.apk', f'lineage-dex-{version}-source.zip', 'RELEASE-NOTES.md', 'SHA256SUMS'
    if set(row['files']) != set(expected):
        raise ValueError('Incomplete release assets')
    checksums = ''.join(row['files'][name] + '  ' + name + '\n' for name in expected if name != 'SHA256SUMS')
    if (folder / 'SHA256SUMS').read_text() != checksums:
        raise ValueError('Checksum list does not match manifest')
    return row, files


def publish(api, row, files, target):
    ref = api.request('GET', 'git/ref/tags/' + row['tag'], missing_ok=True)
    if ref is None:
        api.request('POST', 'git/refs', {'ref': 'refs/tags/' + row['tag'], 'sha': target})
    elif ref['object']['sha'] != target:
        # Reruns of later workflow commits must not move published source tags.
        raise RuntimeError('Existing tag points elsewhere; refusing to move ' + row['tag'])
    notes = next(p for p in files if p.name == 'RELEASE-NOTES.md').read_text()
    release = api.request('GET', 'releases/tags/' + row['tag'], missing_ok=True)
    if release is None:
        release = api.request('POST', 'releases', {'tag_name': row['tag'], 'target_commitish': target,
            'name': row['title'], 'body': notes, 'draft': True, 'prerelease': True, 'make_latest': 'false'})
    elif release['body'].strip() != notes.strip() or not release['prerelease']:
        raise RuntimeError('Existing release differs; refusing to overwrite')
    assets = {a['name']: a for a in api.request('GET', f"releases/{release['id']}/assets?per_page=100")}
    for path in files:
        data = path.read_bytes()
        asset = assets.get(path.name)
        if asset is None:
            url = release['upload_url'].split('{')[0] + '?name=' + urllib.parse.quote(path.name)
            asset = api.request('POST', url, data, binary=True)
        downloaded = api.request('GET', 'releases/assets/' + str(asset['id']), download=True)
        if hashlib.sha256(downloaded).hexdigest() != row['files'][path.name]:
            raise RuntimeError('Uploaded asset differs; leaving draft: ' + path.name)
    if release['draft']:
        api.request('PATCH', f"releases/{release['id']}", {'draft': False, 'prerelease': True, 'make_latest': 'false'})
    print('Published and downloaded-checksum-verified: ' + release['html_url'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    row, files = verify()
    if args.check:
        print('Verified current release assets: ' + row['tag'])
        return
    target = os.environ.get('RELEASE_TARGET_SHA', '')
    token = os.environ.get('GH_TOKEN', '')
    if not re.fullmatch(r'[0-9a-f]{40}', target) or not token:
        raise ValueError('Run publication in GitHub Actions')
    publish(archive.GitHub(token), row, files, target)


if __name__ == '__main__':
    main()
