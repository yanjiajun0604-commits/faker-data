# Generation batch journal (source preview)

[中文](generation-batches.md)

The formal Java service journals each generation task in local SQLite, with `batchId = taskId`. Ordinary rules, parent expansion, existing candidates and field extensions share this path. Field generation stays in the existing rule engine; manual UI workflows remain available.

Read MCP `get_batch(batch_id=taskId, include_rows=true, offset=0, limit=100)`, or HTTP `GET /batches/{taskId}` and `GET /batches/{taskId}/rows?offset=0&limit=100`. Key pages are capped at 200; continue with nextOffset. Inspection neither cleans nor replays data.

## Recorded evidence

- Database identity (product, catalog/schema, connection fingerprint; native MySQL also supplies server UUID), recipe digest, task ID and actual generation order.
- Confirmed actual primary keys, including composite keys, schema digest and full-row fingerprints. Text/binary values are streamed into digests rather than copied into the journal, with a 64 MiB per-row hashing budget.
- Rows are re-read inside the target transaction. Final snapshots are refreshed before commit, including changes made to already-owned rows by later generated-table triggers. Unconfirmed explicit keys cause rollback.
- Read-only `existingData` candidates are never owned inserts. Later business operations may create additional rows; those are not automatically owned by the original journal.
- No database password, full JDBC URL or full business row is saved. Keys and database/table names remain local data: protect the application data directory.

## State and limitations

`WRITING` -> `PREPARED` -> `COMMITTED`; explicit target rollback becomes `ROLLED_BACK`. Local SQLite and the target are not one atomic transaction. Interrupted/nonterminal receipts become `UNKNOWN` on restart. Never treat UNKNOWN as safe to retry; no automatic replay occurs. COMMITTED `rowCount` counts verified key snapshots only; `stagedRowCount` in other states does not establish committed ownership.

Initial reliable tracking requires explicitly supplied VARCHAR/NVARCHAR/integer/numeric primary keys (fixed CHAR/NCHAR padding semantics are not tracked). Missing PKs, omitted/auto-generated PKs, binary/sensitive PKs, PKs wider than 32 columns, and unsupported object fingerprints are marked `UNTRACKED` with a reason. Generation can continue, but `trackable=false`. `untrackedRows` counts successful untracked insert execution paths, not discovered keys. Omitted auto-increment keys are not automatically tracked. Zero-row tables do not create cleanup candidates.

Connection fingerprints detect a different endpoint/database; without a server ID, `strength=CONNECTION_SCOPE` does not identify physical database replacement at the same address. `trackable=true` means the original snapshot is complete, not that cleanup may skip preview, reference/change checks or business acceptance.

These read APIs do not delete data. Current source separately provides [request recovery](request-recovery.en.md) and [explicit cleanup](batch-cleanup.en.md); later derived-record ownership remains separate. Older generated rows have no journal and cannot be adopted by guessing. Low-level embedded GenerationEngine constructors without the journal dependency do not provide tracking.
