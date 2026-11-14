import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

source = Path(__file__).resolve().parents[1] / "scripts/setup-multitasking.py"
spec = importlib.util.spec_from_file_location("setup_multitasking", source)
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class FakeDevice:
    def __init__(self):
        self.values = dict(zip(setup.SETTINGS, ("null", "0", "1")))
        self.mutations = []
        self.fail_key = None

    def run(self, command):
        if command == ["get-state"]:
            return "device"
        if command[:2] == ["shell", "getprop"]:
            return "TEST-TABLET"
        operation, key = command[2], command[4]
        if operation == "get":
            return self.values[key]
        if key == self.fail_key:
            raise RuntimeError("Simulated device disconnection")
        self.mutations.append(command)
        self.values[key] = "null" if operation == "delete" else command[5]
        return ""


class SetupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.device = FakeDevice()

    def configure(self, action):
        return setup.configure(action, self.device.run, self.folder)

    def test_enable_and_restore_preserve_absent_and_explicit_values(self):
        original = self.device.values.copy()
        self.configure("enable")
        self.assertTrue(all(v == "1" for v in self.device.values.values()))
        self.configure("restore")
        self.assertEqual(original, self.device.values)
        self.assertFalse(list(self.folder.glob("*.json")))

    def test_repeated_enable_does_not_replace_original_backup(self):
        original = self.device.values.copy()
        self.configure("enable")
        self.configure("enable")
        self.configure("restore")
        self.assertEqual(original, self.device.values)

    def test_failure_keeps_original_backup_for_restore(self):
        original = self.device.values.copy()
        self.device.fail_key = setup.SETTINGS[1]
        with self.assertRaises(RuntimeError):
            self.configure("enable")
        backup = next(self.folder.glob("*.json"))
        self.assertEqual(original, json.loads(backup.read_text())["values"])
        self.device.fail_key = None
        self.configure("restore")
        self.assertEqual(original, self.device.values)

    def test_status_does_not_mutate(self):
        self.assertEqual(self.device.values, self.configure("status"))
        self.assertFalse(self.device.mutations)

    def test_restore_without_backup_does_not_mutate(self):
        with self.assertRaises(ValueError):
            self.configure("restore")
        self.assertFalse(self.device.mutations)

    def test_wrong_device_backup_rejected_before_mutation(self):
        backup = self.folder / "multitasking-settings-TEST-TABLET.json"
        backup.write_text(json.dumps({"serial": "OTHER", "values": self.device.values}))
        with self.assertRaises(ValueError):
            self.configure("enable")
        self.assertFalse(self.device.mutations)


if __name__ == "__main__":
    unittest.main()
