#!/usr/bin/env python3
"""Build the JDK-only attachment controller and agent separately from Minecraft."""
import os
import pathlib
import shutil
import subprocess

root = pathlib.Path(__file__).resolve().parent.parent
os.chdir(root)

def find_jdk():
    """Find a usable JDK: JAVA_HOME env var, then Gradle's JDK, then PATH."""
    # 1. Explicit JAVA_HOME
    env_home = os.environ.get('JAVA_HOME')
    if env_home and (pathlib.Path(env_home) / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')).exists():
        return pathlib.Path(env_home)
    # 2. Ask Gradle for its JDK location (works when gradlew is available)
    gradlew = root / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    if gradlew.exists():
        try:
            result = subprocess.run(
                [str(gradlew), '-q', '--no-daemon', 'javaToolchains'],
                capture_output=True, text=True, timeout=60, cwd=root
            )
            for line in result.stdout.splitlines():
                line = line.strip()
                if line.startswith('|') and 'JDK' not in line:
                    candidate = line.strip('| ').strip()
                    p = pathlib.Path(candidate)
                    if (p / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')).exists():
                        return p
        except Exception:
            pass
    # 3. Find javac on PATH and derive JAVA_HOME from it
    javac = shutil.which('javac')
    if javac:
        # javac is typically at <jdk>/bin/javac
        return pathlib.Path(javac).resolve().parent.parent
    # 4. Fallback to cloud build path
    fallback = pathlib.Path('/workspace/.tools/jdk-21.0.8+9')
    if fallback.exists():
        return fallback
    raise SystemExit(
        'Cannot find a JDK with javac. Set JAVA_HOME to your JDK 21+ directory,\n'
        'or make sure javac is on your PATH.'
    )

jdk = find_jdk()
classes = root / 'build/attach/classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
sources = sorted(str(p) for p in (root / 'attach/src').rglob('*.java'))
subprocess.run([str(jdk / 'bin/javac'), '--release', '21', '--add-modules', 'jdk.attach', '-d', str(classes), *sources], check=True)
subprocess.run([str(jdk / 'bin/jar'), '--create', '--file', str(root / 'build/attach/levrn-agent.jar'), '--manifest', str(root / 'attach/MANIFEST.MF'), '-C', str(classes), '.'], check=True)
print('Built build/attach/levrn-agent.jar')
