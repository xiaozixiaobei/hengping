"""Emulator-only regression checks; requires adb, Python and Pillow."""
import io
import sys
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

ADB = os.environ.get('ADB', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'))
PACKAGE = 'com.hengping.standby'
OUT = Path(__file__).resolve().parents[1] / 'artifacts'


def adb(*args, data=None):
    return subprocess.run([ADB, *args], input=data, check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def hierarchy():
    adb('shell', 'uiautomator', 'dump', '/sdcard/hengping-regression.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/hengping-regression.xml'))


def find(resource, scroll=False):
    for _ in range(5 if scroll else 1):
        for item in hierarchy().iter('node'):
            if item.get('resource-id') == PACKAGE + ':id/' + resource:
                return item
        if scroll:
            adb('shell', 'input', 'swipe', '700', '2500', '700', '800', '250')
    raise AssertionError('Missing control: ' + resource)


def wait_for(resource, scroll=False):
    last_error = None
    for _ in range(10):
        try:
            return find(resource, scroll=scroll)
        except AssertionError as error:
            last_error = error
            time.sleep(0.3)
    raise last_error


def tap(item):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', item.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))


def launch_main():
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'am', 'start', '-f', '0x10008000', '-n', PACKAGE + '/.MainActivity')
    time.sleep(1.5)


def focused():
    text = adb('shell', 'dumpsys', 'window').decode()
    return next((line for line in text.splitlines() if 'mCurrentFocus=' in line), '')


def wait_standby(expected=True):
    for _ in range(30):
        if (PACKAGE + '.StandbyActivity' in focused()) == expected:
            return
        time.sleep(0.4)
    raise AssertionError('Standby visibility did not become ' + str(expected) + ': ' + focused())


def background_reaches_edges():
    adb('shell', 'am', 'force-stop', PACKAGE)
    old = adb('exec-out', 'run-as', PACKAGE, 'cat', 'shared_prefs/standby.xml')
    settings = ET.fromstring(old)
    for item in list(settings):
        if item.get('name') in ('backgroundUri', 'autoStart'):
            settings.remove(item)
    ET.SubElement(settings, 'string', name='backgroundUri').text = (
        'file:///data/user/0/' + PACKAGE + '/files/regression-background.png')
    ET.SubElement(settings, 'boolean', name='autoStart', value='false')
    image = Image.new('RGB', (1000, 600), (65, 135, 195))
    exif = image.getexif()
    exif[274] = 6
    image.paste((220, 60, 50), (0, 0, 500, 300))
    image.paste((60, 180, 80), (500, 0, 1000, 300))
    image.paste((50, 90, 210), (0, 300, 500, 600))
    image.paste((220, 190, 50), (500, 300, 1000, 600))
    image_bytes = io.BytesIO()
    image.save(image_bytes, format='JPEG', exif=exif)
    adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'files')
    adb('shell', 'run-as', PACKAGE, 'tee', 'files/regression-background.png',
        data=image_bytes.getvalue())
    adb('shell', 'run-as', PACKAGE, 'tee', 'shared_prefs/standby.xml',
        data=ET.tostring(settings))
    try:
        launch_main()
        thumbnail = find('background_thumbnail')
        settings_raw = adb('exec-out', 'screencap', '-p')
        (OUT / 'settings-with-photo.png').write_bytes(settings_raw)
        preview = Image.open(io.BytesIO(settings_raw)).convert('RGB')
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', thumbnail.get('bounds')))
        pixel = preview.getpixel((x1 + (x2-x1)//4, y1 + (y2-y1)//4))
        assert pixel[2] > pixel[0] + 50, 'Photo thumbnail ignored EXIF orientation: ' + str(pixel)
        tap(find('open_standby'))
        time.sleep(2)
        raw = adb('exec-out', 'screencap', '-p')
        (OUT / 'background-edge-regression.png').write_bytes(raw)
        shot = Image.open(io.BytesIO(raw)).convert('RGB')
        width, height = shot.size
        for x in (10, width-11):
            for y in (height//4, 3*height//4):
                assert sum(shot.getpixel((x, y))) > 100, (
                    'Background black strip at ' + str((x, y, shot.getpixel((x, y)))))
    finally:
        adb('shell', 'am', 'force-stop', PACKAGE)
        adb('shell', 'run-as', PACKAGE, 'tee', 'shared_prefs/standby.xml', data=old)


def calendar_gestures():
    launch_main()
    wait_for('calendar_background', scroll=True)
    for option in ('calendar_background_frosted',
                   'calendar_background_liquid',
                   'calendar_background_none'):
        tap(find(option, scroll=True))
    tap(find('open_standby'))
    time.sleep(1.5)
    def month():
        description = find('standby_face').get('content-desc')
        assert '星期：日、一、二、三、四、五、六' in description
        match = re.search(r'月历：(\d+)年(\d+)月', description)
        assert match, description
        return tuple(map(int, match.groups()))
    current = month()
    raw = adb('exec-out', 'screencap', '-p')
    width, height = Image.open(io.BytesIO(raw)).size
    adb('shell', 'input', 'swipe', str(int(width*.86)), str(int(height*.55)),
        str(int(width*.62)), str(int(height*.55)), '350')
    time.sleep(1.2)
    following = (current[0] + 1, 1) if current[1] == 12 else (current[0], current[1] + 1)
    assert month() == following, 'Left swipe did not change month or clock reset it'
    original_scale = adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip()
    try:
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.15')
        time.sleep(1.5)
        assert month() == following, 'Configuration recreation reset the browsed month'
    finally:
        adb('shell', 'settings', 'put', 'system', 'font_scale', original_scale)
        time.sleep(1.5)
    (OUT / 'calendar-next-month.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('shell', 'input', 'swipe', str(int(width*.62)), str(int(height*.55)),
        str(int(width*.86)), str(int(height*.55)), '350')
    assert month() == current, 'Right swipe did not return to previous month'
    adb('shell', 'input', 'swipe', str(int(width*.86)), str(int(height*.55)),
        str(int(width*.62)), str(int(height*.55)), '350')
    adb('shell', 'input', 'tap', str(int(width*.68)), str(int(height*.13)))
    assert month() == current, 'Tapping month heading did not restore today'
    (OUT / 'standby-analog.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')


def charging_and_lock():
    adb('shell', 'dumpsys', 'battery', 'unplug')
    adb('shell', 'appops', 'set', PACKAGE, 'SYSTEM_ALERT_WINDOW', 'allow')
    launch_main()
    control = find('auto_start', scroll=True)
    if control.get('checked') != 'true':
        tap(control)
    time.sleep(1)
    # Dismiss a possible notification-permission prompt through the shell on this emulator.
    adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    adb('shell', 'dumpsys', 'battery', 'set', 'ac', '1')
    wait_standby()
    adb('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    time.sleep(2)
    wait_standby()
    power = adb('shell', 'dumpsys', 'power').decode()
    assert 'mWakefulness=Awake' in power, 'Charging lock did not wake display'
    (OUT / 'standby-charging-locked.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('shell', 'dumpsys', 'battery', 'unplug')
    wait_standby(False)
    # Stop monitoring through the settings switch.
    launch_main()
    control = find('auto_start', scroll=True)
    if control.get('checked') == 'true':
        tap(control)
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    adb('shell', 'dumpsys', 'battery', 'set', 'ac', '1')
    time.sleep(2)
    wait_standby(False)


if __name__ == '__main__':
    assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1', 'Only run on emulator'
    OUT.mkdir(exist_ok=True)
    failures = []
    try:
        checks = ((background_reaches_edges,) if '--photo-only' in sys.argv else
                  (calendar_gestures,) if '--calendar-only' in sys.argv else
                  (background_reaches_edges, calendar_gestures, charging_and_lock))
        for check in checks:
            try:
                check()
                print('PASS:', check.__name__, flush=True)
            except (AssertionError, subprocess.CalledProcessError) as error:
                failures.append(check.__name__)
                print('FAIL:', check.__name__, str(error), flush=True)
    finally:
        adb('shell', 'dumpsys', 'battery', 'reset')
        adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    raise SystemExit(bool(failures))
