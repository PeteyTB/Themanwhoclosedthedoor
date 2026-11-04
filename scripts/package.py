#!/usr/bin/env python3
"""Package a self-contained Windows launcher with the client and official Fabric components."""
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import urllib.request
import zipfile

root = pathlib.Path(__file__).resolve().parent.parent
os.chdir(root)
version = next(line.split('=', 1)[1].strip() for line in (root / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
client = root / f'build/libs/levrn-{version}.jar'
if not client.is_file():
    raise SystemExit('Run the full Gradle build first; no client JAR found.')
with zipfile.ZipFile(client) as jar:
    metadata = json.loads(jar.read('fabric.mod.json'))
    assert metadata['id'] == 'levrn' and metadata['depends']['minecraft'] == '=1.21.11'
    assert 'dev/levrn/client/LevrnClient.class' in jar.namelist()
subprocess.run([sys.executable, 'scripts/build-attach.py'], check=True)
payload = root / 'launcher/payload'
payload.mkdir(parents=True, exist_ok=True)
shutil.copyfile(client, payload / 'levrn-client.jar')
shutil.copyfile(root / 'build/attach/levrn-agent.jar', payload / 'levrn-agent.jar')
dependencies = json.loads((root / 'launcher/dependencies.json').read_text())
for name, spec in dependencies.items():
    target = payload / name
    if not target.exists():
        with urllib.request.urlopen(spec['url'], timeout=60) as response:
            target.write_bytes(response.read())
    if hashlib.sha256(target.read_bytes()).hexdigest() != spec['sha256']:
        raise SystemExit(f'Checksum mismatch: {name}; remove the damaged payload file and retry.')
notice = ('Levrn alpha. All rights reserved for original project code.\n'
          'Includes Fabric Installer (Apache-2.0), Fabric API (Apache-2.0), Gradle-built Levrn client,\n'
          'and the Go runtime (BSD-3-Clause). No Minecraft game files or third-party client code bundled.\n'
          'Source: https://github.com/FabricMC/fabric-installer and https://github.com/FabricMC/fabric\n'
          'Licenses are retained in the bundled JARs; see THIRD_PARTY.md in the source distribution.\n')
(payload / 'NOTICE.txt').write_text(notice)
go = os.environ.get('LEVRN_GO', '/workspace/.tools/go/bin/go')
if not pathlib.Path(go).exists():
    go = shutil.which('go') or go
go_root = subprocess.check_output([go, 'env', 'GOROOT'], text=True).strip()
shutil.copyfile(pathlib.Path(go_root) / 'LICENSE', payload / 'GO-LICENSE.txt')
checksums = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(payload.iterdir()) if p.name != 'checksums.json'}
(payload / 'checksums.json').write_text(json.dumps(checksums, sort_keys=True, indent=2))
env = {**os.environ, 'GOCACHE': str(root / 'build/go-cache'), 'GOTOOLCHAIN': 'local', 'CGO_ENABLED': '0'}
subprocess.run([go, 'test', './...'], cwd=root / 'launcher', env=env, check=True)
out = root / 'build/dist'
out.mkdir(parents=True, exist_ok=True)
subprocess.run([go, 'build', '-trimpath', f'-ldflags=-s -w -X main.clientVersion={version}', '-o', str(out / 'Levrn.exe'), '.'], cwd=root / 'launcher', env={**env, 'GOOS': 'windows', 'GOARCH': 'amd64'}, check=True)
subprocess.run([go, 'build', '-trimpath', f'-ldflags=-X main.clientVersion={version}', '-o', str(out / 'levrn-linux'), '.'], cwd=root / 'launcher', env={**env, 'GOOS': 'linux', 'GOARCH': 'amd64'}, check=True)
print('Built build/dist/Levrn.exe (Windows x64) and build/dist/levrn-linux (test host)')
