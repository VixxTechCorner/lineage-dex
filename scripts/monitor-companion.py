#!/usr/bin/env python3
"""Local, authenticated ADB window companion for a running scrcpy monitor session."""
import argparse
import hmac
import json
import re
import secrets
import shlex
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

COMPONENT = re.compile(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+')


def tasks(text):
    """Parse Android 13 am stack list, retaining each task's owning display."""
    result = []
    display = None
    for line in text.splitlines():
        match = re.search(r'\bdisplayId=(\d+)', line)
        if match:
            display = int(match[1])
        match = re.search(r'\btaskId=(\d+):\s+(\S+)', line)
        if match and display is not None:
            result.append((int(match[1]), match[2], display))
    return result


def canonical(component):
    package, activity = component.split('/', 1)
    return package + '/' + (package + activity if activity.startswith('.') else activity)


class Companion:
    def __init__(self, serial, display=None):
        self.serial = serial
        self.display = display

    def adb(self, *args):
        result = subprocess.run(['adb', '-s', self.serial, *args], capture_output=True,
                                text=True, timeout=30, check=True)
        output = result.stdout + result.stderr
        if re.search(r'(^|\n)(Error:|Exception|SecurityException)', output):
            raise RuntimeError(output.strip())
        return output.strip()

    def shell(self, *args):
        return self.adb('shell', shlex.join([str(a) for a in args]))

    def find_display(self):
        ids = {d for _, c, d in tasks(self.shell('am', 'stack', 'list'))
               if c.startswith('dev.lineagedex/') and d > 0}
        if len(ids) != 1:
            raise RuntimeError('Start monitor.sh first. If multiple desktops exist, pass --display ID.')
        return ids.pop()

    def launch(self, data):
        component = data.get('component', '')
        bounds = data.get('bounds')
        if not isinstance(component, str) or not COMPONENT.fullmatch(component):
            raise ValueError('Invalid app component')
        if component.startswith('dev.lineagedex/'):
            raise ValueError('Use the desktop command to return to the desktop')
        if type(data.get('display')) is not int or data['display'] != self.display:
            raise ValueError('Request is for a different display; restart the companion')
        if (not isinstance(bounds, list) or len(bounds) != 4
                or any(type(v) is not int for v in bounds)
                or not 0 <= bounds[0] < bounds[2] <= 16384
                or not 0 <= bounds[1] < bounds[3] <= 16384):
            raise ValueError('Invalid window bounds')
        self.shell('am', 'start', '-W', '--user', 'current', '--display', self.display,
                   '--windowingMode', '5', '-a', 'android.intent.action.MAIN',
                   '-c', 'android.intent.category.LAUNCHER', '-n', component)
        candidates = [(t, c, d) for t, c, d in tasks(self.shell('am', 'stack', 'list'))
                      if canonical(c) == canonical(component) and d == self.display]
        if len(candidates) != 1:
            raise RuntimeError('Android did not expose one matching app task on the monitor. '
                               'Window placement was not confirmed.')
        task = candidates[0][0]
        self.shell('am', 'task', 'resize', task, *bounds)
        dump = self.shell('dumpsys', 'activity', 'activities')
        task_line = re.search(r'Task\{[^\n]*#' + str(task) + r'\b[^\n]*', dump)
        if not task_line or not re.search(r'\bmode=freeform\b', task_line[0]):
            raise RuntimeError('Android did not confirm freeform mode. Check setup-multitasking.py '
                               'status, reboot after enabling it, and retry. The app may be fullscreen.')
        return {'ok': True, 'message': 'Freeform task confirmed; window bounds requested.'}



IDENTITY_PROPERTIES = (
    'ro.product.vendor.device', 'ro.product.vendor.model',
    'ro.product.odm.device', 'ro.product.odm.model',
    'ro.product.device', 'ro.product.model', 'ro.product.name',
    'ro.vendor.product.device', 'ro.vendor.product.model',
)


def identify_tablet(run):
    # A GSI may replace system product names with generic/treble names.
    # Read the hardware vendor/ODM identity as well; do not require stock Android's name.
    observed = {}
    for prop in IDENTITY_PROPERTIES:
        value = run('getprop', prop).strip()
        observed[prop] = value
        normalized = re.sub(r'[^a-z0-9]', '', value.lower())
        if re.fullmatch(r'(?:lenovo)?(?:tb)?x306x(?:eea|row)?', normalized):
            return prop, value
    details = '; '.join(k + '=' + v for k, v in observed.items() if v)
    raise RuntimeError('Cannot confirm the Lenovo TB-X306X from Android product properties. '
                       'No changes made. Detected: ' + (details or '(all properties empty)'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='ADB serial (required when multiple devices are connected)')
    parser.add_argument('--display', type=int, help='Monitor display ID; otherwise detect the DeX desktop')
    args = parser.parse_args()
    serial = args.serial
    if not serial:
        lines = subprocess.check_output(['adb', 'devices'], text=True, timeout=15).splitlines()[1:]
        devices = [line.split()[0] for line in lines if len(line.split()) >= 2 and line.split()[1] == 'device']
        if len(devices) != 1:
            parser.error('Connect just the Lenovo tablet, or use --serial SERIAL.')
        serial = devices[0]
    bridge = Companion(serial, args.display)
    identity_property, identity_value = identify_tablet(bridge.shell)
    print(f'Tablet identified: {identity_value} ({identity_property})', flush=True)
    if bridge.display is None:
        bridge.display = bridge.find_display()
    if bridge.display <= 0:
        raise ValueError('Select a secondary monitor display, not the tablet screen.')
    if not any(d == bridge.display and c.startswith('dev.lineagedex/')
               for _, c, d in tasks(bridge.shell('am', 'stack', 'list'))):
        raise RuntimeError('No Lineage DeX desktop on that monitor; start monitor.sh first.')
    token = secrets.token_hex(32)

    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(5)

        def do_POST(self):
            if self.path != '/launch' or not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + token):
                self.send_error(403)
                return
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 4096:
                    raise ValueError('Invalid request size')
                data = json.loads(self.rfile.read(length))
                if not isinstance(data, dict):
                    raise ValueError('Invalid request')
                result = bridge.launch(data)
            except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as error:
                result = {'ok': False, 'message': str(error)}
            body = json.dumps(result).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            print(result['message'], flush=True)

        def log_message(self, *args):
            pass

    server = HTTPServer(('127.0.0.1', 0), Handler)
    port = server.server_port
    reverse = False
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        bridge.adb('reverse', f'tcp:{port}', f'tcp:{port}')
        reverse = True
        def desktop():
            bridge.shell('am', 'start', '--display', bridge.display, '--windowingMode', '1',
                         '-n', 'dev.lineagedex/.ExternalDesktopActivity',
                         '--es', 'bridge_token', token, '--ei', 'bridge_port', port)
        desktop()
        print(f'Companion ready on display {bridge.display}. Open apps or pairs from the monitor desktop.')
        print('Commands: desktop (return to workspace), status, quit. Keep this terminal open.')
        while True:
            command = input('DeX > ').strip().lower()
            if command in ('quit', 'exit'):
                break
            if command == 'desktop':
                desktop()
            elif command == 'status':
                print(bridge.shell('am', 'stack', 'list'))
            elif command:
                print('Commands: desktop, status, quit')
    finally:
        server.shutdown()
        server.server_close()
        if reverse:
            bridge.adb('reverse', '--remove', f'tcp:{port}')
        print('Companion stopped. Existing apps remain open.')


if __name__ == '__main__':
    try:
        main()
    except (KeyboardInterrupt, EOFError):
        pass
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        raise SystemExit('Companion stopped: ' + str(error))
