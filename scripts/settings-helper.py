"""Start/status/stop the narrow ADB settings helper on an explicitly selected HY320 Pro."""
from pathlib import Path
import argparse,os,subprocess,hashlib,time,shlex,json
p=argparse.ArgumentParser();p.add_argument('--device',required=True);p.add_argument('--status',action='store_true');p.add_argument('--stop',action='store_true');p.add_argument('--originals',action='store_true');p.add_argument('--restore',help='Recorded wifi/bluetooth/time/zone/timer/hotspot/hotspot_power/ethernet.<interface>/bluetooth_alias.<address>/helper_mode or permission key');p.add_argument('--apply',action='store_true');a=p.parse_args();here=Path(__file__).resolve().parents[1]/'dist'
from device import Device
Device(a.device).guard()
adb=Path(os.environ['ANDROID_HOME'])/'platform-tools/adb.exe';uri='content://com.gathin.tvhome.settings-session'
def run(command):return subprocess.check_output([str(adb),'-s',a.device,'shell',command],text=True,timeout=35).strip()
if run('getprop ro.build.id')!='Projector_20260713.153921' or run('am get-current-user')!='0':raise SystemExit('Unexpected firmware or user')
if a.originals:print(run('content call --uri '+uri+' --method originals'));raise SystemExit()
if a.restore:
 if not a.apply:print('Restore preview: recorded '+a.restore+'. Clock undo compensates elapsed time within the same boot. Turning Wi-Fi off can end this connection. Add --apply to perform this single restore.');raise SystemExit()
 print(run('content call --uri '+uri+' --method restore --arg '+shlex.quote(a.restore)));raise SystemExit()
if a.stop:
 state=run('content call --uri '+uri+' --method status')
 if 'connected=false' in state:print('Helper is already stopped; no setting changed.');raise SystemExit()
 if 'connected=true' not in state:raise SystemExit('Helper state is unavailable; no stop request sent.')
 print(run('content call --uri '+uri+' --method stop'));raise SystemExit()
status=run('content call --uri '+uri+' --method status')
if a.status or 'connected=true' in status:print(status);raise SystemExit()
if run('settings get global adb_wifi_enabled')!='1':raise SystemExit('Enable paired Wireless debugging before starting the helper')
path=run('pm path com.gathin.tvhome').splitlines()[0][8:]
expected=(here/'CleanYourTV-HY320Pro-Advanced.apk.sha256').read_text().split()[0]
if run('sha256sum '+shlex.quote(path)).split()[0]!=expected:raise SystemExit('Installed APK does not match this helper delivery')
if not run('command -v nohup'):raise SystemExit('This firmware has no inspected nohup command; no detached helper started')
command='CLASSPATH='+shlex.quote(path)+' nohup app_process /system/bin --nice-name=cleanyourtv-settings-helper com.gathin.tvhome.ShellSettingsSession > /data/local/tmp/cleanyourtv-settings-helper.log 2>&1 < /dev/null &'
run(command)
for _ in range(8):
 time.sleep(.3);status=run('content call --uri '+uri+' --method status')
 if 'connected=true' in status:print('Settings helper connected. Expiry follows the launcher helper preference; debugging off, launcher process exit or reboot always ends it. No device setting changed by startup.');break
else:raise SystemExit('Helper did not connect. Inspect /data/local/tmp/cleanyourtv-settings-helper.log; do not retry indefinitely.')
