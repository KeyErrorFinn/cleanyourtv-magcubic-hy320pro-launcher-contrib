# Compatibility and verification

One retail HY320 Pro was examined; Android reports manufacturer/brand rockchip, device/model
rk3566_rgo, board exdroid, Android 14 SDK34, firmware Projector_20260713.153921,
armeabi-v7a/armeabi userland and about 1GB RAM. A Leanback feature and ordinary app launchers are
present. Advertising an Android version does not establish certification or compatibility.

The original installed Advanced implementation passed owner checks for Home/Back/D-pad, integrated
settings navigation, media volume/mute/held repeat, HDMI video/audio, streaming playback and reboot
startup. Optical controls were separately verified during development with attended apply/restore
checks. Latest helper startup was owner-confirmed after normal reboot, without manual Start, showing
Helper connected - Wireless debugging off. A network check explicitly refused the prior endpoint.

Public Basic/Advanced source separation, certificate generation and public scripts are new packaging
changes. Build tests/static APK inspection do not substitute for a physical run of these exact public
APKs. The existing device installation is deliberately preserved while preparing this release.
No claim of a second tested projector or universal HY320 Pro support is made.

Known limits:

- Manufacturer reset and local/online firmware-update operations remain manufacturer workflows;
  Advanced does not claim complete replacement of every privileged system operation.
- Cache-only removal was denied by firmware authority. No app-data clearing fallback is used.
- Normal Android permission/pairing/installation confirmations can still appear.
- HDMI detection uses this firmware's source status; no invented standby/orange signal.
- Cast shortcut opens separately installed AirScreen; Miracast opens the existing receiver.
- Bluetooth-off/disconnect/forget tests were avoided where the sole remote could be lost.
- Enterprise Wi-Fi, Ethernet hardware, clock changes, permission/data operations and other disruptive
  controls have not all been verified end to end on every configuration.
- No measured app-start performance improvement is promised. Advanced helper adds memory overhead.
- Helper startup depends on legitimate pairing, firmware policy and network consent. Revocation/expiry,
  a changed firmware or launcher-process exit can require manual recovery.

Before enabling hardware writes on another unit, compare firmware/device/board/ABI, back up its own
values, establish a physical baseline and verify apply/readback/restore. Do not copy calibration or
disable guards merely because another projector has the same retail name.
