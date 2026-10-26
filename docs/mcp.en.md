# Generate business test data over MCP

[中文](mcp.md)

The stdio adapter connects AI clients to the existing Java `GEN_DATA` engine. Normal rules generate ordinary fields; trusted local extensions only fill unsupported field values. The manual UI and MCP share ordinary schemes. Build current source for the interfaces below; published JARs may lag behind and their capabilities must be checked before use.

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

Ask the AI to inspect the selected database with built-in `list_tables`, `describe_table` and `read_rows`, then read `faker://rules`. A separate database MCP is optional; business relationships and workflow requirements still need evidence. See [inspection limits and HTTP examples](database-inspection.en.md). For example: “Generate 50 saved orders, 20 submitted and 100 approved, with details and valid existing customers. Use ordinary rules wherever supported. Validate, execute within my authorized test database, and report tasks and verification queries.”

| Tool | Behavior |
| --- | --- |
| `list_tables(name_contains, limit, offset, connection)` | Page through ordinary tables in the selected database/schema |
| `describe_table(table, connection)` | Columns, Java types, comments, driver-reported primary/foreign keys and indexes |
| `read_rows(table, columns, where, order_by, limit, connection)` | Parameterized single-table samples: default 20, maximum 100 rows |
| `validate_generation(tables)` | Configuration syntax, rules and dependencies only; no target DB connection, writes or extension execution |
| `preflight_generation(tables,connection)` | Read actual metadata/static unique candidates; check valid/errors/warnings/notChecked; no writes |
| `preview_generation(tables,rows_per_table,connection)` | Restricted ordinary-rule sample with no target writes or extension execution; inspect sampleValid/notChecked |
| `save_scheme(name,tables,connection)` | Validate and save an ordinary editable UI scheme; the same name replaces configuration |
| `get_scheme(scheme_id)` | Read field rules, or list IDs/names when omitted; connection credentials are excluded |
| `export_scheme(scheme_id,connection_ref)` | Redacted versioned sharing document; preserves null placeholders and reports local bindings |
| `import_scheme(document,connection,name,values_profile,replace_existing,confirm_trusted_code)` | Save a reviewed document using private local values; no target connection/generation or code execution |
| `generate_data(scheme_id,connection,confirm_write,request_id)` | Submit one GEN_DATA task when `confirm_write=true` is covered by user authorization |
| `get_batch(batch_id,include_rows,offset,limit)` | Read commit/rollback/unknown journal and observed keys/fingerprints; no cleanup |
| `verify_batch(batch_id,checks,connection)` | Read committed original snapshots and declared count/value/reference checks; no repair/state change |
| `get_request(request_id)` | Locate the original task by stable request ID, without replay |
| `reconcile_batch(batch_id,connection,confirm_commit)` | Observe UNKNOWN snapshots; explicit confirmation updates only the local receipt |
| `preview_cleanup(batch_id,connection)` | Target SELECT-only checks and bounded local plan; no deletion |
| `execute_cleanup(batch_id,plan_id,plan_hash,connection,confirm_delete)` | Authorized recheck/transactional deletion/independent post-check; no UNKNOWN replay |
| `get_cleanup(batch_id,plan_id)` | Read the original local cleanup receipt without target access |
| `get_task(task_id)` | Read state, progress and sanitized errors |

See [field diagnostics, no-write preview and verification](preview-verification.en.md) for exact limits and failure semantics. Preview is a sample; FINISHED and snapshot checks do not prove business acceptance.

For sharing, use the [portable format and private bindings](portable-schemes.en.md), not the raw local database/legacy scheme response. `faker://contracts` exposes v1 machine schemas/rule metadata. Optional FAKER_SCHEME_VALUES_FILE supplies local replacement profiles without passing values to the AI; same-name imports fail unless explicitly overwritten.

`connection` selects a private local profile (default: `default`). Execution uses the explicitly selected profile, not stale saved credentials. Configuration validation does not prove database constraints, candidate availability, installed extensions or business workflow validity. `FINISHED` means generation completed, not business acceptance.

Multiple states are separate schemes and transactions. Do not automatically retry timed-out writes; retain and query task IDs. If submission timed out before returning an ID, inspect Java task records and target data first. Cleanup is opt-in, bounded to tracked inserts and explicitly declared verified descendants, and requires a reviewed plan; see [cleanup limits](batch-cleanup.en.md).

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

Generation automatically runs [database preflight](generation-preflight.en.md) before submission, in addition to configuration validation.

Read the [batch journal](generation-batches.en.md) after generation; batchId equals taskId.

Choose and retain request_id before submission; see [idempotency and recovery](request-recovery.en.md). Legacy no-key HTTP requests have no such guarantee.

[Batch cleanup](batch-cleanup.en.md) requires preview/authorization; ordinary generation credentials may lack metadata visibility. Optional [later-derived ownership](derived-ownership.en.md) must be declared before generation and passes additional baseline/reference checks. Read persisted plan keys with get_cleanup(include_rows=true).
