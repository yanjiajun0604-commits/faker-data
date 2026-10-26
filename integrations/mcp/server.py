"""stdio MCP adapter. Generation stays in the existing Java GEN_DATA engine."""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
from typing import Any
import urllib.error
import urllib.parse
import urllib.request

from mcp.server.fastmcp import FastMCP
from mcp.server.fastmcp.exceptions import ToolError
from mcp.types import ToolAnnotations


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class JavaAPI:
    def __init__(self, base_url: str, connections_file: str | None):
        parsed = urllib.parse.urlsplit(base_url)
        if (parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}
                or parsed.username or parsed.password or parsed.query or parsed.fragment):
            raise ValueError("FAKER_BASE_URL must be a local http URL, e.g. http://127.0.0.1:8085")
        self.base_url = base_url.rstrip("/")
        self.connections_file = Path(connections_file).expanduser() if connections_file else None
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def profiles(self) -> dict[str, Any]:
        if self.connections_file is None:
            raise ToolError("Set FAKER_CONNECTIONS_FILE to a private local connection-profile JSON file.")
        try:
            value = json.loads(self.connections_file.read_text(encoding="utf-8-sig"))
            if not isinstance(value, dict):
                raise ValueError()
            return value
        except (OSError, ValueError):
            raise ToolError("Cannot read connection profiles. Check FAKER_CONNECTIONS_FILE and its JSON format.") from None

    def connection(self, name: str) -> dict[str, Any]:
        profile = self.profiles().get(name)
        if (not isinstance(profile, dict) or profile.get("type") not in {"MYSQL", "ORACLE", "DM"}
                or not all(isinstance(profile.get(k), str) and profile[k] for k in ("url", "username", "password"))):
            raise ToolError("Unknown or incomplete connection profile. Configure type, url, username and password locally.")
        return {k: profile[k] for k in ("type", "url", "username", "password")}

    def scrub(self, value: Any) -> Any:
        secrets = []
        if self.connections_file:
            for profile in self.profiles().values():
                if isinstance(profile, dict):
                    secrets.extend(profile[k] for k in ("password", "url") if isinstance(profile.get(k), str) and profile[k])
        return self._scrub(value, secrets)

    def _scrub(self, value: Any, secrets: list[str]) -> Any:
        if isinstance(value, dict):
            return {k: self._scrub(v, secrets) for k, v in value.items()
                    if k.lower() not in {"password", "datasourcejson", "datasource"}}
        if isinstance(value, list):
            return [self._scrub(v, secrets) for v in value]
        if isinstance(value, str):
            for secret in secrets:
                value = value.replace(secret, "[REDACTED]")
            value = re.sub(r"(?i)(password|pwd)\s*[=:]\s*[^\s;&]+", r"\1=[REDACTED]", value)
        return value

    def request(self, path: str, body: dict | None = None) -> Any:
        payload = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(self.base_url + path, data=payload,
                                         headers={"Content-Type": "application/json; charset=utf-8"})
        try:
            with self.opener.open(request, timeout=30) as response:
                raw = response.read(8 * 1024 * 1024 + 1)
                if len(raw) > 8 * 1024 * 1024:
                    raise ToolError("Java response exceeds 8 MiB; reduce the scheme size.")
                result = json.loads(raw)
        except (TimeoutError, urllib.error.URLError, OSError):
            raise ToolError("Java service request failed or timed out. Check the local service; a write may already have succeeded. Do not blindly resubmit generation or save requests.") from None
        except ValueError:
            raise ToolError("Java service returned invalid JSON. Check FAKER_BASE_URL and the Java version.") from None
        if not isinstance(result, dict) or result.get("code") != 0:
            message = result.get("msg", "Unknown Java API error") if isinstance(result, dict) else "Invalid Java API result"
            raise ToolError(str(self.scrub(message)))
        return result.get("data")


api = JavaAPI(os.environ.get("FAKER_BASE_URL", "http://127.0.0.1:8085"), os.environ.get("FAKER_CONNECTIONS_FILE"))
mcp = FastMCP("faker-data", log_level="WARNING", instructions=(
    "Generate related test data using the existing faker-data rules engine. Read faker://rules before configuring. "
    "Inspect the business schema with the user's existing database MCP. Use ordinary rules for every supported field; "
    "use EXT.FIELD only for unsupported values. Validate, save a scheme, then generate only with user-authorized writes. "
    "Several states are several ordinary schemes/tasks. Never retry a generation automatically after a timeout. "
    "Credentials are read from private local profiles, never supplied as tool arguments."
))
READ = ToolAnnotations(readOnlyHint=True, destructiveHint=False, idempotentHint=True, openWorldHint=False)
WRITE = ToolAnnotations(readOnlyHint=False, destructiveHint=True, idempotentHint=False, openWorldHint=True)


@mcp.tool(annotations=READ, structured_output=True)
def validate_generation(tables: list[dict]) -> dict[str, Any]:
    """Check rule syntax, dependencies and supported types without DB writes. Returns configuration scope and unchecked items; does not prove DB/business validity."""
    return api.scrub(api.request("/generation/validate", {"tables": tables}))


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=False, destructiveHint=True, idempotentHint=True, openWorldHint=False), structured_output=True)
def save_scheme(name: str, tables: list[dict], connection: str = "default") -> dict[str, Any]:
    """Validate and save a normal editable UI scheme. Same name replaces its configuration. Connection is a local profile name, never a password."""
    if not name.strip() or len(name) > 200:
        raise ToolError("Scheme name must contain 1-200 characters.")
    source = api.connection(connection)
    validate_generation(tables)
    scheme_id = api.request("/scheme/save", {"name": name.strip(), "tables": tables, "dataSource": source})
    return {"schemeId": scheme_id, "name": name.strip(), "connection": connection}


@mcp.tool(annotations=READ, structured_output=True)
def get_scheme(scheme_id: int | None = None) -> dict[str, Any]:
    """List scheme IDs/names, or read one scheme's field rules. Never returns database connection credentials."""
    if scheme_id is None:
        rows = api.request("/scheme/list")
        return {"schemes": [{"id": row["id"], "name": row["name"], "kind": row.get("kind", "RULES")} for row in rows]}
    if scheme_id < 1:
        raise ToolError("scheme_id must be positive.")
    row = api.request("/scheme/" + str(scheme_id))
    return api.scrub({"id": row["id"], "name": row["name"], "kind": row.get("kind", "RULES"),
                      "tables": json.loads(row["tablesJson"])})


@mcp.tool(annotations=WRITE, structured_output=True)
def generate_data(scheme_id: int, connection: str = "default", confirm_write: bool = False) -> dict[str, Any]:
    """Insert test data by running a saved normal scheme through GEN_DATA. Requires user-authorized confirm_write=true. Returns taskId immediately; poll get_task. No automatic retry."""
    if not confirm_write:
        raise ToolError("Generation writes to the target database. Set confirm_write=true only within the user's authorized scope.")
    if scheme_id < 1:
        raise ToolError("scheme_id must be positive.")
    source = api.connection(connection)
    row = api.request("/scheme/" + str(scheme_id))
    if row.get("kind", "RULES") != "RULES":
        raise ToolError("Only ordinary RULES schemes are supported.")
    tables = json.loads(row["tablesJson"])
    validate_generation(tables)
    task_id = api.request("/publish/task/GEN_DATA", {"dataSource": source, "tables": tables})
    return {"taskId": task_id, "type": "GEN_DATA", "schemeId": scheme_id, "connection": connection,
            "next": "Poll get_task(taskId). Do not submit again while completion is unknown."}


@mcp.tool(annotations=READ, structured_output=True)
def get_task(task_id: str) -> dict[str, Any]:
    """Read a generation task's state, progress and sanitized error. FINISHED means committed generation, not business acceptance."""
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,100}", task_id):
        raise ToolError("Invalid task_id.")
    return api.scrub({"taskId": task_id, **api.request("/asyncTask/queryProcess/" + task_id)})


@mcp.resource("faker://rules")
def rules() -> str:
    return (Path(__file__).parent / "RULES.md").read_text(encoding="utf-8")


if __name__ == "__main__":
    mcp.run(transport="stdio")
