# faker-data rule guide

Use the existing database MCP to understand schema and business relationships. This adapter does not investigate databases or infer workflows automatically. Every target field uses the Java rule engine. UI and MCP share normal schemes.

Workflow: read this resource -> inspect business schema -> validate_generation(tables) -> save_scheme(name,tables,connection) -> generate_data(scheme_id,connection,confirm_write=true) -> get_task(task_id) until FINISHED or ERROR. Obtain write authorization for the intended target; existing authorization does not need to be asked again. Do not retry timed-out writes blindly. Multiple states are separate schemes/tasks, with separate transaction boundaries.

Each table: name, size (non-negative), columns. Each column: name, javaType, nullable (0 required/1 nullable), regs (ordered rule list). Multiple rules concatenate text. An empty list generates null. Common Java types: java.lang.String, java.lang.Long, java.lang.Integer, java.math.BigDecimal, java.sql.Timestamp (milliseconds), byte[] (extension).

- UUID: {"code":"FIX.UUID"}
- Constant: {"code":"RES.INPUTAREA","inputs":[{"value":"SAVED","weight":1}]}
- Sequence: {"code":"REG.INCREMENT_NUM","minSize":1,"step":1}
- Random integer: {"code":"REG.RANGE_NUM","minSize":1,"maxSize":100}
- Regex: {"code":"REG.STYLE","styleReg":"[A-Z0-9]{16}"}
- Existing/generated reference: {"code":"REF.OTHER_TABLE","refOtherTableColumn":["users","id"]}. Default rotates source rows; other fields referencing that source follow the same row.
- Exact reference: add "refMatch":{"document_id":{"field":"document_id"},"role":{"value":"INPUT"}}. Keys name source columns; field names a current-row column; value is a scalar constant (including null). All predicates are equalities joined with AND. Default: no match or multiple matches fails. Optional "refOnMissing":"NULL", "refOnMultiple":"FIRST" (stable source order). Target dependencies must be acyclic. Matching columns are indexed once per prepared rule/task; it does not query each target row.
- Same-row reference: {"code":"REF.SELF_COLUMN","refSelfColumn":"id"}
- Conditional: {"code":"COND.EXPR","condition":"#state == 'SUBMITTED'","onTrue":[...],"onFalse":[...]}. #__rowIndex is the zero-based index. Prefer compact mappings over huge sample-dependent condition chains.
- Read-only source table: "existingData":{"where":{"enabled":1},"orderBy":["id"],"maxRows":10000}. Explicit columns, no generation rules on source columns. Same DB connection; no INSERT into source. No arbitrary SQL/JOIN. Read once per task; not an automatic database investigation feature.
- Children: "parentRows":{"table":"documents","rowsPerParent":2,"condition":"#state == 'SUBMITTED'"}. Condition optional; references to the parent bind to its current row.
- Special field only: {"code":"EXT.FIELD","extension":"installed-id","extensionParameters":{}}. Must be the field's only rule. Requires trusted locally installed code and enabled extensions. It returns that field's value, not tables or SQL. Do not use extensions for fields expressible with ordinary rules.

validate_generation is configuration-only: it does not verify DB constraints, candidates, extension availability or real workflow validity. FINISHED is not proof a target application can submit/approve the generated records. Use appropriate business acceptance checks.

Local connection profiles are supplied by FAKER_CONNECTIONS_FILE. Never put passwords in tables, rules, examples or chat. Saved UI schemes store connection data locally; protect the Java data directory. Extensions are trusted local code, not a sandbox. No cleanup tool is exposed.
