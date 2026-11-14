import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('monitor_companion', Path(__file__).resolve().parents[1] / 'scripts/monitor-companion.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

STACKS = '''RootTask id=2 bounds=[0,0][1280,800] displayId=0 userId=0
 taskId=3: com.tablet/.Main bounds=[0,0][1280,800] userId=0 visible=true
RootTask id=8 bounds=[0,0][1920,1080] displayId=2 userId=0
 taskId=9: dev.lineagedex/.ExternalDesktopActivity bounds=[0,0][1920,1080]
 taskId=11: com.example/.Main bounds=[16,40][950,992] userId=0 visible=true
'''

class Fake(m.Companion):
    def __init__(self, freeform=True):
        super().__init__('tablet', 2)
        self.calls = []
        self.freeform = freeform
    def shell(self, *args):
        self.calls.append(args)
        if args == ('am', 'stack', 'list'):
            return STACKS
        if args == ('dumpsys', 'activity', 'activities'):
            return 'Task{abc #11 type=standard A=com.example mode=' + ('freeform' if self.freeform else 'fullscreen') + '}'
        return 'Status: ok'

class CompanionTest(unittest.TestCase):
    def request(self, **kwargs):
        return dict(component='com.example/com.example.Main', display=2, bounds=[16,40,950,992], **kwargs)
    def test_gsi_uses_vendor_identity(self):
        props = {'ro.product.device': 'generic_arm64',
                 'ro.product.model': 'TrebleDroid',
                 'ro.product.vendor.model': 'Lenovo TB-X306X'}
        found = m.identify_tablet(lambda _, prop: props.get(prop, ''))
        self.assertEqual(found, ('ro.product.vendor.model', 'Lenovo TB-X306X'))
    def test_stock_identity_still_supported(self):
        self.assertEqual(m.identify_tablet(lambda _, p: 'X306X' if p == 'ro.product.device' else '')[1], 'X306X')
    def test_regional_product_name_supported(self):
        self.assertEqual(m.identify_tablet(lambda _, p: 'LenovoTB-X306X_EEA' if p == 'ro.product.name' else '')[1], 'LenovoTB-X306X_EEA')
    def test_wrong_tablet_and_huawei_rejected(self):
        for model in ('ALE-L21', 'TB-X306F', 'generic_arm64', ''):
            with self.assertRaisesRegex(RuntimeError, 'No changes made'):
                m.identify_tablet(lambda *a: model)
    def test_display_ownership(self):
        self.assertEqual(m.tasks(STACKS)[0], (3, 'com.tablet/.Main', 0))
        self.assertEqual(Fake().find_display(), 2)
    def test_ambiguous_monitor_rejected(self):
        f = Fake()
        f.shell = lambda *a: STACKS + '\nRootTask id=20 displayId=3\n taskId=21: dev.lineagedex/.ExternalDesktopActivity'
        with self.assertRaises(RuntimeError): f.find_display()
    def test_launch_and_resize_correct_task(self):
        f = Fake()
        self.assertTrue(f.launch(self.request())['ok'])
        self.assertIn(('am','task','resize',11,16,40,950,992), f.calls)
        self.assertEqual(f.calls[0][f.calls[0].index('--windowingMode')+1], '5')
    def test_fullscreen_is_not_reported_success(self):
        with self.assertRaisesRegex(RuntimeError, 'did not confirm freeform'):
            Fake(False).launch(self.request())
    def test_command_injection_rejected_before_adb(self):
        f = Fake(); data = self.request(); data['component'] = 'com.example/.Main;reboot'
        with self.assertRaises(ValueError): f.launch(data)
        self.assertEqual(f.calls, [])
    def test_wrong_display_rejected(self):
        f = Fake(); data = self.request(); data['display'] = 0
        with self.assertRaises(ValueError): f.launch(data)
        self.assertEqual(f.calls, [])
    def test_invalid_bounds_rejected(self):
        for bounds in ([0,0,0,2], [0,0,20000,2], [False,0,100,200], '0 0 100 200'):
            f = Fake(); data = self.request(); data['bounds'] = bounds
            with self.assertRaises(ValueError): f.launch(data)
            self.assertEqual(f.calls, [])
    def test_missing_task_is_not_resized(self):
        f = Fake(); data = self.request(); data['component'] = 'org.missing/.Main'
        with self.assertRaises(RuntimeError): f.launch(data)
        self.assertFalse(any(c[:3] == ('am','task','resize') for c in f.calls))

if __name__ == '__main__': unittest.main()
