# AI agent integration guide — faker-data

> **Access URL:** this guide targets v0.1.1 and current source builds at `http://127.0.0.1:8085`. Older v0.1.0 JARs retain `http://127.0.0.1:8085/faker`; set the client's base URL explicitly when calling that older build. See [access URLs and version compatibility](docs/access-url.md).

Workflow: **person → local AI agent → HTTP API → target test database**. There is no built-in LLM; the agent supplies reasoning and translates the user's authorized request into field rules.

Build and run a JAR from the current source. Its default base URL is `http://127.0.0.1:8085`. A cloud agent cannot reach the user's localhost; run calls through a local agent/tool. Do not expose this unauthenticated service publicly.

## Call sequence

1. Establish the user's authorized target, tables, row counts, and rules. Do not guess a production target or clear data. Existing, explicit authorization does not need repeated confirmation.
2. `GET /asyncTask/list` must return `code:0` and a GEN_DATA task type.
3. `POST /db/connect/test` with the data source object below.
4. `POST /db/showTables?withSub=0` with the same object. Read the existing table names.
5. `POST /db/showColumns/{url-encoded-table-name}` with the same object. Preserve the actual name, javaType, dbType, length, and nullable values. `withSub=1` on showTables can include columns for all tables.
6. Build `{dataSource, tables:[{name,size,columns:[...]}]}`; each column has `regs:[...]`. Merge rules into metadata; do not guess JDBC Java types.
7. `POST /publish/task/GEN_DATA` once. On `code:0`, save the returned `data` task UUID.
8. Poll `GET /asyncTask/queryProcess/{uuid}` about once a second. FINISHED with progress 100 confirms successful task completion. ERROR is failure; WAITING/PROCESSING means continue polling the same UUID. NONE means unknown to this instance. Report uncertainty rather than automatically resubmitting.

All JSON requests use Content-Type application/json. HTTP success does not replace checking `code` (0 is success, nonzero is failure). Data is in `data`; errors are in `msg`, or in task data's `errorMsg`.

```json
{"type":"MYSQL","url":"jdbc:mysql://127.0.0.1:3306/faker_demo?serverTimezone=UTC","username":"faker_demo","password":"safely supplied by the user"}
```

Supported target types are MYSQL, ORACLE, DM. The URL, user and password must be nonempty. SQLite stores application state and is not a supported generation target. Tables must already exist; there is no schema creation or arbitrary SELECT/SQL endpoint.

## Rules

| code | Parameters / meaning |
| --- | --- |
| FIX.UUID / FIX.PEOPLE_NAME / FIX.TEL / FIX.IDENTIF | UUID, Chinese name, phone, identity-shaped synthetic string |
| REG.RANGE_NUM | minSize, maxSize: inclusive integer range |
| REG.INCREMENT_NUM | minSize start, step; starts over per task and does not inspect existing table maxima |
| REG.RANGE_STRING | minSize, maxSize: length range |
| REG.STYLE | styleReg: supported regex pattern |
| REG.DATE | dateRange: two Unix epoch **millisecond** integers |
| RES.INPUTAREA | inputs:[{value:"constant/candidate",weight:1}]; one item is a constant |
| REF.SELF_COLUMN | refSelfColumn, optional refSelfTransform:"FIRST_CHAR" |
| REF.OTHER_TABLE | refOtherTableColumn:["parent_table","parent_column"] |
| COND.EXPR | condition, onTrue:[rules], onFalse:[rules] |
| STRUCT.TREE_PARENT | parent value from the table's tree configuration |
| STRUCT.TREE_CODE / REG.TREE_CODE | hierarchical code from enabled tree configuration |

Multiple rules concatenate output. Missing rules usually yield NULL. Preserve strings for UUIDs and phone-like values. Use string candidates such as "12.50" for DECIMAL constants. Binary values require an enabled EXT.FIELD returning byte[]; ordinary text rules do not generate binary. Unknown JDBC Java types fail rather than silently generating values.

Cross-table references read generated rows or source tables configured with `existingData` in the **same request**. The current source adds [existing datasets and parent-row expansion](docs/existing-data.en.md); these extensions are not included in older JARs. References need a nonempty source and usable field values. Dependencies, including nested conditional references, are sorted automatically; cycles are rejected. Let the engine set depended/refOtherTables.

Conditions use `#column` variables, usually string-valued during row generation. Prefer helper functions such as `#between(#age,18,60)` for numeric ranges. Example: `condition:"#kind == 'vip'"` with constant 80 and 100 branches. See the [full guide](AI_USAGE.md) and [SpEL helpers](docs/spel-functions-guide.md).

Tree requests need `treeConfig` plus explicit STRUCT.TREE_PARENT / STRUCT.TREE_CODE rules on the appropriate columns. maxDepth=3 means 3 levels (depths 0,1,2). With 1 root and 2 children per non-leaf, the result is 7 nodes; size is replaced by the actual topology count. Node IDs must be unique, root parent columns should permit NULL, and hierarchical code width must fit the branching. Estimate the full topology before large requests.

## Complete examples and client

- [Sample MySQL schema](examples/mysql-demo-schema.sql): create only in a separately authorized test database.
- [Relational request](examples/generate-demo.json): 20 parents / 50 children, same-row reference, conditional values, BIGINT, DECIMAL, dates.
- [Tree request](examples/generate-tree.json): one 7-node tree.
- [Document data recipe](docs/document-recipes.en.md): exact 50/20/100 state counts, shared keys and related lines, using [its own schema](examples/mysql-document-schema.sql). Includes current capability boundaries; does not create operational workflow instances.
- [Python standard-library client](examples/ai_client.py): check, connect, tables, columns, submit, status, wait; never retries submission.

Adjust the examples to the actual target metadata. Set FAKER_TARGET_PASSWORD securely; do not commit real credentials. Inspect first:

```sh
python examples/ai_client.py --request examples/generate-demo.json
```

When authorized, submit once:

```sh
python examples/ai_client.py --request examples/generate-demo.json --submit --timeout 300
```

Use `--base-url http://127.0.0.1:8095` for another port. The client prints the task ID, then the terminal result. A timeout is not proof of failure: continue querying that ID, do not submit a duplicate task. A POST timeout without a received UUID is ambiguous and requires checking task records/target data before any retry.

## Saved schemes

POST /scheme/save takes `{name,dataSource,tables}` and returns a numeric ID. Same-name saves update that scheme. GET /scheme/list and GET /scheme/{id} return objects with **JSON strings** dataSourceJson and tablesJson; parse those once before creating a generation request. DELETE /scheme/{id} removes only the saved scheme, not target data. Saving stores credentials locally; it is optional.

## Completion and failure

The normal GEN_DATA task runs INSERTs in a single target JDBC transaction. Transactional tables are required for atomic rollback (e.g. InnoDB). GEN_DATA has no idempotency key or automatic duplicate prevention. Restart marks unfinished tasks ERROR and does not replay their arguments. Redis is an optional reference cache, not a distributed queue. There is no cancel endpoint.

Report task ID, final state, requested scope, and any actual row-count/reference/constraint checks. The API does not return verified per-table counts or execute arbitrary SELECTs; use a separately authorized database query tool if needed, and state when data-level checks have not been performed. Protect the local data directory and do not expose credentials in logs or reports.

## Complex fields: local advanced extensions

Current source supports an optional `EXT.FIELD` rule inside ordinary `GEN_DATA`, disabled by default. Ordinary fields use existing rules; only unsupported fields invoke trusted local code using the generated context. Extensions cannot return tables, add records or overwrite ordinary fields. See [local field extensions](docs/local-extensions.en.md). Saved schemes retain the Use flow. There is no state-group engine. A separate [stdio MCP adapter](docs/mcp.en.md) connects AI clients; unknown binary formats are not automatically decoded.
