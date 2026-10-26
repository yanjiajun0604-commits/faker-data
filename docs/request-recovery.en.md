# Idempotent generation requests and unknown-result recovery (source preview)

[中文](request-recovery.md)

Choose and retain a stable request ID before submission, e.g. `orders-demo-20261026-01`. Reuse it for retries of the same intended generation. Changed recipe/count/target is a different intent; never invent a new ID merely to bypass an UNKNOWN result.

## Submit and locate

- MCP: `generate_data(scheme_id=1, request_id="orders-demo-20261026-01", confirm_write=true)`. A caller-chosen ID is required by the adapter; existing rules still generate fields.
- HTTP: add top-level `"requestId":"orders-demo-20261026-01"` to `POST /publish/task/GEN_DATA`, or send `Idempotency-Key`. When both are present they must agree. IDs are 8-100 ASCII letters/digits/underscores/hyphens, starting with a letter/digit.
- If submission times out before returning taskId, call MCP `get_request(request_id)` or `GET /generation/requests/{requestId}`. found=true returns the original taskId; poll get_task and inspect get_batch.
- Same key/payload returns the original task, even if failed/unknown, without re-enqueueing. Changed payload returns `IDEMPOTENCY_CONFLICT`. Key claim and task creation share one SQLite transaction. The request record stores only a digest, IDs and timestamp, not the request body/password. Datasource password is excluded so password rotation does not create a new intent.
- Legacy HTTP requests without a key remain compatible and create a new task each time: **they have no idempotency guarantee**. UI request-ID integration remains a separate UI improvement. The stdlib client requires requestId or `--request-id` for submission.

```sh
python examples/ai_client.py --request private-demo.json --submit --request-id orders-demo-20261026-01
```

A key is not a random seed. Same key means the original task; different keys remain distinct requests even with identical seeds. Scheme saving/name replacement is outside this mechanism. Losing the local SQLite directory also loses request mappings; that does not make blind replay safe.

## Interrupted service or lost commit receipt

Startup never replays the old queue. Previously queued/running tasks become interrupted, and nonterminal batch receipts become UNKNOWN. Target commit and local journal are separate transactions: ERROR is not proof that nothing was inserted.

Locate the task with get_request and inspect its [batch journal](generation-batches.en.md). For UNKNOWN:

1. Observe with `reconcile_batch(batch_id, connection="default", confirm_commit=false)`, or `POST /batches/{id}/reconcile` with `{"dataSource":{...local connection...},"confirmCommit":false}`.
2. Read-only checks compare target identity/schema and every key/full-row digest. Only a complete pre-commit snapshot with all rows matching yields `observation=ALL_MATCHING, eligible=true`. Observation changes neither target data nor local receipt.
3. Use execution history to confirm these matching rows are this task's writes; explicitly call with confirm_commit=true. The tool checks again before marking the local receipt COMMITTED and corresponding ERROR task FINISHED. It does not insert again.

Missing/mixed/changed rows, schema changes, wrong targets, incomplete snapshots or untracked writes cannot be confirmed. MISSING_ALL remains UNKNOWN: invisibility alone does not rule out delayed commit. There is no automatic rollback conclusion, new-key retry or cleanup.

Connection-level identity cannot prove physical database replacement never occurred. Identical keys/content cannot distinguish identical external recreation, so confirmation requires execution history rather than automatic ownership inference. Normal recorded COMMITTED/ROLLED_BACK results do not need recovery.
