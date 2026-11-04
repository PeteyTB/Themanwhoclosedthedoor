#!/usr/bin/env python3
"""Use the session proxy without writing it or credentials into Gradle configuration."""
import os
import pathlib
import sys
import urllib.parse

root = pathlib.Path(__file__).resolve().parent.parent
os.chdir(root)
jdk = pathlib.Path('/workspace/.tools/jdk-21.0.8+9')
os.environ.setdefault('GRADLE_USER_HOME', '/workspace/.tools/gradle-home')
if not os.environ.get('JAVA_HOME') and (jdk / 'bin/javac').exists():
    os.environ['JAVA_HOME'] = str(jdk)
arguments = [str(root / 'gradlew')]
trust_store = pathlib.Path('/etc/ssl/certs/java/cacerts')
if trust_store.exists() and 'javax.net.ssl.trustStore' not in (os.environ.get('JAVA_TOOL_OPTIONS', '') + os.environ.get('GRADLE_OPTS', '')):
    arguments += [f'-Djavax.net.ssl.trustStore={trust_store}']
for scheme in ('http', 'https'):
    proxy = os.environ.get(scheme.upper() + '_PROXY')
    if proxy:
        parsed = urllib.parse.urlsplit(proxy)
        if parsed.hostname:
            arguments += [f'-D{scheme}.proxyHost={parsed.hostname}', f'-D{scheme}.proxyPort={parsed.port or 80}']
arguments += ['-Dhttp.nonProxyHosts=localhost|127.*|[::1]', *sys.argv[1:]]
os.execv(arguments[0], arguments)
