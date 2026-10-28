"""Opt-in packaged runtime check. Private temporary home; no target writes by default.

--profiles --confirm-demo-write runs test_protocol against an explicitly prepared
dedicated empty mysql-order-schema.sql fixture, including exact batch cleanup.
Never point this option at a business database. Profiles are never printed.
"""
import argparse
import asyncio
import importlib.util
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import zipfile

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('launcher', ROOT/'faker.py')
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


async def probe(home, base):
    params = StdioServerParameters(command=sys.executable,
        args=[str(ROOT/'integrations/mcp/server.py')], env=dict(os.environ, FAKER_BASE_URL=base))
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as client:
            await client.initialize()
            tools = (await client.list_tools()).tools
            assert len(tools) == 19 and all(t.outputSchema for t in tools)
            resource = await client.read_resource('faker://rules')
            assert 'REF.OTHER_TABLE' in resource.contents[0].text
            contracts = await client.read_resource('faker://contracts')
            assert contracts.contents
    return len(tools)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--java', default='java')
    parser.add_argument('--profiles', type=Path)
    parser.add_argument('--confirm-demo-write', action='store_true')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    if bool(args.profiles) != args.confirm_demo_write:
        parser.error('Native fixture testing requires both --profiles and --confirm-demo-write')
    java, major = launcher.java_command(args.java)
    jar = args.jar.resolve()
    version = launcher.jar_info(jar)
    with zipfile.ZipFile(jar) as archive:
        import re
        prefix = 'BOOT-INF/classes/static/'
        html = archive.read(prefix+'index.html').decode('utf-8')
        assets = set(re.findall(r'(?:src|href)="/?((?:js|css)/[^"?]+)', html))
        assert assets
        for asset in assets:
            assert archive.read(prefix+asset), asset
        assert archive.read(prefix+'fonts/element-icons.f1a45d74.ttf')[:4] == b'\x00\x01\x00\x00'
        assert archive.read(prefix+'fonts/element-icons.ff18efd1.woff')[:4] == b'wOFF'
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    with tempfile.TemporaryDirectory(prefix='faker-runtime-') as folder:
        home = Path(folder)
        data = {'jar': str(jar), 'java': java, 'port': port, 'data': str(home/'data')}
        child = launcher.launch_java(home, data)
        try:
            base = 'http://127.0.0.1:'+str(port)
            tools = asyncio.run(probe(home, base))
            if args.profiles:
                env = dict(os.environ, FAKER_MCP_E2E='1', FAKER_BASE_URL=base,
                           FAKER_CONNECTIONS_FILE=str(args.profiles.resolve()),
                           FAKER_MCP_CLEANUP_CONNECTION='default')
                subprocess.run([sys.executable, '-m', 'unittest', 'discover', '-s',
                                str(ROOT/'integrations/mcp'), '-p', 'test_protocol.py', '-v'],
                               cwd=ROOT, env=env, check=True)
        finally:
            launcher.stop_owned(child)
        assert child.poll() is not None
    report = {'platform': sys.platform, 'python': sys.version.split()[0], 'javaMajor': major,
              'jar': version, 'toolCount': tools, 'bundledAssets': sorted(assets),
              'iconFontsVerified': True, 'ownedJavaStopped': True,
              'nativeMysqlLifecycle': bool(args.profiles), 'externalAiClient': False}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
