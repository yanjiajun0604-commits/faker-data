# Declared ownership of later-created records (source preview)

[中文](derived-ownership.md) · [Cleanup](batch-cleanup.en.md)

When an application submits or approves generated documents, it can change their status and create new instances, tasks or history. Generation's original key journal does not automatically own these records. Declare the following optional policy **before generating**; old batches cannot retroactively acquire it. This policy does not generate workflow state or replace field rules.

```json
{
  "name": "documents",
  "size": 2,
  "columns": [
    {"name":"id","javaType":"java.lang.String","regs":[{"code":"FIX.UUID"}]},
    {"name":"state","javaType":"java.lang.String","regs":[{"code":"RES.INPUTAREA","inputs":[{"value":"DRAFT","weight":1}]}]}
  ],
  "ownership": {
    "mutableColumns": ["state"],
    "derivedRows": [{
      "table":"instances", "join":{"document_id":"id"},
      "freshness":"AUTO", "baselineLimit":10000, "maxRows":10000,
      "derivedRows":[{"table":"tasks","join":{"instance_id":"id"},"where":{"kind":"fixture"}}]
    }]
  }
}
```

The example assumes matching real tables/columns and indexes; rename them for the selected test schema. UI and MCP save this as part of a normal table scheme. `join` maps child columns to the **complete parent primary key**, using compatible string/numeric types and AND equality. It requires an index whose leading columns cover the child join. `where` is optional scalar equality/IS NULL joined with AND. It is not SQL and does not infer undeclared relationships. The same physical derived table can be declared once per policy graph; nesting must be acyclic.

`mutableColumns` allows declared non-key columns to change between generation and cleanup preview. Every other original field must still match its recorded immutable-field digest. Primary keys cannot be mutable. Preview freezes **current full-row** digests, so even an allowed status change after preview invalidates that plan. Pre-existing/read-only source rows cannot receive an ownership policy.

## Freshness and safety

Generation captures the baseline before inserting any target rows, inside a serializable target transaction. This can lock key/index ranges; use bounded test tables and account for concurrent writers.

- `AUTO`: one numeric child PK uses `NUMERIC_HIGH_WATERMARK`; other supported scalar/composite PKs use `FULL_KEYS`.
- `NUMERIC_HIGH_WATERMARK`: later keys must be strictly above the pre-generation maximum. A genuinely new lower ID is conservatively rejected. This assumes stable primary keys and an accurate declaration of exclusive ownership; it is not a universal record-provenance mechanism.
- `FULL_KEYS`: save the child's pre-existing key set locally, within `baselineLimit`. A pre-existing key moved to a generated parent is rejected. Local baseline keys are private data; protect the Java data directory.

Matching non-owned direct-child records found before an original parent insert reject generation. Cleanup follows only declared ancestry, rejects old baseline candidates, other live batch/cleanup claims, and physical references from adopted descendants to non-owned rows in the declared graph. Existing physical inbound FK, permission, transaction and DELETE-trigger checks also apply to descendants. Read-only lookup tables outside this graph are not automatically adopted or deleted.

Review `derivedRowCount` alongside `rowCount`. `get_cleanup(batch_id,plan_id,include_rows=true,offset=0,limit=100)` or `GET /batches/{batch}/cleanup/plans/{plan}/rows` returns the persisted exact keys, full fingerprints, `origin` (`ORIGINAL`/`DERIVED`) and relation ID. Pages have at most 200 rows. Execute re-discovers/locks/rechecks the complete set; added, removed or changed descendants invalidate the plan. Deletion is child-first; independent post-commit checks cover planned keys and declared relationships using retained parent keys.

Existing cleanup UNKNOWN rules remain: inspect the same receipt after a timeout; do not submit another deletion to bypass ambiguity. Old READY plans created before persisted plan-key support are invalidated on upgrade.

Limits: selected schema, 20 relations, depth 5, 50 mutable columns, 20 predicates, 1–100000 candidate/baseline rows per relation (default 10000), 100000 aggregate full-baseline keys and 16M encoded baseline characters. Cleanup retains its 100000 total rows/approximately 16M key-and-fingerprint character budget. Overflow blocks; it never truncates the deletion scope. Native cleanup is verified on MySQL InnoDB; H2 is a test fixture.

Unknown weak relationships, external side effects and records added after the verification snapshot cannot be inferred. This is a bounded declared cleanup scope, not a promise to erase normal system audit logs or all traces of business activity. A correct policy and application-level acceptance remain necessary.
