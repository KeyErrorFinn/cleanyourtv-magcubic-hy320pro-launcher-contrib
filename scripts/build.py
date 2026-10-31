"""Build public editions with an explicit private signing key; never installs anything."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

def run(*args, **kwargs):
    subprocess.run([str(x) for x in args], check=True, **kwargs)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--edition', choices=['basic', 'advanced', 'both'], default='both')
    parser.add_argument('--keystore', type=Path, required=True)
    parser.add_argument('--alias', required=True)
    args = parser.parse_args()
    key = args.keystore.resolve()
    if not key.is_file():
        raise SystemExit('Signing key missing. Create your own key using the documented keytool procedure.')
    for name in ['CYTV_STORE_PASSWORD', 'CYTV_KEY_PASSWORD', 'ANDROID_HOME']:
        if not os.environ.get(name):
            raise SystemExit('Set '+name+' locally; never commit passwords or keys.')
    certificate = subprocess.check_output(['keytool', '-exportcert', '-keystore', str(key),
        '-alias', args.alias, '-storepass:env', 'CYTV_STORE_PASSWORD'])
    pin = hashlib.sha256(certificate).hexdigest()
    env = dict(os.environ, CYTV_KEYSTORE=str(key), CYTV_KEY_ALIAS=args.alias, CYTV_CERT_SHA256=pin)
    editions = ['basic', 'advanced'] if args.edition == 'both' else [args.edition]
    tasks = [task+edition.title()+'Release'+suffix for edition in editions
             for task, suffix in [('test','UnitTest'), ('lint',''), ('assemble','')]]
    wrapper = ROOT/'launcher'/('gradlew.bat' if os.name == 'nt' else 'gradlew')
    run(wrapper, '--no-daemon', '-PreleaseCertificateSha256='+pin, *tasks, '--console=plain',
        cwd=ROOT/'launcher', env=env)
    dist = ROOT/'dist'; dist.mkdir(exist_ok=True)
    artifacts = {}
    def collect(source, name):
        target=dist/name; shutil.copy2(source,target)
        artifacts[name]=hashlib.sha256(target.read_bytes()).hexdigest()
        Path(str(target)+'.sha256').write_text(artifacts[name]+'  '+name+'\n',encoding='ascii')
    for edition in editions:
        collect(ROOT/f'launcher/app/build/outputs/apk/{edition}/release/app-{edition}-release.apk',
                f'CleanYourTV-HY320Pro-{edition.title()}.apk')
    if 'advanced' in editions:
        run(sys.executable, ROOT/'settings-starter/build.py', env=env)
        collect(ROOT/'settings-starter/CleanYourTV-Settings-Starter.apk','CleanYourTV-Settings-Starter.apk')
    (dist/'signing-certificate.sha256').write_text(pin+'\n',encoding='ascii')
    (dist/'release.json').write_text(json.dumps({'version':'1.0.0','certificate_sha256':pin,
        'firmware':'Projector_20260713.153921','artifacts':artifacts},indent=2)+'\n')
    print('Build complete. No device was connected or modified. Artifacts: '+str(dist))

if __name__ == '__main__':
    main()
