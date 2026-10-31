"""Public edition boundary/installer tests. No device access."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
ANDROID='{http://schemas.android.com/apk/res/android}'
spec=importlib.util.spec_from_file_location('device',ROOT/'scripts/device.py')
device=importlib.util.module_from_spec(spec);spec.loader.exec_module(device)

class ReleaseTests(unittest.TestCase):
    def test_basic_has_no_advanced_permissions_or_providers(self):
        main=ET.parse(ROOT/'launcher/app/src/main/AndroidManifest.xml').getroot()
        permissions={x.get(ANDROID+'name') for x in main.findall('uses-permission')}
        for permission in ['WRITE_SECURE_SETTINGS','WRITE_SETTINGS','CHANGE_CONFIGURATION','BLUETOOTH_CONNECT','BLUETOOTH_SCAN']:
            self.assertNotIn('android.permission.'+permission,permissions)
        providers={x.get(ANDROID+'name') for x in main.find('application').findall('provider')}
        self.assertFalse({'.SettingsSessionProvider','.StarterStatusProvider','.ProjectionRecoveryProvider'} & providers)

    def test_shell_helper_source_only_in_advanced(self):
        self.assertFalse((ROOT/'launcher/app/src/main/java/com/gathin/tvhome/ShellSettingsSession.java').exists())
        self.assertTrue((ROOT/'launcher/app/src/advanced/java/com/gathin/tvhome/ShellSettingsSession.java').exists())

    def test_starter_confirmation_remains_signature_protected(self):
        root=ET.parse(ROOT/'launcher/app/src/advanced/AndroidManifest.xml').getroot()
        permission=root.find('permission')
        self.assertEqual(permission.get(ANDROID+'protectionLevel'),'signature')
        provider=next(x for x in root.find('application').findall('provider') if x.get(ANDROID+'name')=='.StarterStatusProvider')
        self.assertEqual(provider.get(ANDROID+'permission'),permission.get(ANDROID+'name'))

    def test_every_adb_call_selects_device(self):
        result=type('Result',(),{'returncode':0,'stdout':b'ok','stderr':b''})()
        with patch.object(device.subprocess,'run',return_value=result) as run:
            selected=device.Device('confirmed-projector:12345')
            selected.shell('getprop ro.build.id')
        command=run.call_args.args[0]
        self.assertEqual(command[1:4],['-s','confirmed-projector:12345','shell'])

    def test_guard_rejects_firmware_mismatch_before_mutation(self):
        selected=device.Device('confirmed-projector:12345')
        with patch.object(selected,'shell',return_value='different firmware') as shell:
            with self.assertRaises(RuntimeError): selected.guard()
        self.assertEqual(shell.call_count,1)

    def test_unknown_recovery_state_blocks_update(self):
        selected=device.Device('confirmed-projector:12345')
        with patch.object(selected,'installed',return_value=True), patch.object(selected,'shell',side_effect=[
                'com.gathin.tvhome.projection-recovery','busy=0']):
            with self.assertRaises(RuntimeError): selected.quiescent()

    def test_active_helper_blocks_update(self):
        selected=device.Device('confirmed-projector:12345')
        status=' '.join(x+'=0' for x in ['orientation_pending','manual_pending','reset_pending','scaling_pending','automatic_pending','busy'])
        with patch.object(selected,'installed',return_value=True), patch.object(selected,'shell',side_effect=[
                'com.gathin.tvhome.projection-recovery',status,'connected=true']):
            with self.assertRaises(RuntimeError): selected.quiescent()

if __name__=='__main__': unittest.main()
