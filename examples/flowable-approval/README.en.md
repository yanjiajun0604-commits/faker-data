# Public Flowable 6.8.1 acceptance example

[中文](README.md)

An optional, isolated application with document/line tables and genuine Flowable instances, inbox tasks, history and serialized variables in one empty MySQL schema. The faker-data core gains no Flowable dependency or proprietary business code.

Target:50 saved,20 submitted,100 completed documents. Details must open, real reviewer tasks advance to auditor and finish, saved documents can submit, completed documents have ended instances/two completed tasks, and the engine can deserialize the binary payload.

## How generation works

`init` creates a public BPMN model. `templates` briefly creates two reference processes, records their shape and removes them through the engine. These are configuration references, not generated results.

`create_recipe.py` compiles that shape into ordinary faker-data tables/rules. All new IDs use UUID rules, relationships use references, fixed fields/times use input rules, row variants use conditions and virtual fields preserve logical IDs. Only `ACT_GE_BYTEARRAY.BYTES_` uses a trusted field extension. The codec returns Java-serialized field values; it performs no SQL or HTTP calls.

`generate.py` uses actual stdio MCP to validate, preflight, save, generate, poll and verify. It does not call process-start, submit or approval APIs to produce final-state data and does not implement its own INSERT loop. The real engine is used afterward to verify and exercise business operations.

The reference is tied to this deployment/version, not a universal workflow adapter. Recapture/retest after model, deployment or engine changes. Trusted local extensions are not sandboxed.

## Build and prepare

Java8+,Python3.10+,the [launcher MCP environment](../../docs/launcher.en.md) and authorized local MySQL are required. Native evidence uses Flowable6.8.1/MySQL8.0.31; other versions are not implied.

From repository root (POSIX:replace mvnw.cmd with ./mvnw):

```powershell
.\mvnw.cmd -f examples/flowable-approval/pom.xml -B compile dependency:build-classpath '-Dmdep.outputFile=classpath.txt'
```

This is an independent Maven module. Generated classpath.txt contains machine-specific paths; rebuild it on another machine. Initial dependencies require network.

Create an **empty loopback schema ending `_flowable_demo`** and a private [connection profile](../../integrations/mcp/connections.example.json). Only jdbc:mysql://127.0.0.1:port/name_flowable_demo is accepted. The example account needs schema creation/generation rights; cleanup plans additionally require [metadata visibility](../../docs/batch-cleanup.en.md). Never expand real business-account permissions for this example.

Use `/private/demo` as the launcher home and copy your private profile into its connections.json. Keep it outside the repository. Use a separate service port:

```sh
python faker.py --home /private/demo setup --jar /path/current-faker.jar --port 18990
python examples/flowable-approval/demo.py init --profile /private/connections.json --confirm-schema
python examples/flowable-approval/demo.py templates --profile /private/connections.json --output /private/templates.json
python examples/flowable-approval/demo.py install-extension --home /private/demo
```

Init refuses nonempty schemas. Capture templates before generating documents. Evidence files and installed manifests are never overwritten. Explicitly set `javaArgs` in home/settings.json to `["--faker.extensions.enabled=true"]`. Optional `--java /absolute/path/java` selects the app/codec executable; launcher Java remains in its own settings.

## Generate over MCP

Activate the installed venv, or replace python with home/venv/Scripts/python.exe (Windows) or home/venv/bin/python (POSIX):

```sh
python examples/flowable-approval/generate.py --templates /private/templates.json --home /private/demo --receipt /private/generation.json --counts 50 20 100 --confirm-write
```

Default counts are1/1/1; each state is a separate task/transaction. Example limits are1..200 per state. Saved schemes are ordinary rules, also viewable through the existing UI. An AI can inspect schema/rules and author the same configuration itself.

Stable request IDs are persisted before writes. Existing receipts cannot be overwritten. Use `--resume` with the same receipt/home to observe original requests and verify; it never submits missing tasks or blindly retries. The document set must initially be empty.

## Engine and browser acceptance

```sh
python examples/flowable-approval/demo.py verify --profile /private/connections.json --output /private/verified.json
python examples/flowable-approval/demo.py serve --profile /private/connections.json --port 18991
```

Open http://127.0.0.1:18991/. Saved/Submitted/Approved tabs display documents; View shows lines and the actual engine-decoded variable. Owner submits, reviewer approves, then auditor approves. Role selection is a local simulation, **not authentication**. The server binds loopback only and is not a production app; wrong actors are rejected by business methods.

For automated checks across every document and sample submission/approval actions:

```sh
python examples/flowable-approval/demo.py accept --profile /private/connections.json --output /private/accepted.json
```

Actions change counts; do not expect50/20/100 to remain unchanged afterward. The explicit catalog prevents metadata in another visible schema from interfering with initialization.

## Cleanup boundary

Unmodified generated batches may use reviewed MCP preview_cleanup/execute_cleanup. The untouched completed-state batch was confirmed eligible.

Submission/approval modifies/deletes original runtime rows and adds history. The original snapshot cleanup therefore refuses; declaring only a mutable state does not solve it. Never bypass these checks. This example provides separate engine-aware cleanup after business actions:

```sh
python examples/flowable-approval/demo.py purge --profile /private/connections.json --proof /private/verified.json --confirm-delete
```

Only the proof's explicit document IDs are used. The app validates process business-key ownership, lets Flowable delete associated runtime/history, then transactionally deletes the named document/line rows. This is example-specific cleanup, not faker-data generic batch cleanup. On partial failure inspect first; do not blindly repeat. Schema/model/local journals/normal logs remain. Keep receipts with the original local data directory/database.

Own example code follows repository MIT. Flowable dependencies retain their [Apache2.0 project license](https://github.com/flowable/flowable-engine/tree/flowable-6.8.1); Java8 compatibility is documented in the [official repository](https://github.com/flowable/flowable-engine). This fixed local acceptance example is optional for ordinary generator users.
