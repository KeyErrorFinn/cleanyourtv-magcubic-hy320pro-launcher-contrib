"""Explicit-device HY320 Pro setup. Preview mutations unless --apply is supplied."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess

ROOT=Path(__file__).resolve().parents[1]
PACKAGE='com.gathin.tvhome'
STOCK='com.htc.magcubicos/com.htc.magcubicos.activity.MainActivity'
GRANTS=['WRITE_SECURE_SETTINGS','CHANGE_CONFIGURATION','BLUETOOTH_CONNECT','BLUETOOTH_SCAN','ACCESS_FINE_LOCATION']

class Device:
    def __init__(self, selector):
        self.selector=selector
        self.adb=Path(os.environ['ANDROID_HOME'])/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')
    def run(self,*args):
        result=subprocess.run([str(self.adb),'-s',self.selector,*args],capture_output=True,timeout=90)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace').strip() or 'ADB command failed')
        return result.stdout.decode(errors='replace').strip()
    def shell(self,command): return self.run('shell',command)
    def guard(self):
        expected={'ro.build.id':'Projector_20260713.153921','ro.build.version.sdk':'34',
                  'ro.product.device':'rk3566_rgo','ro.product.board':'exdroid'}
        for prop,value in expected.items():
            if self.shell('getprop '+prop)!=value: raise RuntimeError('Unsupported device/firmware: '+prop)
        if self.shell('am get-current-user')!='0': raise RuntimeError('Only verified user 0 is supported')
        return expected
    def installed(self,package): return self.shell('pm path '+package).startswith('package:')
    def quiescent(self):
        if not self.installed(PACKAGE): return
        dump=self.shell('dumpsys package '+PACKAGE)
        if 'com.gathin.tvhome.projection-recovery' not in dump: return
        status=self.shell('content query --uri content://com.gathin.tvhome.projection-recovery/status')
        for key in ['orientation_pending','manual_pending','reset_pending','scaling_pending','automatic_pending','busy']:
            if re.findall(r'\b'+key+r'=([01])\b',status)!=['0']:
                raise RuntimeError('Finish the active projection operation before updating')
        if 'connected=true' in self.shell('content call --uri content://com.gathin.tvhome.settings-session --method status'):
            raise RuntimeError('Stop the settings helper in its UI before updating or restoring grants; timers must be inactive')
    def starter_stopped(self):
        if self.installed('local.hy320.helperstarter'):
            status=self.shell('content call --uri content://local.hy320.helperstarter.setup --method startup-status')
            if 'enabled=true' in status: raise RuntimeError('Turn Start after reboot off before updating')

def backup(device):
    directory=ROOT/'.local'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    directory.mkdir(parents=True)
    commands={'packages':'pm list packages -f','disabled':'pm list packages -d','properties':'getprop',
        'global':'settings list global','secure':'settings list secure','system':'settings list system',
        'home_handlers':'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
        'home':'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
        'display_size':'wm size','display_density':'wm density','launcher':'dumpsys package '+PACKAGE,
        'stock':'dumpsys package com.htc.magcubicos','write_settings':'cmd appops get '+PACKAGE+' WRITE_SETTINGS',
        'usage':'cmd appops get '+PACKAGE+' GET_USAGE_STATS'}
    values={}
    for name,command in commands.items():
        try: values[name]=device.shell(command)
        except RuntimeError as error: values[name]='UNREADABLE: '+str(error)
        (directory/(name+'.txt')).write_text(values[name]+'\n',encoding='utf-8')
    grants={permission:bool(re.search(r'android\.permission\.'+permission+r': granted=true',values['launcher'])) for permission in GRANTS}
    home=next((line for line in reversed(values['home'].splitlines()) if re.fullmatch(r'[\w.$]+/[\w.$]+',line)),None)
    mode=re.search(r'WRITE_SETTINGS:\s*(\w+)',values['write_settings'])
    record={'device':device.selector,'home':home,'grants':grants,'write_settings_mode':mode.group(1) if mode else 'default',
        'notice':'Private records, not firmware backup. Unsupported items are recorded, not silently assumed.'}
    (directory/'original.json').write_text(json.dumps(record,indent=2)+'\n')
    # Ordinary APKs only; no system partition files are copied or modified.
    for package in [PACKAGE,'local.hy320.helperstarter']:
        if device.installed(package):
            remote=device.shell('pm path '+package).splitlines()[0].removeprefix('package:')
            target=directory/(package+'.apk');device.run('pull',remote,str(target))
            expected=device.shell('sha256sum '+shlex.quote(remote)).split()[0]
            if hashlib.sha256(target.read_bytes()).hexdigest()!=expected: raise RuntimeError('Backup APK hash mismatch')
    print('Private recovery record: '+str(directory))
    return directory

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--device',required=True,help='Confirmed serial or IP:port; every ADB command selects this exact device')
    p.add_argument('--apply',action='store_true')
    sub=p.add_subparsers(dest='action',required=True)
    sub.add_parser('inspect');sub.add_parser('backup')
    install=sub.add_parser('install');install.add_argument('--apk',type=Path,required=True)
    sub.add_parser('grant-advanced')
    home=sub.add_parser('home');home.add_argument('--stock',action='store_true')
    restore=sub.add_parser('restore-access');restore.add_argument('--record',type=Path,required=True)
    a=p.parse_args();d=Device(a.device);d.guard()
    if a.action=='inspect':
        print('Matching firmware, SDK, device, board and user verified. No changes.');return
    if a.action=='backup': backup(d);return
    if not a.apply:
        print('Preview: '+a.action+'. No changes. Add --apply before the action to execute after reading docs/INSTALL.md.');return
    if a.action=='install':
        apk=a.apk.resolve();expected=Path(str(apk)+'.sha256').read_text().split()[0]
        if hashlib.sha256(apk.read_bytes()).hexdigest()!=expected: raise RuntimeError('APK hash mismatch')
        tools=Path(os.environ['ANDROID_HOME'])/'build-tools/34.0.0'
        details=subprocess.check_output([str(tools/('aapt2.exe' if os.name=='nt' else 'aapt2')),'dump','badging',str(apk)],text=True)
        match=re.search(r"package: name='([^']+)'",details)
        if not match or match.group(1) not in [PACKAGE,'local.hy320.helperstarter']: raise RuntimeError('Unexpected APK package')
        package=match.group(1);d.starter_stopped();d.quiescent();backup(d)
        result=d.run('install','--no-incremental','-r',str(apk))
        if 'Success' not in result: raise RuntimeError('Installation not confirmed: '+result)
        remote=d.shell('pm path '+package).splitlines()[0].removeprefix('package:')
        if d.shell('sha256sum '+shlex.quote(remote)).split()[0]!=expected: raise RuntimeError('Installed hash mismatch')
        print('Installed and hash-verified only. No grants, Home selection or package disabling.');return
    if a.action=='grant-advanced':
        dump=d.shell('dumpsys package '+PACKAGE)
        if 'com.gathin.tvhome.settings-session' not in dump: raise RuntimeError('Advanced edition must be installed')
        backup(d)
        for permission in GRANTS:
            full='android.permission.'+permission;d.shell('pm grant --user 0 '+PACKAGE+' '+full)
            if not re.search(re.escape(full)+r': granted=true',d.shell('dumpsys package '+PACKAGE)):
                raise RuntimeError('Grant not confirmed: '+full)
        d.shell('cmd appops set '+PACKAGE+' WRITE_SETTINGS allow')
        if not re.search(r'WRITE_SETTINGS:\s*allow',d.shell('cmd appops get '+PACKAGE+' WRITE_SETTINGS')):
            raise RuntimeError('WRITE_SETTINGS access not confirmed')
        print('Documented Advanced grants verified. Original access states saved; no calibration/debugging/network change.');return
    if a.action=='home':
        backup(d);component=STOCK if a.stock else PACKAGE+'/.MainActivity'
        d.shell('cmd package set-home-activity --user 0 '+component)
        resolved=d.shell('cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
        if component.split('/')[0]+'/' not in resolved: raise RuntimeError('Home selection was not confirmed')
        d.shell('input keyevent KEYCODE_HOME')
        print('Home handler verified. Confirm the visible screen and physical Home key yourself. Stock launcher remains enabled.');return
    if a.action=='restore-access':
        d.starter_stopped();d.quiescent();record=json.loads(a.record.read_text())
        if record.get('device')!=a.device: raise RuntimeError('Record belongs to a different selected endpoint; inspect manually')
        for permission in GRANTS:
            if permission not in record['grants']: raise RuntimeError('Incomplete original grant state')
        mode=record['write_settings_mode']
        if mode not in ['default','allow','deny','ignore','foreground']: raise RuntimeError('Unknown original app-op state')
        backup(d)
        for permission in GRANTS:
            command='grant' if record['grants'][permission] else 'revoke'
            d.shell('pm '+command+' --user 0 '+PACKAGE+' android.permission.'+permission)
            actual=bool(re.search(r'android\.permission\.'+permission+r': granted=true',d.shell('dumpsys package '+PACKAGE)))
            if actual!=record['grants'][permission]: raise RuntimeError('Permission restore not confirmed: '+permission)
        d.shell('cmd appops set '+PACKAGE+' WRITE_SETTINGS '+mode)
        restored=re.search(r'WRITE_SETTINGS:\s*(\w+)',d.shell('cmd appops get '+PACKAGE+' WRITE_SETTINGS'))
        if (restored.group(1) if restored else 'default')!=mode: raise RuntimeError('App-op restore was not confirmed')
        print('Original grants restored. See recovery guide for app-op/Home confirmation and companion cleanup. No packages enabled blindly.')

if __name__=='__main__': main()
