# Unified launcher and MCP setup

[中文](launcher.md)

Current source includes `faker.py` for the Java UI and Python stdio MCP adapter. It does not modify AI client settings or global Python packages. Use a matching current JAR; older Releases may lack contracts v1.

## Install

Install Java 8+ and Python 3.10+. From the project root (use `python3` on macOS/Linux):

```sh
python faker.py setup --jar /absolute/path/to/faker-data-0.1.1.jar
python faker.py doctor
```

Source users first run `mvnw.cmd -B package` (Windows) or `./mvnw -B package`; choose the JAR in `target/`. Initial setup downloads isolated open-source MCP dependencies. `--ui-only` skips them; doctor will report the missing adapter. UI-only users can also use `java -jar` without Python.

Default private directory: `~/.faker-data`.

| File | Purpose |
| --- | --- |
| `settings.json` | Absolute JAR/Java paths, port, data and profile locations |
| `connections.json` | Private database profiles; replace CHANGE_ME locally |
| `venv/` | Isolated dependencies; recreate on another machine |
| `data/` | Schemes, tasks, batch journals and extensions |
| `java.log` | Private service log; redact before sharing |

Never publish these files or send credentials to the AI. Windows inherits directory permissions; use a private user directory. New POSIX home/config permissions are 0700/0600.

Use separate homes/ports for isolated instances; repeat the same home on every command:

```sh
python faker.py --home /private/faker-demo setup --jar /path/current.jar --port 18085 --java /path/to/java
python faker.py --home /private/faker-demo start
```

Setup never overwrites profiles or silently changes different settings. Rerun to repair dependencies. To change port/JAR location, deliberately edit settings.json or choose a new home. Optional `javaArgs` accepts Spring arguments such as `--faker.extensions.enabled=true` after reviewing trusted code. Bind address, port, root context and data directory use explicit settings. Memory cache is the default; Redis is unnecessary. Heap defaults to 64–512 MiB; use your own Java command and attach for custom heap settings.

## UI

```sh
python faker.py start
```

Open `http://127.0.0.1:8085/`. Ctrl+C stops only this command's child. Busy ports fail without stopping existing services. Choose another home/port, or explicitly check an existing service with `start --attach`.

## AI client

```sh
python faker.py config
```

Copy JSON into a stdio MCP client. For Codex TOML use `python faker.py config --format toml`. Absolute paths are machine-specific and contain no profile values. Enable/reconnect in your client; the launcher does not modify client settings.

Default config starts an owned Java child with MCP. Standard output carries only MCP protocol; diagnostics use stderr/private java.log. On stdin EOF, the adapter exits and the launcher stops its Java child. **Only one client can own a port.** Do not run `start` together with default MCP mode.

Keep the client connected until asynchronous Java generation tasks finish. Disconnecting early may interrupt work; reconnect and inspect the original request/batch instead of blindly resubmitting. For long tasks or multiple clients, prefer an explicitly started service with attach.

To share an explicitly started service:

```sh
python faker.py start
# Keep this terminal open. In another terminal:
python faker.py config --attach
```

Attach checks contracts v1 at the local root URL and never stops that service. This is a capability check, not proof of an identical Git revision. The existing service's data directory and extension settings apply. Legacy `/faker` paths are unsupported here. [Manual MCP configuration](mcp.en.md) remains available for custom environment variables.

## Diagnostics and migration

Doctor checks Python, Java, selected JAR version/contracts, fixed MCP version, actual adapter import, port and profile completeness. It does not connect to target databases or generate/delete rows. Configured profiles do not prove database permissions, business validity or cleanup privileges.

On another machine, copy source and matching JAR, transfer private profiles/data securely, install Java/Python, recreate venv with setup, correct settings paths and regenerate client config. Do not copy the old venv. Logs/SQLite state are private and excluded from public artifacts.
