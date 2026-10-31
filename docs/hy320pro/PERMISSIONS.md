# Advanced permissions and helper

Basic has no integrated settings backend, secure/system settings write permissions, Bluetooth-control
permissions, native settings bridge, starter status provider or shell-helper entry point. Optional
location access is only for the Wi-Fi-name label; declining it leaves a generic Network shortcut.
Both launchers are offline and request no INTERNET permission.

Advanced uses standard permissions where possible and the reviewed manufacturer interface where
necessary. Permission grants do not make it a system app or root. The code exposes typed actions,
not an arbitrary command runner.

| Access | Purpose |
|---|---|
| MODIFY_AUDIO_SETTINGS | Media-volume/mute controls |
| WRITE_SETTINGS special app access | Button sound, screen-saver timeout, 12/24-hour format |
| WRITE_SECURE_SETTINGS | Automatic date/time and time zone, keyboard selection/subtype, ADB/Wireless debugging keys |
| CHANGE_CONFIGURATION | System locale list |
| BLUETOOTH_CONNECT/SCAN | Paired devices, requested foreground discovery and supported audio operations |
| ACCESS_FINE_LOCATION, Wi-Fi/network access | SSID and requested foreground Wi-Fi discovery |
| PACKAGE_USAGE_STATS declaration | Optional app storage usage readout; separate special access, not automatically granted |
| REQUEST_DELETE_PACKAGES | Android's user-confirmed user-app removal UI |
| Signature-only starter permission | Confirmation between the same-signed launcher and companion |

The broad Android permissions above are broader than the specific controls. They are not generic
system-write endpoints. The ordinary UI keeps Android consent for pairing, app removal and other
system-controlled confirmations. Unknown hardware/calibration interfaces are not guessed.

After installing Advanced manually, the following previews the documented grants:

```powershell
python scripts/device.py --device <projector endpoint> grant-advanced
```

Add `--apply` before `grant-advanced` if you want these controls. It records each original grant and
WRITE_SETTINGS app-op, grants the listed runtime/development permissions and verifies their states.
It does not alter debugging, networking, calibration, clock values or Home. Usage access is optional
and is not included in this grant command. Save its `.local/.../original.json` for precise undo.

## Optional Settings Starter

Install the same-signed `CleanYourTV-Settings-Starter.apk` using the normal install script. The
companion has INTERNET (loopback TLS ADB client only), ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED,
WRITE_SECURE_SETTINGS (only adb_wifi_enabled) and the custom signature permission. No root,
device-admin, always-on remote server, computer ADB key import or generic command interface.

Preview the initial setup:

```powershell
python scripts/setup-starter.py --device <projector endpoint> --enable-boot --brief-debugging
```

Add `--setup` to perform it. This requires already-authorised paired Wireless debugging and verifies
both installed APK hashes against `dist/`. It records original companion grant/preferences/trust,
grants companion WRITE_SECURE_SETTINGS only if absent, pairs one companion-owned Keystore identity
through Android's normal pairing protocol and verifies real local shell UID2000. Codes stay in
memory/Binder. It preserves other paired computers and never disables authentication or expiry.

The selected mode briefly enables paired Wireless debugging at boot, starts the existing typed helper
through a local TLS connection, verifies a signed nonce/UID2000 attachment, then switches debugging
off. This authenticated network debugging window is intentional and optional. The helper retains
shell authority while debugging is off. It ends on reboot, launcher-process death, explicit Stop or
selected expiry. Stop pauses recovery for the current boot. Jobs are bounded; no permanent polling
watchdog. If trust expires, Wi-Fi consent changes or startup fails, normal pairing/consent may be needed.

Verify after a normal reboot without pressing Start helper now: Settings > Other settings > Settings
Starter should read Helper connected - Wireless debugging off. Confirm physical controls yourself.
The companion is opened there, not as an extra app-grid tile. Ordinary computer-started helpers retain
their debugging-off stop rule. `scripts/settings-helper.py --device ... --status` can inspect one while
ADB is available; it cannot reconnect to a closed listener.
