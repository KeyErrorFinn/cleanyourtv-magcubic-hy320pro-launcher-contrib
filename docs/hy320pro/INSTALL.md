# Install on your own HY320 Pro

Keep normal power and ventilation. Do not use factory/engineering menus. A regular USB-A host port
does not establish support for a computer debugging cable; do not use a USB-A-to-A cable. USB debugging
may affect USB-host peripherals on some firmware; this is a possibility to check, not a proven fact
about every HY320 Pro. The paired Wireless debugging route does not require enabling USB debugging.

## Connect and verify

Use current Android SDK Platform-Tools. Open the ordinary Android Developer options page exposed on
your unit. If there is paired Wireless debugging, pair with its pairing port, then connect using its
separate connection port. Keep the pairing code private. Never substitute a guessed port or switch
to plaintext legacy TCP ADB just to follow an example. Accept Android authorisation/Wi-Fi consent
yourself. If developer access is unavailable, stop the ADB setup.

In the commands below, replace `<projector endpoint>` with the confirmed IP:connection-port (or exact
ADB serial). Every device script requires it. No network scanning or automatic device selection occurs.

```powershell
python scripts/device.py --device <projector endpoint> inspect
python scripts/device.py --device <projector endpoint> backup
```

Backup records go in `.local/` and are private. Unsupported/unreadable items are recorded. These are
not firmware backups. They do not guarantee recovery from boot failure. Establish physical baseline:
Home/Back/Settings, HDMI picture/audio, focus and projection-control access, held-volume/mute,
streaming playback, your USB/Bluetooth/casting features and normal standby/wake. Do not change
calibration to test access. Preserve any pre-existing failures in your notes.

## Install manually first

Place APKs and their adjacent `.sha256` files in `dist/` (or build them there). Preview:

```powershell
python scripts/device.py --device <projector endpoint> install --apk dist/CleanYourTV-HY320Pro-Basic.apk
```

Add `--apply` before `install` to actually install. Advanced uses
`dist/CleanYourTV-HY320Pro-Advanced.apk`. The script backs up, performs ordinary `adb -s ... install -r`,
and verifies installed APK bytes. It never changes Home, grants access or disables packages. Signature,
SDK/verifier/storage errors must be investigated as reported; never disable verification to force it.

Open the installed Home app from the stock app list. Check the layout/app grid and physical remote.
Basic Settings opens the stock manufacturer page; hold Settings and choose Launcher settings for
Home appearance. Advanced opens the integrated eight-section page. Install/grant/configure its helper
only if you choose Advanced; follow [PERMISSIONS.md](PERMISSIONS.md).

## Select Home separately

Prefer Android's normal default Home chooser. If supported on this firmware, the explicit alternative is:

```powershell
python scripts/device.py --device <projector endpoint> --apply home
```

It records the previous handler and verifies resolution, then presses Home. You must confirm the screen
and physical remote. Keep stock `com.htc.magcubicos` enabled. Test return from apps, Settings and HDMI,
then reboot normally and confirm Home, projector controls and used features still work.

## Updates and edition switches

Use the same publisher/signing certificate. In Advanced, finish any projection operation, cancel any
scheduled shutdown, turn off Settings Starter's Start after reboot, then Stop helper. Re-enable paired
Wireless debugging normally and use the installation command. Install/update both Advanced apps from
the same release; redo starter setup only as documented. Switching to Basic requires no Advanced grants
or starter; disable its startup, stop it and remove it through the normal app UI if no longer wanted.
Appearance preferences normally survive a same-signed update; UI and app-specific authority can differ.

## Close debugging

Basic: disable all debugging modes you enabled, revoke the computer authorisation/remove pairing if
no longer wanted, reboot normally, and check every previously used port from a network-capable computer.
`tools/adb-closed.py <projector ip>` is useful for supported ports; explicitly check a nonstandard paired
connection port too. Refusal is evidence for that endpoint. Timeout/routing/local permission errors
are inconclusive. `adb disconnect` only disconnects the client.

Advanced optional automatic helper: use the explicitly selected brief-window mode described in
PERMISSIONS.md. Keep the companion's own pairing while using that feature; revoke unneeded computer
pairings separately. Confirm live Helper connected - Wireless debugging off after reboot. Recheck USB
peripherals/network transport if debugging affected them. A closed port does not certify the firmware.
