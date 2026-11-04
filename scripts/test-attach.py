#!/usr/bin/env python3
"""Real Attach API integration checks against owned JVM fixtures, not Minecraft claims."""
import os
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parent.parent
jdk = pathlib.Path(os.environ.get('JAVA_HOME', '/workspace/.tools/jdk-21.0.8+9'))
java = str(jdk / 'bin/java')
agent = root / 'build/attach/levrn-agent.jar'
version = next(line.split('=', 1)[1].strip() for line in (root / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
client = root / f'build/libs/levrn-{version}.jar'
with tempfile.TemporaryDirectory(prefix='levrn-attach-test-') as out:
    subprocess.run([str(jdk / 'bin/javac'), '--release', '21', '-d', out, str(root / 'attach/test/PlainTarget.java')], check=True)
    for extra, expected in [([], 'no Fabric Loader'), (['-XX:-EnableDynamicAgentLoading'], 'Dynamic agent loading is not enabled')]:
        target = subprocess.Popen([java, *extra, '-cp', out, 'PlainTarget'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            assert target.stdout.readline().strip() == 'READY'
            result = subprocess.run([java, '--add-modules', 'jdk.attach', '-jar', str(agent), '--attach', str(target.pid), str(client)], capture_output=True, text=True, timeout=35)
            assert result.returncode != 0, 'Unsupported target must not report success'
            assert expected in result.stderr, result.stderr
            assert target.poll() is None, 'Target terminated unexpectedly'
            stdout, stderr = target.communicate('\n', timeout=10)
            assert target.returncode == 0 and 'ALIVE' in stdout
            print('PASS: attachment rejection preserves live target:', expected)
        finally:
            if target.poll() is None:
                target.kill()
                target.communicate(timeout=10)
