"""Download exact public Maven dependencies, verify pinned hashes, extract bounded paths. No device access."""
from pathlib import Path
import hashlib,json,urllib.request,zipfile
root=Path(__file__).resolve().parent;directory=root/'dependencies';directory.mkdir(exist_ok=True)
for item in json.loads((root/'dependencies.lock.json').read_text()):
 archive=directory/item['file']
 if not archive.is_file():
  data=urllib.request.urlopen(item['url'],timeout=40).read()
  if hashlib.sha256(data).hexdigest()!=item['sha256']:raise SystemExit('Dependency download hash mismatch')
  archive.write_bytes(data)
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=item['sha256']:raise SystemExit('Dependency hash mismatch: '+item['file'])
 destination=(directory/item['extract_to']).resolve();destination.mkdir(exist_ok=True)
 with zipfile.ZipFile(archive) as z:
  for info in z.infolist():
   candidate=(destination/info.filename).resolve()
   if not candidate.is_relative_to(destination):raise SystemExit('Unsafe dependency archive path')
  z.extractall(destination)
(directory/'boringssl.sha256').write_text(next(x['sha256'] for x in json.loads((root/'dependencies.lock.json').read_text()) if x['file'].startswith('boring'))+'\n')
(directory/'conscrypt-2.8.0.sha256').write_text(next(x['sha256'] for x in json.loads((root/'dependencies.lock.json').read_text()) if x['file'].startswith('conscrypt'))+'\n')
print('Pinned dependency integrity passed.')
