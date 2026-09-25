#!/usr/bin/env python3
"""Build against an installed PhpStorm SDK; no Gradle or network downloads required."""
import argparse
import json
import xml.etree.ElementTree as ET
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--ide', default=os.environ.get('PHPSTORM_HOME', str(Path.home() / 'Applications/PhpStorm.app/Contents')))
parser.add_argument('--ai-plugin', default=str(Path.home() / 'Library/Application Support/JetBrains/PhpStorm2026.3/plugins/ml-llm'))
args = parser.parse_args()
ide = Path(args.ide).expanduser().resolve()
java = ide / 'jbr/Contents/Home/bin'
if not java.exists():
    java = ide / 'jbr/bin'
if not (java / 'javac').exists():
    parser.error('Specify --ide with a PhpStorm installation containing JBR SDK (javac).')
build = root / 'build'
if build.exists():
    shutil.rmtree(build)
classes = build / 'classes'
classes.mkdir(parents=True)
ai_plugin = Path(args.ai_plugin).expanduser()
if not ai_plugin.exists():
    parser.error('Specify --ai-plugin with the installed JetBrains AI Assistant plugin directory.')
classpath = os.pathsep.join(str(p) for base in [ide / 'lib', ai_plugin / 'lib'] for p in base.rglob('*.jar'))
metadata = ET.parse(root / 'src/main/resources/META-INF/plugin.xml').getroot()
version = metadata.findtext('version')
name = metadata.findtext('name')
generated = build / 'generated/PluginInfo.java'
generated.parent.mkdir(parents=True)
generated.write_text('package local.codexlimits;\nfinal class PluginInfo {\n'
    + '    static final String NAME = ' + json.dumps(name) + ';\n'
    + '    static final String VERSION = ' + json.dumps(version) + ';\n'
    + '    static final String TITLE = NAME + " v" + VERSION;\n}\n', encoding='utf-8')
sources = sorted((root / 'src/main/java').rglob('*.java')) + [generated]
subprocess.run([str(java / 'javac'), '--release', '21', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), *map(str, sources)], check=True)
tests = sorted((root / 'src/test/java').rglob('*.java'))
subprocess.run([str(java / 'javac'), '--release', '21', '-encoding', 'UTF-8', '-cp', str(classes) + os.pathsep + classpath, '-d', str(build / 'tests'), *map(str, tests)], check=True)
for test in ['QuotaTest', 'CliEnvironmentTest', 'AccountAgeTest', 'TokenStatisticsTest', 'CodexAuthTest', 'ChatHistoryRecoveryTest']:
    subprocess.run([str(java / 'java'), '-ea', '-cp', os.pathsep.join([str(build / 'tests'), str(classes), classpath]), 'local.codexlimits.' + test], check=True)
shutil.copytree(root / 'src/main/resources', classes, dirs_exist_ok=True)
jar = build / 'codex-limit-status.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
    for file in sorted(classes.rglob('*')):
        if file.is_file():
            z.write(file, file.relative_to(classes))
dist = root / 'dist'
dist.mkdir(exist_ok=True)
archive = dist / f'uis-codex-limit-{version}.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(jar, 'codex-limit-status/lib/codex-limit-status.jar')
print(archive)
