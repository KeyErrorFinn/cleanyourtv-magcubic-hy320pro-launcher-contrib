# Build both editions

The documented build environment is Windows, Python 3.10+, JDK 17, Android SDK platform 34,
build-tools 34.0.0, NDK 30.0.16248370 and CMake 3.22.1. Set ANDROID_HOME and JAVA_HOME.
The standalone companion toolchain currently uses Windows SDK executable paths. Other operating
systems have not been validated. Dependency downloads require network access at build time only.

Create a private signing identity once with JDK keytool; let its interactive prompts collect passwords:

```powershell
keytool -genkeypair -keystore "$HOME\cleanyourtv-release.jks" -alias cleanyourtv -keyalg RSA -keysize 3072 -validity 10000
```

Keep that key private and backed up. Never commit it. Set CYTV_STORE_PASSWORD and CYTV_KEY_PASSWORD
in the current process environment using hidden entry, for example:

```powershell
$storeSecret = Read-Host 'Keystore password' -AsSecureString
$keySecret = Read-Host 'Key password' -AsSecureString
$env:CYTV_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $storeSecret).Password
$env:CYTV_KEY_PASSWORD = [System.Net.NetworkCredential]::new('', $keySecret).Password
python scripts/build.py --edition both --keystore "$HOME\cleanyourtv-release.jks" --alias cleanyourtv
Remove-Item Env:CYTV_STORE_PASSWORD, Env:CYTV_KEY_PASSWORD
```

Use `--edition basic` or `--edition advanced` to build one. Both launchers intentionally share package
`com.gathin.tvhome` and version code 100 so a same-signed edition switch can be a normal update, after
stopping/turning off the helper. They cannot be installed side by side. Starter package:
`local.hy320.helperstarter`. Keep both apps on the same signing key.

The script extracts the actual public certificate, pins it into the Advanced helper and companion,
runs JVM tests and lint for the chosen editions and builds signed APKs. Advanced also builds the
companion from pinned SHA256-checked Maven dependencies. Outputs and hashes go into ignored `dist/`.
No device is contacted. Native code is compiled from source; no device vendor library is copied.

Fork builders use their own key. That build cannot update an existing installation signed by someone
else; Android will reject the signature mismatch. Do not bypass it. Back up, restore stock Home and
use the normal user-app removal/reinstall flow if deliberately changing publishers, then redo grants
and pairing. The release publisher must retain its key for all future updates. Public certificate
fingerprints can be shared; private keys and pairing state cannot.

Build scripts are repeatable, not a promise of byte-identical APKs across arbitrary toolchain/time
variations. Check source tag, tool versions, APK hash and signing certificate together. The old upstream
512KB gate applies to upstream contributions; Advanced is explicitly a larger independent fork. No
upstream gate or safety script is modified here.
