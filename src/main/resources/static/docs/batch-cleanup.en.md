# Preview and clean a generated batch (source preview)

[中文](batch-cleanup.md)

Cleanup covers original inserted rows explicitly owned by the generation journal. Older/untracked data, read-only candidates, pre-existing rows and later weakly linked business-created rows are not adopted. Later derived-record ownership is separate; cleaning original inserts is not proof of an entirely traceless business scenario.

## AI workflow

1. Inspect get_batch(batch_id).
2. preview_cleanup(batch_id, connection="cleanup") performs target SELECT-only checks and stores a ten-minute local plan. Review eligible, blockers, rowCount, deleteOrder, scope, planId/planHash; tool/HTTP success alone is insufficient.
3. Within user authorization for that reviewed batch, call execute_cleanup(batch_id, plan_id, plan_hash, connection="cleanup", confirm_delete=true).
4. Query get_cleanup(batch_id, plan_id). DONE must include verifiedAbsent=true. After timeout/UNKNOWN, inspect the same receipt rather than attempting deletion with another plan.

HTTP: POST /batches/{id}/cleanup/preview with dataSource; POST /batches/{id}/cleanup/execute with dataSource/planId/planHash/confirmDelete; GET /batches/{id}/cleanup/plans/{planId}. Keep connection credentials local. Journal/plan reads never delete.

## Checks and outcomes

Plans bind database identity, column/PK schema, the complete write manifest, physical FK topology, expiry and random plan identity. Execution locks/rechecks original keys inside a serializable transaction, deletes child tables before parents and independently verifies absence after commit. FK checks stay enabled; no TRUNCATE, sequence resets or log purges. Reusing a DONE/UNKNOWN plan does not delete again.

Changed/missing/shared keys, incomplete/unknown/untracked ownership, external referencing records, DELETE triggers, cross-scope references, non-PK FK targets, self/cyclic table graphs and schema/topology changes block cleanup. A self-FK with no matching rows adds no dependency. Composite primary/foreign keys are supported. Concurrent changes are addressed by execution-time checks and database enforcement.

Before any commit attempt, a confirmed rollback restores batch COMMITTED and invalidates that plan. Ambiguous commit, interruption or failed independent post-check produces plan UNKNOWN/batch CLEANUP_UNKNOWN without replay. Journal evidence remains intact. Missing rows alone do not establish when an unknown transaction committed; use execution history to investigate.

## Native database and permission scope

The initial native target is MySQL InnoDB. H2 admin is a test fixture, not native Oracle/Dameng evidence. Other database cleanup is blocked until its capability/permission checks are natively validated; existing generation support is not expanded by this claim.

To avoid confusing hidden metadata with absence, MySQL currently requires SHOW GRANTS to directly prove global SELECT plus TRIGGER on the owned schema (or global), with no partial revokes. Inherited/ambiguous role grants are conservatively unsupported. An ordinary generation account may not qualify for cleanup. **The product never issues GRANT or changes accounts.** Use an approved test account/separate private profile. TRIGGER also permits trigger changes; a database administrator should assess it rather than automatically expanding real-environment privileges to run a demo.

[MySQL's documentation](https://dev.mysql.com/doc/mysql-infoschema-excerpt/8.0/en/information-schema-triggers-table.html) requires table TRIGGER privilege to see trigger metadata. Missing metadata is not proof of absence. Existing DELETE triggers block cleanup; side effects are not guessed. Generation preflight/formal writes also reject non-InnoDB MySQL targets to preserve whole-task rollback semantics; read-only candidates can use other engines.

Bounds: 100 owned tables, 100000 verified snapshots, 8192 characters per encoded PK, approximately 16M total characters of key/fingerprint text, 500 inbound FK metadata entries per table, 100000 scanned references. Overflow blocks rather than truncating and deleting. The [journal](generation-batches.en.md) keeps its 64 MiB per-row fingerprint budget. These are v1 limits, not instructions to bypass checks.

One heavy cleanup preview/execution runs per service at a time; concurrent requests return CLEANUP_BUSY. Reading an existing receipt does not enter this heavy-operation gate.
