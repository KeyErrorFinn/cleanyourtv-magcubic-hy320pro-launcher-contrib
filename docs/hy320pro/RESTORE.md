# Restore and remove

Keep stock Home installed/enabled throughout. Do not clear its data or disable provisioning, network,
remote, input, thermal, light-source or projector services. No release script disables system packages.

## Return to stock Home

Use Android's Home chooser to select MagcubicOS. With authorised ADB on the matching firmware:

```powershell
python scripts/device.py --device <projector endpoint> --apply home --stock
```

To reopen stock Home directly, selecting the exact device:

```powershell
adb -s <projector endpoint> shell am start -n com.htc.magcubicos/com.htc.magcubicos.activity.MainActivity
```

The `home --stock` action targets the known stock component; it is not a claim that every owner
originally used that component. Your backup's home.txt/original.json records your actual prior handler.
To restore a different recorded handler, review it, then use `cmd package set-home-activity --user 0`
with that exact component. Confirm Home physically and after normal reboot.

## Undo Advanced access/helper

1. Finish or restore any active projection transaction; cancel timed shutdown. Keep current calibration
   unless intentionally using that control's recorded Restore original action. Wall presets are personal.
2. Turn Settings Starter's Start after reboot off; Stop helper. Do not stop an operation halfway through.
3. If computer access is needed, enable paired Wireless debugging normally and use its current endpoint.
4. Restore launcher permission/app-op states using the **record from before the grants**:

```powershell
python scripts/device.py --device <projector endpoint> restore-access --record .local/<your backup>/original.json
```

Preview first; add `--apply` before `restore-access` to apply. It preserves already-granted permissions
and restores the original WRITE_SETTINGS app-op, rather than revoking/enabling everything blindly.
It does not roll back calibration or system settings changed during actual use; use their own recorded
undo controls/records while the needed authority still exists, before revoking access.

5. Revoke only the companion-owned paired identity. With the companion still installed, use its
   inspected shell-only relay: find its path using `adb -s ... shell pm path local.hy320.helperstarter`,
   then run `CLASSPATH=<that exact base.apk> app_process /system/bin local.hy320.helperstarter.SetupRelay revoke-local`
   in the selected device shell. Compare other paired identities with your private setup record. Never
   revoke all computers as a shortcut. The native Android paired-computers UI is also available.
6. Restore the companion's WRITE_SECURE_SETTINGS grant to the original state recorded in
   `.local/setup-records/.../before-private.json`; normal user-app removal also removes its own grant.
7. After choosing stock Home, remove the launcher and companion through Android's normal app-info
   Uninstall controls. Never uninstall system packages or delete system APKs.
8. Disable/revoke debugging access no longer needed. If restoring its prior setting would end the
   connection, do that last. Preserve exact prior values/presence from your records. Reboot normally
   and verify the previously used ports as described in INSTALL.md.

For a same-publisher APK rollback, retain the previous release APK and certificate/hash. Android may
refuse a lower versionCode; do not force a downgrade or clear app data to bypass it. Restore stock Home
and use ordinary user-app removal/reinstallation only after backing up and accepting app-data loss.
The previous personal recovery ZIP is private and is not part of this public release.

Most changes are reversible while Settings or ADB remains accessible. A restore script/settings dump
is not a full firmware backup and cannot guarantee recovery from a boot failure.
