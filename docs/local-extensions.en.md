# Local field extensions

[简体中文](local-extensions.md)

Small binary values may use `{"$base64":"encoded bytes","sha256":"digest"}` instead of one file per value. Each decoded inline value is limited to 1 MiB. Invalid encoding, hash mismatch, per-value/aggregate limits or the existing 16 MiB output JSON limit cause rejection and rollback. Larger values can keep using `$binary` files. The public order demo uses inline values; the original document demo keeps file output.

Every generated field uses the existing `GEN_DATA` engine. Prefer ordinary UUID, input, date, condition, existing-data and relationship rules. Configure `EXT.FIELD` only for a field those rules cannot express. The manual UI and saved-scheme **Use** flow remain available. The [stdio MCP adapter](mcp.en.md) calls the same engine.

Trusted local extensions are disabled by default. They execute with the OS user's permissions; a separate process is not a security sandbox.

```sh
java -jar faker-data-0.1.1.jar --faker.extensions.enabled=true --faker.extensions.root=/absolute/path/extensions
```

Create `demo-field-codec/` under the extension root, copy the [example script](../examples/local-extension/generate.py) into it, and add `extension.json`. Replace the executable with a real absolute path; Windows JSON paths may use `/`.

```json
{
  "command": ["/absolute/path/python3", "${extension}/generate.py", "${input}", "${output}"],
  "allowedFields": {"demo_extension_payload": ["payload"]},
  "timeoutSeconds": 60
}
```

Only the complex column selects an extension:

```json
{"name":"payload","javaType":"byte[]","nullable":0,"regs":[{
  "code":"EXT.FIELD","extension":"demo-field-codec","extensionParameters":{"formatVersion":1}
}]}
```

See the full [ordinary generation request](../examples/generate-field-extension.json) and [MySQL schema](../examples/local-extension/schema.mysql.sql). Create the tables in a separate test database and submit through `POST /publish/task/GEN_DATA`, supplying credentials securely. Poll the same task ID; never blindly resubmit an ambiguous task. This feature is in current source builds and may be absent from older published JARs.

## Protocol

Ordinary rules first generate fields and resolve relationships. A task with extension fields buffers these values before insertion. The child receives protocol `faker-field-extension/1`, `taskId`, `tables` containing the generated context, and `fields` containing requested table/row/column addresses and parameters. Unresolved extension fields appear as `null`. Database connection credentials are not passed to the process.

Return exactly the requested addresses in `values`; returning tables, extra rows, ordinary fields, duplicate addresses or missing fields is rejected:

```json
{"values":[{"table":"demo_extension_payload","rowIndex":0,"column":"payload",
  "value":{"$binary":"payload-0.bin","sha256":"64-character SHA256 hex digest of the file"}
}]}
```

Values may be scalars, `null`, `{"$timestampMillis":1790000000000}` or binary references. Binary files must be inside the job directory and match their hashes. An extension must be the column's only top-level rule; it cannot be concatenated or used as the source of an ordinary reference. It can read ordinary generated values, but cannot replace them. The existing JDBC writer inserts all resulting rows in one target transaction; transactional tables such as InnoDB are required for rollback.

Ordinary tasks retain streaming batch writes. Field-extension tasks buffer up to 100,000 rows and 1,000,000 cells, with at most 64 MiB estimated ordinary string storage, 16 MiB input context JSON, 16 MiB output JSON, 16 MiB per binary and 64 MiB total binary. Timeout is 1–600 seconds. The process environment is reduced, but code retains local machine access.

When enabled, `data/field-extension-jobs/{taskId}/rule-audit.json` records actual per-column rule execution counts without generated values. Ordinary field conditions may use `#__rowIndex`, the zero-based table row index; parent-row filtering does not supply this variable.

Save, load and adjust ordinary schemes through the existing APIs. `GEN_DATA` has no request idempotency key; verify prior results before rerunning. Dynamic rules such as UUID run again, while fixed input keys can still collide. Validate actual application details, submission, approvals and ledgers rather than relying on state numbers alone. The example uses its own demo binary format, not a workflow-engine codec.
