#!/usr/bin/env python3
"""Verify every supplied variant against authoritative server compatibility metadata."""
import pathlib, sys, zipfile, collections, json
manifest=json.loads((pathlib.Path(__file__).resolve().parents[1]/'versions.json').read_text())

def verify(path):
    target=next((name for name in manifest['serverTargets'] if path.name == f"commandapi-server-{name}-{manifest['modVersion']}.jar"),None)
    assert target, f'Unknown or stale server artifact: {path.name}'
    spec=manifest['serverTargets'][target]
    with zipfile.ZipFile(path) as jar:
        names=jar.namelist();counts=collections.Counter(names)
        assert not [name for name,count in counts.items() if count>1], 'Duplicate ZIP entries'
        assert 'plugin.yml' in names and 'config.yml' in names
        for required in ['com/commandapi/server/CommandApiPlugin.class','com/commandapi/protocol/BridgeProtocol.class','com/commandapi/api/CommandScheduler.class']:
            assert required in names, 'Missing core class: '+required
        assert not any(name.startswith(('org/bukkit/','net/minecraft/','net/fabricmc/','com/google/gson/','org/junit/','org/mockito/')) for name in names),'Server/test dependencies must not be bundled'
        plugin=jar.read('plugin.yml').decode()
        assert f"version: '{manifest['modVersion']}'" in plugin
        assert f"api-version: '{spec['apiVersion']}'" in plugin and 'default: false' in plugin
        assert '${' not in plugin, 'Unexpanded metadata'
        assert int.from_bytes(jar.read('com/commandapi/server/CommandApiPlugin.class')[6:8],'big')==44+spec['java'], 'Incorrect plugin Java bytecode'
        for name in names:
            if name.endswith('.class'):
                assert int.from_bytes(jar.read(name)[6:8],'big') <= 44+spec['java'], f'{name} exceeds variant Java baseline'
        assert 'token:' not in plugin,'Never place credentials in metadata'
    print(f"OK: {target} API {spec['apiVersion']}, Java {spec['java']}, all classes and dependency exclusions")
    return target

if __name__ == '__main__':
    assert len(sys.argv)>1, 'Supply plugin JAR paths'
    seen=set()
    for arg in sys.argv[1:]:
        target=verify(pathlib.Path(arg))
        assert target not in seen, 'Duplicate server variant'
        seen.add(target)
    assert seen == set(manifest['serverTargets']), 'Missing required server variant'
