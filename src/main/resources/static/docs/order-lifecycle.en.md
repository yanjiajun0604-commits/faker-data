# Public order lifecycle example

[中文](order-lifecycle.md)

This self-contained relational example uses ordinary rules for all ordinary fields. Only optional binary `payload` uses a local field extension. State strings do **not** establish a working workflow engine, inbox, submission or approval process.

For genuine instances, inboxes, submission and approvals, see the separate [public Flowable acceptance example](../examples/flowable-approval/README.en.md). It uses the same generator with a field-only serialization extension, without adding an engine dependency to the core.

## Prepare

1. [Set up the launcher](launcher.en.md) with a current matching JAR.
2. Execute [mysql-order-schema.sql](../examples/mysql-order-schema.sql) in an authorized **empty, dedicated MySQL schema**. It contains no DROP/DELETE and refuses to silently recreate existing tables.
3. Configure that schema in the private connections.json. Cleanup requires [metadata visibility and other permissions](batch-cleanup.en.md) beyond ordinary generation. Do not expand real business-account privileges for this demo.

Optional binary case: review and install the public codec:

```sh
python examples/install-field-extension.py --root /private/home/data/extensions --demo orders
```

Set `javaArgs` in this home's settings.json to `["--faker.extensions.enabled=true"]`. Extensions are trusted local code, not sandboxed. FAKER-DEMO + JSON is an example format, not a proprietary workflow format. IDs, states, dates, customers and correlated relationships use ordinary rules.

## Run

Use the venv Python installed by setup: `HOME/venv/Scripts/python.exe` on Windows, `HOME/venv/bin/python` on macOS/Linux. Activate it or replace python below with that executable. Run from the source root:

```sh
python examples/order-lifecycle.py run --home /private/home --receipt ./run-001.json --counts 50 20 100 --with-binary --confirm-write
```

Omit `--with-binary` for ordinary mode; no codec is needed. Add `--attach` to use an explicitly started compatible Java service. Otherwise the launcher owns the service and closes it after each command; schemes and journals persist in home/data.

The script uses genuine stdio MCP: rules resource, validation, preflight, save, generate, task/request lookup and batch verification. It never substitutes its own INSERT scripts for field generation.

| Table | New rows | Method |
| --- | --- | --- |
| Customers | 0 | Read enabled existing customers; match customer name by chosen ID |
| Orders | 170 | Ordinary UUID/state/date rules: 50 SAVED,20 SUBMITTED,100 APPROVED |
| Items | 340 | Two per parent, ordinary numeric rules |
| Contents | 340 | One INPUT/OUTPUT per parent, optional binary field codec |
| Reference records | 170 | Exact order/role correlation |

Total: 1020 new rows in three separate transactions. Each batch checks four table counts, states, six complete-key references and actual row snapshots. There is no cross-batch atomicity guarantee; binary snapshot equality does not prove workflow-engine compatibility.

## Keep, inspect and clean

Run retains generated data. Keep its receipt (request IDs, batch IDs and checks; no credentials) together with the original local data directory. Existing receipts cannot be overwritten by another run. Never use a new request ID to blindly retry a timed-out write.

```sh
# Only recover original requests and verify, without submitting more generation:
python examples/order-lifecycle.py resume --home /private/home --receipt ./run-001.json
# Explicitly authorize cleanup of these batches:
python examples/order-lifecycle.py cleanup --home /private/home --receipt ./run-001.json --confirm-delete
```

Cleanup saves the exact preview plan/hash before deletion. The tool rechecks ownership, deletes in dependency order and independently verifies absence. Subsequent cleanup only reads existing plan receipts; it never bypasses UNKNOWN with a new plan. Interrupted-before-execution or expired plans need inspection, not automatic deletion retries.

Schema, seeded customers, schemes, journals, extension files and normal audit information remain. This is precise row cleanup, not a claim of system-wide trace erasure. Partial failures may leave earlier committed batches; resume does not create missing later cases. Do not move receipts across unrelated local service data directories.
