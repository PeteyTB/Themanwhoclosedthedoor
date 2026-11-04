#!/usr/bin/env python3
"""Build the JDK-only attachment controller and agent separately from Minecraft."""
import os
import pathlib
import shutil
import subprocess

root = pathlib.Path(__file__).resolve().parent.parent
os.chdir(root)
jdk = pathlib.Path(os.environ.get('JAVA_HOME', '/workspace/.tools/jdk-21.0.8+9'))
classes = root / 'build/attach/classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
sources = sorted(str(p) for p in (root / 'attach/src').rglob('*.java'))
subprocess.run([str(jdk / 'bin/javac'), '--release', '21', '--add-modules', 'jdk.attach', '-d', str(classes), *sources], check=True)
subprocess.run([str(jdk / 'bin/jar'), '--create', '--file', str(root / 'build/attach/levrn-agent.jar'), '--manifest', str(root / 'attach/MANIFEST.MF'), '-C', str(classes), '.'], check=True)
print('Built build/attach/levrn-agent.jar')
