import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

path = Path(__file__).resolve().parents[1] / "scripts/publish-versions.py"
spec = importlib.util.spec_from_file_location("publisher", path)
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublishingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.folder = self.root / ".release-bundle/v0.1.0"
        self.folder.mkdir(parents=True)
        (self.root / "release-notes").mkdir()
        (self.root / "release-notes/v0.1.0.md").write_text("Prototype notes\n")
        self.apk = self.folder / "lineage-dex-0.1.0.apk"
        self.apk.write_bytes(b"test-apk")
        (self.folder / "SHA256SUMS").write_text("test checksum\n")
        (self.folder / "RELEASE-NOTES.md").write_text("Prototype notes\n")
        self.row = {"tag": "v0.1.0", "version": "0.1.0", "title": "Prototype",
                    "asset": str(self.apk.relative_to(self.root)), "notes": "release-notes/v0.1.0.md"}
        self.created = False
        self.assets = {}
        self.calls = []
        self.failure = False
        self.published = False

    def fake_run(self, *args, check=True):
        self.calls.append(args)
        operation = args[2]
        if operation == "view":
            if not self.created:
                return subprocess.CompletedProcess(args, 1, "", "release not found")
            data = {"assets": [{"name": name} for name in self.assets],
                    "isDraft": not self.published, "isPrerelease": True, "body": "Prototype notes\n"}
            return subprocess.CompletedProcess(args, 0, json.dumps(data), "")
        if operation == "create":
            self.assertIn("--draft", args)
            self.assertIn("--prerelease", args)
            self.created = True
        elif operation == "upload":
            if self.failure:
                raise RuntimeError("Simulated upload failure")
            file = Path(args[4])
            self.assets[file.name] = file.read_bytes()
        elif operation == "download":
            name = args[args.index("--pattern") + 1]
            folder = Path(args[args.index("--dir") + 1])
            (folder / name).write_bytes(self.assets[name])
        elif operation == "edit":
            self.assertEqual(3, len(self.assets))
            self.assertIn("--draft=false", args)
            self.published = True
        else:
            self.fail("Unexpected external command: " + str(args))
        return subprocess.CompletedProcess(args, 0, "", "")

    def publish(self):
        with patch.object(publisher, "ROOT", self.root), patch.object(publisher, "run", self.fake_run):
            with contextlib.redirect_stdout(io.StringIO()):
                publisher.upload_verified(self.row)

    def test_publish_only_after_assets_verified(self):
        self.publish()
        self.assertTrue(self.published)
        self.assertEqual(3, sum(call[2] == "download" for call in self.calls))

    def test_upload_failure_leaves_draft(self):
        self.failure = True
        with self.assertRaises(RuntimeError):
            self.publish()
        self.assertTrue(self.created)
        self.assertFalse(self.published)

    def test_different_existing_asset_is_not_overwritten(self):
        self.created = True
        self.assets[self.apk.name] = b"different-bytes"
        with self.assertRaisesRegex(RuntimeError, "no overwrite"):
            self.publish()
        self.assertFalse(any(call[2] == "upload" for call in self.calls))
        self.assertFalse(self.published)

    def test_repeated_publish_rechecks_without_replacing_assets(self):
        self.publish()
        self.calls.clear()
        self.publish()
        self.assertFalse(any(call[2] == "upload" for call in self.calls))
        self.assertEqual(3, sum(call[2] == "download" for call in self.calls))


if __name__ == "__main__":
    unittest.main()
