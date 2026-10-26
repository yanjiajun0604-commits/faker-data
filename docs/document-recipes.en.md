# Configure document data with existing rules

[简体中文](document-recipes.md)

faker-data is a general-purpose relational data generator. Users supply table names, fields, state values and relationships as configuration. The product does not embed a company's document model or workflow. An AI agent should inspect target metadata and business constraints before preparing a reusable generation recipe.

## Verified recipe

The [request JSON](../examples/generate-document-states.json) and [MySQL example schema](../examples/mysql-document-schema.sql) demonstrate one task generating:

- 170 `demo_document` rows: exactly 50 `SAVED`, 20 `SUBMITTED` and 100 `APPROVED`.
- 170 `demo_document_extension` rows sharing the corresponding document primary keys.
- 340 `demo_document_line` rows: exactly two per document, each worth 12.50, matching the fixed header total of 25.00.
- Matching state copies in the extension and line tables, selected from the same parent row as the referenced document ID.

These states are ordinary strings in an example schema, not a universal approval protocol. The example verifies rows, counts, references and amounts. It does not create operational workflow instances, inbox tasks or approval history, nor demonstrate that another application can submit or approve these records.

No business adapter or new generation rule is required:

| Requirement | Existing configuration | Boundary |
| --- | --- | --- |
| Unique keys | `FIX.UUID` | Does not replace an application's numbering protocol |
| Document number | Constant `DOC-` concatenated with a `REF.SELF_COLUMN` sequence reference | Sequences restart per task; adapt to actual uniqueness constraints |
| Exact state counts | Increment from 1; nested `COND.EXPR` rules using `between(#sequence_no,1,50)` | Weighted candidates are random, not exact quotas |
| Shared-key extension | `REF.OTHER_TABLE` to the document `id`, with equal row counts | Alignment uses generation order |
| Two lines per document | Set line count to twice the document count | References cycle by row index modulo parent count; arbitrary per-parent cardinality is not supported |
| Matching referenced fields | Reference both `id` and `state` from the same parent table | Both values come from the same parent row |
| Consistent totals | Fixed line amount 12.50 and header total 25.00 | Precomputed constants, not child-row aggregation |

## Agent execution

1. Establish the authorized database and write scope. Create example tables in an isolated test database if needed; the generation API does not execute DDL.
2. Inspect actual metadata using `/db/showTables` and `/db/showColumns/{table}`. Preserve real types, lengths and nullability when adapting to another schema. The supplied JSON matches only the example tables.
3. Configure the target connection and replace `DOC-DEMO-CHANGE-ME` in the JSON with a run-specific marker. Supply the password through the local `FAKER_TARGET_PASSWORD` environment variable. The marker helps locate records; it does not provide idempotency or automatic cleanup.
4. Follow the [AI integration guide](../AI_USAGE.en.md) and use the existing `GEN_DATA` endpoint. The supplied client supports this recipe:

   ```sh
   python examples/ai_client.py --request examples/generate-document-states.json
   python examples/ai_client.py --request examples/generate-document-states.json --submit --timeout 300
   ```

   The first command checks the connection and reads metadata. Only the second submits a task. After a submission timeout, reconcile the original task and target rows before retrying.

5. Poll the same task UUID until `FINISHED` with progress 100. Separately query state counts, orphan references, lines per document and amount totals using an authorized database query tool. The API has no arbitrary SQL query or batch cleanup endpoint.
6. To reuse the recipe, save `{name,dataSource,tables}` through `/scheme/save`. Saving configuration does not generate or validate business records. Saved schemes store connection credentials locally; protect the local files.

Application tabs, detail views and state transitions require separate validation against the target application's actual rules.

## Capabilities and gaps

| Requirement | Current capability | Potential general-purpose extension |
| --- | --- | --- |
| Fixed related row counts and multiple states | Expressible with current rules; covered by this local integration test | Reuse configuration first |
| Reference existing users, organizations or definitions | Current source adds `existingData`, read once and referenced by whole records | See [existing datasets](existing-data.en.md); not in older JARs |
| Generate child rows only for `SUBMITTED` parents | Current source adds `parentRows` filtering and fixed-count expansion | Generates rows, not operational engine tasks |
| Variable lines per document or one row per approval node | Fixed-count parent expansion is available; workflow definitions are not parsed | Variable cardinality, per-parent sequences and node-set expansion remain proposals |
| Header totals computed from details | No cross-row aggregation rule; consistent constants are possible | Aggregation and generated-data constraints |
| Operational instances based on an existing workflow definition | The engine inserts supported field types; it does not execute state machines or construct proprietary serialized runtime state | Verify the required data protocol first; status values cannot substitute for execution |
| Trace and undo generated rows safely | No generation manifest or automatic undo | Primary-key manifests, dependency checks and scoped cleanup |

The explicitly marked `existingData` and fixed-count `parentRows` features are implemented in the current source; other extensions remain proposals. Core capabilities use configuration without embedding a target system's tables, APIs or approval logic. Operational workflow correctness remains a separate acceptance criterion.

Local tests use temporary H2 databases and the real generation engine. They do not connect to an external business application. Actual target database and application acceptance require separate testing.
