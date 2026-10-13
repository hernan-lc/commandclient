#!/usr/bin/env python3
"""Validate intentional plugin packaging without relying on a server or Gradle internals."""
import pathlib, sys, zipfile, collections, json
path=pathlib.Path(sys.argv[1]);manifest=json.loads((pathlib.Path(__file__).resolve().parents[1]/'versions.json').read_text())
with zipfile.ZipFile(path) as jar:
    names=jar.namelist();counts=collections.Counter(names)
    assert not [name for name,count in counts.items() if count>1], 'Duplicate ZIP entries'
    assert 'plugin.yml' in names and 'config.yml' in names
    for required in ['com/commandapi/server/CommandApiPlugin.class','com/commandapi/protocol/BridgeProtocol.class','com/commandapi/api/CommandScheduler.class']:
        assert required in names, 'Missing core class: '+required
    assert not any(name.startswith(('org/bukkit/','net/minecraft/','net/fabricmc/','com/google/gson/','org/junit/','org/mockito/')) for name in names),'Server/test dependencies must not be bundled'
    plugin=jar.read('plugin.yml').decode();assert "version: '"+manifest['modVersion']+"'" in plugin
    assert "api-version: '1.21'" in plugin and 'default: false' in plugin
    assert int.from_bytes(jar.read('com/commandapi/server/CommandApiPlugin.class')[6:8],'big')==65,'Plugin requires Java 21 bytecode'
    assert 'token:' not in plugin,'Never place credentials in metadata'
print('OK: standalone plugin packaging, metadata, Java level, shared core, and dependency exclusions')
