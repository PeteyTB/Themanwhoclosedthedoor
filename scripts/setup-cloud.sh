#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
import hashlib
import pathlib
import platform
import tarfile
import urllib.request

if platform.system() != 'Linux' or platform.machine() != 'x86_64':
    raise SystemExit('Install a Java 21 JDK for this platform, then use ./gradlew build.')
tools = pathlib.Path('/workspace/.tools')
tools.mkdir(exist_ok=True)
jdk = tools / 'jdk-21.0.8+9'
if not (jdk / 'bin/javac').exists():
    name = 'OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz'
    archive = tools / name
    if not archive.exists():
        urllib.request.urlretrieve('https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/' + name, archive)
    # Published Adoptium release checksum, independently checked during initial setup.
    expected = 'f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219'
    if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
        raise SystemExit('JDK checksum mismatch; remove the damaged archive before retrying.')
    with tarfile.open(archive) as bundle:
        bundle.extractall(tools, filter='data')
print('Java 21 development kit available:', jdk)
PY
bash scripts/test-core.sh
python3 scripts/gradle-cloud.py --no-daemon build
