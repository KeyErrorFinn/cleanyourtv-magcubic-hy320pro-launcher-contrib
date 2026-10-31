"""Explicit one-time companion setup on an explicitly selected projector; preview by default."""
from pathlib import Path
import argparse,os,subprocess,json,re,hashlib,time,shlex,datetime
p=argparse.ArgumentParser();p.add_argument('--device',required=True);p.add_argument('--setup',action='store_true');p.add_argument('--enable-boot',action='store_true');p.add_argument('--brief-debugging',action='store_true');a=p.parse_args()
if not a.setup:
 print('Preview: require existing authorised Wireless ADB, verify installed delivery hashes, record original starter grant/preferences and trusted set, grant starter WRITE_SECURE_SETTINGS if needed, pair only its own new identity through normal Android pairing, explicitly enrol this daemon certificate and apply the requested boot options. No Home, disabled-package, USB, authentication-expiry or calibration change. Add --setup to perform.');raise SystemExit()
from device import Device
Device(a.device).guard()
here=Path(__file__).resolve().parents[1]/'dist';adb=Path(os.environ['ANDROID_HOME'])/'platform-tools/adb.exe'
def run(*args):
 r=subprocess.run([str(adb),'-s',a.device,*args],capture_output=True,check=True,timeout=60)
 return r.stdout.decode(errors='replace').strip()
if run('shell','getprop ro.build.id')!='Projector_20260713.153921' or run('shell','am get-current-user')!='0':raise SystemExit('Exact firmware/user guard failed')
if run('shell','settings get global adb_wifi_enabled')!='1':raise SystemExit('Enable paired Wireless debugging normally before setup; no debugging switch was changed')
paths={}
for package,file in [('com.gathin.tvhome','CleanYourTV-HY320Pro-Advanced.apk'),('local.hy320.helperstarter','CleanYourTV-Settings-Starter.apk')]:
 path=run('shell','pm path '+package).splitlines()[0].removeprefix('package:');expected=Path(str(here/file)+'.sha256').read_text().split()[0]
 if run('shell','sha256sum '+shlex.quote(path)).split()[0]!=expected:raise SystemExit('Installed delivery hash mismatch: '+package)
 paths[package]=path
state=run('shell','content call --uri content://local.hy320.helperstarter.setup --method startup-status')
if 'enabled=true' in state:raise SystemExit('Turn Start after reboot off while changing setup; no option changed')
relay='CLASSPATH='+shlex.quote(paths['local.hy320.helperstarter'])+' app_process /system/bin local.hy320.helperstarter.SetupRelay '
def trusted():
 text=run('shell',relay+'list');return set(json.loads(next(x for x in text.splitlines() if x.startswith('{')))['fingerprints'])
before=trusted();package=run('shell','dumpsys package local.hy320.helperstarter');old_grant='android.permission.WRITE_SECURE_SETTINGS: granted=true' in package
folder=here.parent/'.local'/'setup-records'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S');folder.mkdir(parents=True,exist_ok=False)
record={'write_secure_settings_granted':old_grant,'startup_status':state,'paired_fingerprints_private':sorted(before)};(folder/'before-private.json').write_text(json.dumps(record,indent=2),encoding='utf-8')
if not old_grant:run('shell','pm grant local.hy320.helperstarter android.permission.WRITE_SECURE_SETTINGS')
run('shell','am start -n local.hy320.helperstarter/.MainActivity');time.sleep(1)
identity=run('shell','content call --uri content://local.hy320.helperstarter.setup --method identity');match=re.search(r'fingerprint=([0-9A-F:]{47})',identity)
if not match:raise SystemExit('Local identity unavailable; no trust changes requested')
own=match.group(1)
if own not in before:
 run('shell',relay+'pair');after=trusted()
 if after-before!={own} or before-after:raise SystemExit('Unexpected trust change; preserve records and inspect, do not revoke unrelated identities')
result=run('shell',relay+'enrol-verify')
if 'Authenticated local shell: UID 2000' not in result:raise SystemExit('Authenticated local check did not pass; no boot option enabled')
run('shell','content call --uri content://local.hy320.helperstarter.setup --method configure --extra enabled:b:'+str(a.enable_boot).lower()+' --extra brief_debug_at_boot:b:'+str(a.brief_debugging).lower())
(folder/'after-private.json').write_text(json.dumps({'paired_fingerprints_private':sorted(trusted()),'enabled':a.enable_boot,'brief_debug_at_boot':a.brief_debugging},indent=2),encoding='utf-8')
print('One-time setup verified. Use Start helper now or restart normally. Setup records are private. No system package/Home/authentication-expiry change.')
