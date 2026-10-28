"""Optional public Flowable reference app. Never required by the faker-data core."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve().parent


def classpath(project):
    file = project / 'classpath.txt'
    if not file.is_file() or not (project / 'target/classes/demo/Fixture.class').is_file():
        raise ValueError('Build this optional Maven example first; see README.md. No global libraries are installed.')
    return str(project / 'target/classes') + os.pathsep + file.read_text(encoding='utf-8-sig').strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-dir', type=Path, default=HERE, help='optional isolated Maven project build directory')
    parser.add_argument('--java', default='java')
    sub = parser.add_subparsers(dest='action', required=True)
    for name in ['init', 'templates', 'verify', 'accept', 'purge', 'serve']:
        command = sub.add_parser(name)
        command.add_argument('--profile', type=Path, required=True, help='private connection JSON (default profile)')
        if name in {'templates', 'verify', 'accept'}:
            command.add_argument('--output', type=Path, required=True)
        if name == 'serve': command.add_argument('--port', type=int, default=18991)
        if name == 'init': command.add_argument('--confirm-schema', action='store_true')
        if name == 'purge':
            command.add_argument('--proof', type=Path, required=True)
            command.add_argument('--confirm-delete', action='store_true')
    install = sub.add_parser('install-extension')
    install.add_argument('--home', type=Path, required=True, help='same private home used by faker.py')
    args = parser.parse_args()
    try:
        java = shutil.which(args.java) if not Path(args.java).is_absolute() else args.java
        if not java: raise ValueError('Java 8+ not found; pass --java')
        cp = classpath(args.build_dir.resolve())
        if args.action == 'install-extension':
            directory = args.home.expanduser().resolve() / 'data/extensions/flowable-public-payload'
            directory.mkdir(parents=True, exist_ok=True)
            manifest = {'protocol': 'faker-field-extension/1',
                        'command': [str(Path(java).resolve()), '-cp', cp, 'demo.PayloadCodec', '${input}', '${output}'],
                        'allowedFields': {'ACT_GE_BYTEARRAY': ['BYTES_']}, 'timeoutSeconds': 60}
            with (directory / 'extension.json').open('x', encoding='utf-8') as file:
                json.dump(manifest, file, indent=2)
            print('Installed field-only codec; enable trusted extensions separately in faker launcher settings.')
            return 0
        if args.action == 'init' and not args.confirm_schema:
            raise ValueError('init requires --confirm-schema for an EMPTY loopback *_flowable_demo database')
        if args.action == 'purge' and not args.confirm_delete:
            raise ValueError('purge requires --confirm-delete for the exact document IDs in --proof')
        extra = []
        if args.action in {'templates', 'verify', 'accept'}:
            if args.output.exists(): raise ValueError('Output already exists; choose another path to retain prior evidence')
            args.output.parent.mkdir(parents=True, exist_ok=True)
            extra = [str(args.output.resolve())]
        if args.action == 'purge': extra = [str(args.proof.resolve())]
        if args.action == 'serve': extra = [str(args.port)]
        return subprocess.run([str(java), '-Xmx512m', '-cp', cp, 'demo.Fixture', args.action,
                               str(args.profile.resolve()), *extra]).returncode
    except (ValueError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
