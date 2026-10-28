#!/usr/bin/env python3
"""Local launcher. No third-party imports until the isolated MCP child starts."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import venv
import zipfile

ROOT = Path(__file__).resolve().parent
MCP_VERSION = "1.29.0"


class LaunchError(Exception):
    pass


def log(message):
    print(message, file=sys.stderr, flush=True)


def python_in(home):
    return home / "venv" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")


def create_private(path, value):
    """Exclusive creation, never overwrite local credentials/settings."""
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        fd = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        return
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def jar_info(path):
    try:
        with zipfile.ZipFile(path) as jar:
            contract = json.loads(jar.read("BOOT-INF/classes/contracts/manifest-v1.json"))
            if not isinstance(contract, dict) or contract.get("format") != "faker-data.contracts" or contract.get("formatVersion") != 1:
                raise ValueError()
            manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8")
            version = re.search(r"(?m)^Implementation-Version:\s*([^\r\n]+)", manifest)
            return {"version": version.group(1) if version else "unknown", "contracts": 1}
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        raise LaunchError("JAR missing compatible contracts v1. Build current source or obtain a matching bundle; an older Release may not support these tools.") from None


def java_command(value):
    command = shutil.which(value) if not Path(value).is_absolute() else value
    if not command:
        raise LaunchError("Java not found. Install Java 8+ or pass setup --java /absolute/path/to/java.")
    try:
        result = subprocess.run([command, "-version"], capture_output=True, text=True, timeout=15)
        match = re.search(r'version\s+"?(\d+)(?:\.(\d+))?', result.stdout + result.stderr)
        major = int(match.group(2) if match.group(1) == "1" else match.group(1)) if match else 0
        if result.returncode or major < 8:
            raise ValueError()
    except (OSError, ValueError, subprocess.TimeoutExpired):
        raise LaunchError("Cannot verify Java 8+. Check setup --java executable.") from None
    return str(Path(command).resolve()), major


def settings(home):
    try:
        data = json.loads((home / "settings.json").read_text(encoding="utf-8-sig"))
        if (data.get("formatVersion") != 1 or type(data.get("port")) is not int
                or not 1 <= data["port"] <= 65535
                or not all(isinstance(data.get(k), str) and data[k] for k in ("jar", "java", "data", "connections"))):
            raise ValueError()
        if not isinstance(data.get("javaArgs", []), list) or not all(isinstance(v, str) for v in data.get("javaArgs", [])):
            raise ValueError()
        return data
    except (OSError, ValueError, AttributeError):
        raise LaunchError("Missing/invalid settings.json. Run python faker.py setup --jar /absolute/path/to/current.jar (use the same --home).") from None


def port_busy(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as connection:
        connection.settimeout(0.5)
        return connection.connect_ex(("127.0.0.1", port)) == 0


def require_free_port(port):
    if port_busy(port):
        raise LaunchError(f"Port {port} is occupied; nothing was stopped. Choose setup --port another_port in a separate --home, or use --attach to explicitly connect to an existing compatible service.")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def compatible_service(port):
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(f"http://127.0.0.1:{port}/contracts", timeout=2) as response:
            value = json.loads(response.read(65537))
        return (value.get("format") == "faker-data.contracts" and value.get("formatVersion") == 1
                and "scheme-v1.schema.json" in value.get("files", []))
    except (OSError, ValueError, AttributeError, urllib.error.URLError):
        return False


def check_adapter(home):
    python = python_in(home)
    if not python.is_file():
        raise LaunchError("MCP environment missing. Run setup without --ui-only to install isolated dependencies.")
    # Check the real adapter import with warnings promoted to errors, not only package presence.
    code = ("import importlib.metadata as m,sys; "
            f"assert m.version('mcp') == {MCP_VERSION!r}; "
            "sys.path.insert(0,sys.argv[1]); import server")
    result = subprocess.run([str(python), "-W", "error", "-c", code,
                             str(ROOT / "integrations/mcp")], capture_output=True, timeout=30)
    if result.returncode:
        raise LaunchError("MCP dependency/import check failed. Rerun setup to repair the private environment; details are not printed to avoid exposing local environment values.")


def stop_owned(child):
    if child is None or child.poll() is not None:
        return
    child.terminate()
    try:
        child.wait(timeout=10)
    except subprocess.TimeoutExpired:
        child.kill()
        child.wait(timeout=10)


def launch_java(home, data):
    jar_info(Path(data["jar"]))
    java, _ = java_command(data["java"])
    require_free_port(data["port"])
    # Explicit args take precedence over unrelated SERVER_PORT/DATA_DIR from the caller.
    args = [java, "-Xms64m", "-Xmx512m", "-jar", data["jar"], "--faker.cache.backend=memory"]
    args += data.get("javaArgs", [])
    args += ["--server.address=127.0.0.1",
            f"--server.port={data['port']}", "--server.servlet.context-path=/",
            "--faker.data-dir=" + data["data"]]
    with (home / "java.log").open("ab") as output:
        child = subprocess.Popen(args, cwd=home, stdin=subprocess.DEVNULL, stdout=output,
                                 stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic() + 60
        while child.poll() is None and time.monotonic() < deadline:
            if compatible_service(data["port"]):
                # A port race must not adopt an unrelated process after our child already exited.
                if child.poll() is None:
                    return child
            time.sleep(0.25)
        raise LaunchError(f"Java did not become ready. Inspect private log {home / 'java.log'}; check port and JAR.")
    except BaseException:
        stop_owned(child)
        raise


def client_config(home, attach=False):
    args = [str(ROOT / "faker.py"), "--home", str(home.resolve()), "mcp"]
    if attach:
        args.append("--attach")
    return {"mcpServers": {"faker-data": {"command": sys.executable, "args": args}}}


def setup(args, home):
    jar = Path(args.jar).expanduser().resolve()
    info = jar_info(jar)
    java, major = java_command(args.java)
    home.mkdir(parents=True, exist_ok=True)
    if os.name != "nt":
        home.chmod(0o700)
    data = {"formatVersion": 1, "jar": str(jar), "java": java, "port": args.port,
            "data": str(home / "data"), "connections": str(home / "connections.json"), "javaArgs": []}
    if (home / "settings.json").exists():
        current = settings(home)
        if any(current.get(key) != value for key, value in data.items() if key != "javaArgs"):
            raise LaunchError("This --home already has different settings. Keep it unchanged and use a new --home (or edit settings.json deliberately).")
    create_private(home / "settings.json", data)
    create_private(home / "connections.json", {"default": {"type": "MYSQL",
                   "url": "jdbc:mysql://127.0.0.1:3306/your_test_database", "username": "CHANGE_ME", "password": "CHANGE_ME"}})
    if not args.ui_only:
        if not python_in(home).is_file():
            venv.EnvBuilder(with_pip=True).create(home / "venv")
        result = subprocess.run([str(python_in(home)), "-m", "pip", "install", "-r",
                                 str(ROOT / "integrations/mcp/requirements.txt")], stdout=sys.stderr)
        if result.returncode:
            raise LaunchError("Dependency installation failed; rerun setup after checking network/package access. No global packages were changed.")
        check_adapter(home)
    log(f"Setup ready: Java {major}, JAR {info['version']}, contracts v1. Private home: {home}")
    log("Edit connections.json locally; do not send credentials to your AI. Next: start / config / doctor.")


def run(args, home):
    if args.command == "setup":
        return setup(args, home)
    if args.command == "config":
        settings(home)
        config = client_config(home, args.attach)
        if args.format == "json":
            print(json.dumps(config, ensure_ascii=False, indent=2))
        else:
            server = config["mcpServers"]["faker-data"]
            # JSON strings and arrays are valid TOML basic strings/arrays here.
            print("[mcp_servers.faker-data]\ncommand = " + json.dumps(server["command"], ensure_ascii=False)
                  + "\nargs = " + json.dumps(server["args"], ensure_ascii=False))
        return
    data = settings(home)
    if args.command == "doctor":
        info = jar_info(Path(data["jar"]))
        _, major = java_command(data["java"])
        check_adapter(home)
        busy = port_busy(data["port"])
        compatible = compatible_service(data["port"]) if busy else False
        if busy and not compatible:
            raise LaunchError(f"Port {data['port']} occupied by an incompatible service. Nothing was changed.")
        try:
            profiles = json.loads(Path(data["connections"]).read_text(encoding="utf-8-sig"))
            ready = isinstance(profiles, dict) and bool(profiles) and all(
                isinstance(p, dict) and p.get("type") in {"MYSQL", "ORACLE", "DM"}
                and all(isinstance(p.get(k), str) and p[k] and "CHANGE_ME" not in p[k]
                        for k in ("url", "username", "password")) for p in profiles.values())
        except (OSError, ValueError):
            ready = False
        log(f"Python {sys.version.split()[0]}; Java {major}; selected JAR {info['version']}; MCP {MCP_VERSION}; contracts v1")
        log(f"Service: {'compatible (exact build not verified)' if compatible else 'stopped, port available'}")
        log("Connections: " + ("configured locally (not connected/tested)" if ready else "edit private connections.json before database tools"))
        return
    if args.command == "mcp":
        check_adapter(home)
    java = adapter = None
    try:
        if args.attach:
            if not compatible_service(data["port"]):
                raise LaunchError("--attach requires a running compatible service at the configured root URL; /faker and older Release JARs are not supported by this launcher.")
            log("Using existing Java service; it will not be stopped on exit. Its own data/extension settings apply.")
        else:
            java = launch_java(home, data)
        log(f"faker-data ready: http://127.0.0.1:{data['port']}/")
        if args.command == "start":
            if java is None:
                return
            code = java.wait()
        else:
            env = dict(os.environ, FAKER_BASE_URL=f"http://127.0.0.1:{data['port']}",
                       FAKER_CONNECTIONS_FILE=data["connections"])
            adapter = subprocess.Popen([str(python_in(home)), str(ROOT / "integrations/mcp/server.py")],
                                       cwd=ROOT, env=env)
            code = adapter.wait()
        if code:
            raise LaunchError(f"Owned {args.command} process exited with code {code}. Check private logs.")
    finally:
        stop_owned(adapter)
        stop_owned(java)


def interrupt(*_):
    raise KeyboardInterrupt


def main():
    if sys.version_info < (3, 10):
        log("Python 3.10+ required.")
        return 2
    parser = argparse.ArgumentParser(description="faker-data local UI + MCP launcher (no global dependency changes).")
    parser.add_argument("--home", type=Path, default=Path.home() / ".faker-data")
    commands = parser.add_subparsers(dest="command", required=True)
    install = commands.add_parser("setup", help="create private settings/venv; requires network for MCP dependencies")
    install.add_argument("--jar", required=True)
    install.add_argument("--java", default="java")
    install.add_argument("--port", type=int, choices=range(1, 65536), metavar="PORT", default=8085)
    install.add_argument("--ui-only", action="store_true", help="skip Python MCP dependency installation")
    commands.add_parser("doctor", help="read-only dependency, JAR and port checks; no target database connection")
    config = commands.add_parser("config", help="print config for manual client installation; never edit client settings")
    config.add_argument("--format", choices=["json", "toml"], default="json")
    for command in (config, commands.add_parser("start", help="run UI Java service until Ctrl+C"),
                    commands.add_parser("mcp", help="stdio adapter; own Java lifecycle unless --attach")):
        command.add_argument("--attach", action="store_true", help="explicitly reuse compatible existing Java, never stop it")
    args = parser.parse_args()
    # Client shutdown via SIGTERM must run the same owned-child cleanup as Ctrl+C.
    signal.signal(signal.SIGTERM, interrupt)
    try:
        run(args, args.home.expanduser().resolve())
        return 0
    except KeyboardInterrupt:
        return 130
    except (LaunchError, OSError, subprocess.SubprocessError) as error:
        # OS exceptions may include local paths, but never dump settings/profile JSON.
        log(str(error))
        return 2


if __name__ == "__main__":
    sys.exit(main())
