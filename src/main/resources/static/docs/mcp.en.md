# Generate business test data over MCP

[中文](mcp.md)

The stdio adapter connects AI clients to the existing Java `GEN_DATA` engine. Normal rules generate ordinary fields; trusted local extensions only fill unsupported field values. The manual UI and MCP share ordinary schemes. Build the current source: older published JARs do not include the new `/generation/validate` endpoint.

## Setup

Requirements: Java 8+, Python 3.10+, and an AI client supporting stdio MCP. Start the Java JAR separately, then create a Python environment:

```sh
java -jar faker-data-0.1.1.jar
python -m venv .venv
```

Windows: `.venv\Scripts\python.exe -m pip install -r integrations/mcp/requirements.txt`. macOS/Linux: `.venv/bin/python -m pip install -r integrations/mcp/requirements.txt`.

Copy `integrations/mcp/connections.example.json` to a private directory outside the checkout and enter a test database connection. Never commit the file or send passwords to the AI. Saved Java schemes also contain local connection configuration: protect the Java data directory.

Add this to your client's MCP configuration, replacing paths with local absolute paths:

```json
{
  "mcpServers": {
    "faker-data": {
      "command": "/path/to/faker-data/.venv/bin/python",
      "args": ["/path/to/faker-data/integrations/mcp/server.py"],
      "env": {
        "FAKER_BASE_URL": "http://127.0.0.1:8085",
        "FAKER_CONNECTIONS_FILE": "/private/faker-connections.json"
      }
    }
  }
}
```

On Windows use `C:/path/to/faker-data/.venv/Scripts/python.exe` as command and forward slashes in paths. The adapter calls a local HTTP Java service, listens only on stdio, and does not start Java automatically.

## AI workflow

Ask the AI to investigate schema and relationships using your existing database MCP, then read `faker://rules`. For example: “Generate 50 saved orders, 20 submitted and 100 approved, with details and valid existing customers. Use ordinary rules wherever supported. Validate, execute within my authorized test database, and report tasks and verification queries.”

| Tool | Behavior |
| --- | --- |
| `validate_generation(tables)` | Configuration syntax, rules and dependencies only; no target DB connection, writes or extension execution |
| `save_scheme(name,tables,connection)` | Validate and save an ordinary editable UI scheme; the same name replaces configuration |
| `get_scheme(scheme_id)` | Read field rules, or list IDs/names when omitted; connection credentials are excluded |
| `generate_data(scheme_id,connection,confirm_write)` | Submit one GEN_DATA task when `confirm_write=true` is covered by user authorization |
| `get_task(task_id)` | Read state, progress and sanitized errors |

`connection` selects a private local profile (default: `default`). Execution uses the explicitly selected profile, not stale saved credentials. Configuration validation does not prove database constraints, candidate availability, installed extensions or business workflow validity. `FINISHED` means generation completed, not business acceptance.

Multiple states are separate schemes and transactions. Do not automatically retry timed-out writes; retain and query task IDs. If submission timed out before returning an ID, inspect Java task records and target data first. No cleanup tool is exposed.

## Portable demo

Run [mysql-order-schema.sql](../examples/mysql-order-schema.sql) in a dedicated empty test schema, then:

```sh
python examples/create-order-schemes.py --output order-schemes --counts 50 20 100
```

Have the AI read each file's `tables`, validate, save and generate through MCP. Customer candidates are read-only. Each order receives two items, two INPUT/OUTPUT content rows and one referencing row. Exact references match the current order and content role; ordinary IDs require no extension. These states demonstrate relational data, not a real workflow engine's executable protocol.

Optional field-only binary demo:

```sh
python examples/install-field-extension.py --root ./data/extensions --demo orders
python examples/create-order-schemes.py --output order-schemes-binary --counts 50 20 100 --with-binary
java -jar faker-data-0.1.1.jar --faker.extensions.enabled=true
```

The installer copies public Python code and writes a machine-specific manifest without overwriting existing extensions. Match the root to `faker.extensions.root` (default `data/extensions`). It does not enable or execute code. The binary format is a demo, not a universal workflow codec. Extensions run as trusted local code, not in a sandbox.

## Verification

`python -m unittest discover -s integrations/mcp -p test_server.py -v` runs adapter boundary tests. Real stdio protocol tests require an empty dedicated demo schema, a running Java service, and explicit `FAKER_MCP_E2E=1`, `FAKER_BASE_URL`, `FAKER_CONNECTIONS_FILE`. Run `test_protocol.py`; it inserts and retains demo records and is skipped by default. H2 fixture results do not prove native database compatibility.
