"""Build the signed local Settings Starter; invoked by scripts/build.py."""
from pathlib import Path
import hashlib
import os
import subprocess
import zipfile
import sys

root = Path(__file__).resolve().parent
sdk = Path(os.environ['ANDROID_HOME'])
tools = sdk / 'build-tools/34.0.0'
android = sdk / 'platforms/android-34/android.jar'
key = Path(os.environ['CYTV_KEYSTORE'])
alias = os.environ['CYTV_KEY_ALIAS']
cert = os.environ['CYTV_CERT_SHA256']
import re
if not re.fullmatch('[0-9a-f]{64}',cert): raise SystemExit('Explicit signing certificate required')
generated=root/'generated/local/hy320/helperstarter/ReleaseIdentity.java'
generated.parent.mkdir(parents=True,exist_ok=True)
generated.write_text('package local.hy320.helperstarter; final class ReleaseIdentity { static final String CERT="'+cert+'"; }\n')
if not key.is_file():
    raise SystemExit('Existing private signing key required; no new key generated.')

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

subprocess.run([sys.executable,str(root/'prepare-dependencies.py')],check=True)
classes = root / 'classes' 
dex = root / 'dex'
classes.mkdir(exist_ok=True)
dex.mkdir(exist_ok=True)
run('javac', '-source', '8', '-target', '8', '-classpath', str(android)+os.pathsep+str(root/'dependencies/conscrypt-2.8.0/classes.jar'),
    '-d', classes, *sorted((root/'src').rglob('*.java')), generated)
run(tools/'d8.bat', '--lib', android, '--min-api', '26', '--output', dex,
    *classes.rglob('*.class'), root/'dependencies/conscrypt-2.8.0/classes.jar')
native = root/'liblocalpairing.so'
clang = sdk/'ndk/30.0.16248370/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
crypto = root/'dependencies/boringssl/prefab/modules/crypto_static'
run(clang, '--target=armv7a-linux-androideabi26', '-shared', '-fPIC', '-Os', '-std=c++17', '-fno-exceptions', '-fno-rtti', '-DNO_LOG', '-Wl,-z,max-page-size=16384', '-Wl,--exclude-libs,ALL', '-I'+str(crypto/'include'), root/'adb_pairing.cpp', crypto/'libs/android.armeabi-v7a/libcrypto_static.a', '-llog', '-o', native)
unsigned = root/'unsigned.apk' 
run(tools/'aapt2.exe', 'link', '-o', unsigned, '--manifest', root/'AndroidManifest.xml', '-I', android, '-A', root/'licenses')
with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as archive:
    archive.write(dex/'classes.dex', 'classes.dex')
    archive.write(native, 'lib/armeabi-v7a/liblocalpairing.so')
    archive.write(root/'dependencies/conscrypt-2.8.0/jni/armeabi-v7a/libconscrypt_jni.so', 'lib/armeabi-v7a/libconscrypt_jni.so')
aligned = root/'aligned.apk'
run(tools/'zipalign.exe', '-f', '4', unsigned, aligned)
apk = root/'CleanYourTV-Settings-Starter.apk'
run(tools/'apksigner.bat', 'sign', '--ks', key, '--ks-key-alias', alias,
    '--ks-pass', 'env:CYTV_STORE_PASSWORD', '--key-pass', 'env:CYTV_KEY_PASSWORD', '--out', apk, aligned)
run(tools/'apksigner.bat', 'verify', '--verbose', apk)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
Path(str(apk)+'.sha256').write_text(digest+'  '+apk.name+'\n', encoding='ascii')
print('Built', apk.name, apk.stat().st_size, 'bytes; SHA256', digest)

