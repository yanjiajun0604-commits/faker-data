# Existing datasets and parent-row expansion

[简体中文](existing-data.md)

These features are new in the current source and are not included in older released JARs. Configure them through the existing `GEN_DATA` JSON API; no new visual editor has been added. Existing recipes can omit both properties.

## Existing data for logical references

A field without a database foreign key may still require a valid user, organization, customer or product. Keep `RES.INPUTAREA` for constants and small fixed candidate lists. Use `existingData` to read candidates while preserving related fields from one record.

Include the source table in the same request's `tables` array, with explicit projected columns:

```json
{
  "name": "demo_lookup_user",
  "existingData": {
    "where": {"enabled": 1},
    "orderBy": ["id"],
    "maxRows": 10000
  },
  "columns": [
    {"name":"id","javaType":"java.lang.String"},
    {"name":"display_name","javaType":"java.lang.String"},
    {"name":"org_id","javaType":"java.lang.String"}
  ]
}
```

This entry reads existing rows and never inserts into the source table. Its `size` is ignored, and its columns cannot contain generation rules. Each source is read once before generated rows are written, then reused as a task-local dataset snapshot. This is not a guarantee of a common point-in-time snapshot across different source tables; verify database isolation and business constraints separately if required.

Target fields keep the existing reference rule:

```json
{"code":"REF.OTHER_TABLE","refOtherTableColumn":["demo_lookup_user","id"]}
```

Reference `display_name` and `org_id` from the same table for the corresponding target fields. They select the same source row as the user ID. Ordinary references cycle by target row index.

Read boundaries:

- Uses the request's target database connection, with SELECT permission on source tables.
- `where` combines equality predicates with AND. Values are strings, numbers, booleans or null; null becomes `IS NULL`. Filter fields need not be projected. Values are parameter-bound, not SQL fragments.
- Table names may use restricted `schema.table` identifiers. Columns and `orderBy` accept ordinary identifiers, not expressions, sort directions or arbitrary SQL. Default ordering uses all projected columns ascending; prefer an explicit unique key.
- `maxRows` defaults to 10000 and accepts 1–100000. Empty results and overflow fail instead of silently truncating candidates. Reference-cache entry and byte budgets still apply.
- Dates become epoch-millisecond strings; numbers and booleans become strings; null remains null. Binary, BLOB, CLOB and other complex values are unsupported.
- Table names must be unique within a request. Multiple aliases of one table, JOINs, per-target-row parameterized queries and random sampling are not supported yet.

## Expand only matching parent rows

Add this to the child table:

```json
{
  "parentRows": {
    "table": "demo_lookup_document",
    "condition": "#state == 'SUBMITTED'",
    "rowsPerParent": 2
  }
}
```

The engine first generates or loads parents, filters them, and emits two children per match. Actual expansion replaces the child's `size`. All `REF.OTHER_TABLE` rules pointing at this parent bind to the current parent record. References to other tables still cycle by child index.

`condition` uses parent fields as SpEL variables. Prefer helpers such as `between(#amount,1,100)` for numeric comparisons, since rule-context values are usually strings. Omit the condition to select every parent. No matching parent produces zero children. Parents may be generated rows or `existingData`. Incrementing field rules still count across the whole child table, not separately per parent.

`rowsPerParent` defaults to 1 and accepts 1–10000; only fixed per-parent counts are currently supported. A table cannot combine `parentRows` with `treeConfig` or `existingData`. Cycles and integer overflow fail rather than accepting partial output.

## Runnable example

The [example SQL](../examples/mysql-existing-data-schema.sql) creates three new tables and three fictional users, two enabled, in an isolated test database. Do not run it against existing business tables.

The [request JSON](../examples/generate-existing-data.json) reads the two eligible users, generates six documents, then emits two child rows for each of the two `SUBMITTED` documents. User IDs, names and organizations remain aligned, and source users are not inserted.

```sh
python examples/ai_client.py --request examples/generate-existing-data.json
python examples/ai_client.py --request examples/generate-existing-data.json --submit --timeout 300
```

Follow the [AI guide](../AI_USAGE.en.md) for connection settings, local credentials, authorization and task polling. Temporary H2 integration tests exercise this JSON, field alignment, snapshot reuse, overflow, empty results, parameter binding, parent filtering and rollback. Real MySQL, Oracle, Dameng and application acceptance require separate testing.

These features generate relational data. Example child rows are not operational workflow tasks. An executable process still requires the target engine's runtime protocol. HTTP actions, generation manifests and automatic cleanup are not implemented.

## Exact correlated references

Add `refMatch` to `REF.OTHER_TABLE`, for example `{"order_id":{"field":"order_id"},"role":{"value":"INPUT"}}`. Source fields must match the current row field or scalar constant (including null), joined with AND. Matching uses text-normalized equality; null equals null, unlike SQL three-valued logic. Source fields must exist in the projected columns. The engine builds an equality index once per prepared rule/task, without per-target-row SQL queries. It works with generated and existing source rows.

No match or multiple matches fails and rolls back by default (`REF_MATCH_NONE` / `REF_MATCH_MULTIPLE`). Explicit `refOnMissing: "NULL"` or `refOnMultiple: "FIRST"` changes the behavior; FIRST follows stable source order. Omit refMatch to preserve legacy round-robin or bound-parent selection. Dependencies must be acyclic. The existing rule editor can edit the advanced matching JSON. See the [MCP guide](mcp.en.md) for a portable public order example.
