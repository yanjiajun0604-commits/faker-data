# Task-local virtual fields (source preview)

[中文](virtual-fields.md) · [Runnable public example](../examples/virtual-fields/README.md)

Add `"virtual": true` to a **generated** column to create a task-local association marker using ordinary rules. Default is false. It can participate in same-row references, conditional rules, parentRows filtering, cross-table references and refMatch. The JDBC writer excludes virtual columns from INSERT; no ALTER TABLE or extension is required.

```json
{"name":"owner_marker","javaType":"java.lang.String","virtual":true,
 "regs":[{"code":"REF.OTHER_TABLE","refOtherTableColumn":["documents","id"]}]}
```

A content table may have only `id`. Generate virtual `owner_marker` and `role_marker` alongside it, then match its ID from a task:

```json
{"code":"REF.OTHER_TABLE","refOtherTableColumn":["contents","id"],
 "refMatch":{"owner_marker":{"field":"document_id"},"role_marker":{"value":"INPUT"}}}
```

The marker values must actually be generated from the intended parent/role. They do not make a missing or ambiguous association valid. Exact matching retains the usual failure behavior and the full write transaction rolls back on failure. The public example uses ordinary UUID, same-row reference, constant, conditional and exact-reference rules; its content table has no owner/role columns. Three documents and six contents produce two selected tasks with zero mismatches and zero extension fields.

## Boundaries

- At most 50 virtual fields per table; generated targets still require at least one physical column and an existing real target table. This is not a standalone virtual-table data source.
- A virtual name cannot shadow a real column. Preflight reports `VIRTUAL_SHADOWS_DATABASE_COLUMN`, and continues checking omitted physical required columns. Preflight/configuration summaries expose `virtualColumns`.
- `existingData` remains a read-only source of explicit physical columns; virtual fields on it are rejected. Unknown relationships in pre-existing rows still need a real source of truth.
- Virtual fields cannot be SQL filters, physical primary keys, database constraints or physical cleanup ownership joins. Use the real generated parent PK in an ownership policy.
- Values remain available to trusted field-extension context when a genuinely unsupported physical field needs an extension. Existing EXT.FIELD dependency/allowlist/size restrictions still apply. Virtual values are not target data and are not independently recoverable from the insertion journal.
- Reference-cache limits and whole-task memory bounds still apply. UI advanced creation controls are a separate roadmap task; ordinary scheme serialization preserves the flag.

Verify using current source, not an older JAR that ignores this flag. Inspect preflight summaries, generated counts/references and `get_batch`; the journal must contain actual physical keys only. Cleanup still requires the reviewed plan and authorization. This example demonstrates relational data generation, not an executable workflow engine.
