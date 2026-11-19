"""Check one final platform JAR before distributing it. Python 3.11+; no extra packages."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = 'https://github.com/BrainLeech198/mc_mod.yezong_town_super_toolkit'


def verify(platform, directory):
    properties = dict(line.split('=', 1) for line in
                      (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                      if line and not line.startswith('#') and '=' in line)
    version, game = properties['mod_version'], properties['minecraft_version']
    jar = directory / f'yzt_super-{version}-mc{game}-{platform}.jar'
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), 'Duplicate ZIP entries'
        blocked_dirs = ('.git/', '.github/', '.gradle/', 'run/', 'world/', 'logs/',
                        'config/', 'docs/', 'scripts/', 'src/')
        data_files = {'users.json', 'mail.json', 'lastseen.json', 'risk.json', 'titles.json'}
        for name in names:
            lower = name.lower()
            assert not lower.startswith(blocked_dirs), f'Development/runtime directory: {name}'
            assert Path(lower).name not in data_files, f'Player data in package: {name}'
            assert not lower.endswith(('.log', '.tmp', '.bak')), f'Runtime file: {name}'
            assert not Path(lower).name.startswith('.env'), f'Environment file: {name}'
            assert '/selftest/' not in lower, f'Test class in package: {name}'
        licence = archive.read('META-INF/LICENSE-yzt_super.txt').decode('utf-8').splitlines()
        assert licence == (ROOT / 'LICENSE').read_text(encoding='utf-8').splitlines()
        assert 'com/yztc/auth/security/PasswordHasher.class' in names
        classes = [name for name in names if name.endswith('.class')]
        assert classes
        assert all(struct.unpack('>H', archive.read(name)[6:8])[0] == 61 for name in classes), 'Java target mismatch'
        png = archive.read('logo.png')
        assert png[:8] == b'\x89PNG\r\n\x1a\n' and struct.unpack('>II', png[16:24]) == (400, 400), 'Icon must be 400x400 PNG'
        for language in ('zh_cn', 'en_us'):
            json.loads(archive.read(f'assets/yzt_super/lang/{language}.json'))
        if platform == 'forge':
            metadata = tomllib.loads(archive.read('META-INF/mods.toml').decode('utf-8'))
            mod = metadata['mods'][0]
            assert mod['version'] == version and mod['modId'] == properties['mod_id']
            assert mod['authors'] == properties['mod_authors']
            assert mod['description'].strip() == properties['mod_description']
            assert metadata['license'] == properties['mod_license']
            assert metadata['issueTrackerURL'] == REPOSITORY + '/issues'
            assert mod['displayURL'] == REPOSITORY and mod['logoFile'] == 'logo.png'
            assert f'Implementation-Version: {version}' in archive.read('META-INF/MANIFEST.MF').decode('utf-8')
            assert 'fabric.mod.json' not in names
        else:
            metadata = json.loads(archive.read('fabric.mod.json'))
            assert metadata['version'] == version and metadata['id'] == properties['mod_id']
            assert metadata['authors'] == [name.strip() for name in properties['mod_authors'].split(',')]
            assert metadata['description'] == properties['mod_description']
            assert metadata['license'] == properties['mod_license']
            assert metadata['environment'] == 'server' and metadata['icon'] == 'logo.png'
            assert metadata['depends']['minecraft'] == '=' + game
            assert metadata['contact']['sources'] == REPOSITORY
            assert metadata['contact']['issues'] == REPOSITORY + '/issues'
            assert 'META-INF/mods.toml' not in names and 'yzt_super.refmap.json' in names
    print(json.dumps({'file': jar.name, 'bytes': jar.stat().st_size,
                      'classes': len(classes), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform', choices=('forge', 'fabric'), required=True)
    parser.add_argument('--directory', type=Path, required=True)
    args = parser.parse_args()
    verify(args.platform, args.directory)
