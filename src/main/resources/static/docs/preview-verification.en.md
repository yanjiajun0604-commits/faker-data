# Field diagnostics, no-write preview and batch verification (source preview)

[中文](preview-verification.md) · [MCP](mcp.en.md)

These are three different observations: configuration diagnostics locate errors, preview evaluates a bounded sample, and verification reads already committed original rows. None proves a business application can submit/approve a generated workflow.

## Field diagnostics

Configuration/preview failures include a `diagnostic` when a rule location is known: `code`, `table`, `column`, `rulePath`, `ruleCode`, optional zero-based `rowIndex`, and `hint`. For example, `regs[0].onTrue[0]` locates a nested conditional rule. Raw field values, rule parameters and connection credentials are not included in this object. Whole-table/graph failures may only identify the table/container.

Async `get_task` retains the same diagnostic in local SQLite after failure and across repeated reads; existing `errorMsg` remains available. MCP errors use `isError=true` and JSON text containing the message plus available diagnostic/error/reason, so inspect that detail before changing rules. Match failures must be resolved with actual source/parent/role evidence, not by silently weakening missing/multiple policies.

## True no-target-write preview

MCP: `preview_generation(tables,rows_per_table=5,connection="reader")`.
HTTP: `POST /generation/preview` with `dataSource`, `tables` and `rowsPerTable`.

Preview uses ordinary prepared rules, task-local reference caches, sampled parent expansion and existing-source SELECTs. Its orchestrator has no INSERT, DDL, generation journal, extension runner or local task submission. It does not insert and roll back: INSERT triggers and identity counters are not invoked. A SELECT-only test profile is sufficient. Temporary in-process reference/sequence state is cleared after the request.

Review `scope=GENERATED_SAMPLE_NO_TARGET_WRITES`, `sampleValid`, `preflight`, per-table `requestedRows`/`sampledRows`, `omittedExtensionFields`, rows/columns and `notChecked`. Static database-preflight failure returns `sampleValid=false`; HTTP success alone is insufficient. A successful sample does not prove full-task uniqueness, JDBC conversion, dynamic constraints, trigger behavior, extension results or workflow execution. It does not acquire cleanup ownership baselines.

Displayed values are generator output before JDBC conversion and database defaults; they may differ from the final stored representation.

Limits: 1–20 tables, 1–20 rows per independent generated root (default 5), at most 1000 generated rows after parent expansion, 50000 total generated/source cells, 2M generated-output characters, per-cell display 512 characters. Read-only sources use at most 1000 rows and 10000 cells per source, at most 50 scalar columns with declared size up to 1024; excess actual source rows fail instead of silently changing exact-match candidates. Tree topology is bounded to 1000 nodes, and only its sampled prefix is emitted. Unsupported/unbounded requests fail with a reason rather than write data.

Preview executes only reviewed exact built-in rule classes. General `REG.STYLE`, custom rule implementations and arbitrary expressions are currently rejected. Built-in conditional arithmetic/comparison/boolean operations and the pure functions length/contains/startsWith/endsWith/isEmpty/isNotEmpty/between/toString are supported; Java types, constructors, methods, properties, assignments, custom functions and other AST forms are rejected. Formal generation keeps its existing expression/extension behavior. `EXT.FIELD` becomes an `EXTENSION_NOT_EXECUTED` placeholder; no extension installation/execution is required. Credential-named columns are redacted; this is not general PII detection.

Samples may not cover a match only available later in a large source/generated set. A sample mismatch is scoped to the sample; it is not proof the full task must fail. Preview/source reads can observe concurrent database changes between preflight and sampling.

## Verify committed original rows

MCP: `verify_batch(batch_id,checks=[...],connection="reader")`.
HTTP: `POST /batches/{id}/verify` with `dataSource` and `checks`.

```json
[
  {"kind":"ROW_COUNT","table":"orders","expected":50},
  {"kind":"VALUE_SET","table":"orders","column":"state","values":["DRAFT","SUBMITTED"]},
  {"kind":"REFERENCE","table":"items","targetTable":"orders",
   "columns":{"order_id":"id"},"targetScope":"BATCH","allowNull":false}
]
```

The checker reads database identity, original key/schema receipts, full-row and immutable-field fingerprints in one repeatable-read/serializable observation. `snapshotStatus` is `UNCHANGED`, `DECLARED_MUTABLE_CHANGES`, or `CHANGED_OR_MISSING`. `valid` also requires all requested checks to pass. An allowed mutable status change can preserve immutable ownership while failing an explicit VALUE_SET requirement.

ROW_COUNT counts actual present **batch** rows, not the entire table. VALUE_SET compares bounded scalar values and rejects credential columns. REFERENCE maps child columns to the complete compatible parent PK. `BATCH` requires the resolved parent actually belongs to this batch; `DATABASE` only requires existence in the selected schema. Resolution uses the database's key comparison, including case-insensitive collations. Nullable references pass only with explicit `allowNull=true`.

Limits: 10000 original snapshots, 4M encoded key/fingerprint characters, 20 checks, 100 VALUE_SET candidates, 50000 selected cells and 4M value characters per check, 2048 characters per checked scalar. At most ten failing keys are returned per check. No SQL strings, arbitrary joins/expressions or cross-schema target names. Unknown/untracked/non-COMMITTED batches do not become eligible through verification.

This does not repair data, reconcile UNKNOWN commits, mutate task/batch state or delete anything. Later derived records, missing business associations, full workflow semantics and changes after the observation remain outside the result; use the separately reviewed [derived cleanup policy](derived-ownership.en.md) for that lifecycle.
