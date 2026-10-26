# Redacted scheme sharing and local bindings (source preview)

[中文](portable-schemes.md) · [Machine contracts](../contracts/README.md)

Use `export_scheme` for sharing. The legacy `/scheme/{id}` response/local SQLite database is a **private local backup**, contains connection configuration, and is not the sharing format.

## Export

MCP `export_scheme(scheme_id,connection_ref="local")`, or HTTP `GET /scheme/{id}/export?connectionRef=local`, returns a `faker-data.scheme` document with `formatVersion=1`. It preserves ordinary rules, virtual fields, existingData, parentRows, ownership and tree configuration, while dropping computed dependency flags. `connectionRef` is a public logical label, not a host/JDBC URL or credential.

Connection objects, executables and local extension manifests are excluded. Default redaction covers credential-named columns' rules, all existingData.where local selectors, sensitive match/predicate literals, detected saved-connection secrets/JDBC/auth/private-key/path strings, and opaque extension parameters. A small benign parameter subset (sourceField, binaryEncoding, role, formatVersion) may remain. Each redacted node becomes null and is listed in `requiredLocalValues` by an exact RFC6901 JSON pointer. Do not remove that list or fill placeholders by guessing.

Review business names, schemas and literals before publishing. Automatic masking is not general PII/business-secret detection. Advanced condition-expression text may remain and is marked for trusted-code review; no program file is bundled. Scheme import/export does not change the target database.

## Import without sending private values to the AI

1. Read the v1 document and its `requiredLocalValues`/`requiredExtensions`/`trustedCodeRequired`.
2. Bind an explicit private connection profile on the recipient machine. Old saved connection data is never imported.
3. If local replacements are required, copy [scheme-values.example.json](../integrations/mcp/scheme-values.example.json) **outside the checkout**, replace its pointers with those from the actual export, and fill values privately. Values must have the right JSON type: a redacted regs node needs a rule array; a redacted literal needs a scalar.
4. Set optional adapter environment `FAKER_SCHEME_VALUES_FILE` to that private file, alongside `FAKER_CONNECTIONS_FILE`. Select only its logical alias in MCP:

```text
import_scheme(document=reviewed_document, connection="test", name="Local order demo",
              values_profile="demo", replace_existing=false, confirm_trusted_code=false)
```

The tool has no replacement-value/password argument. The adapter reads replacements privately and posts them over loopback, like connection profiles. Do not send the values file to the AI, repository, screenshots or public examples. Missing bindings, unknown properties/versions, reserved serialized-type metadata and arbitrary extra patches fail instead of silently dropping settings.

Import validates and saves a normal scheme; it does not connect to the target, generate rows, install extensions or run rule/provider code. EXT.FIELD/custom implementations and expressions outside the reviewed pure subset require explicit `confirm_trusted_code=true` after authorization. The requirement is recalculated, not trusted from the file's boolean. This review gate is specific to import; formal generation remains trusted local execution, not a sandbox. Do not bypass review through raw save requests.

Same names conflict by default; use `replace_existing=true` only for an intended local overwrite. After timeout, inspect scheme IDs/names before retrying. Import returns a scheme ID; generation still needs its separate write authorization and stable request ID.

## Persistence, re-export and limits

Local Java schemes still contain their private connection and bound values; protect the data directory. Imported private pointer metadata is stored atomically with the scheme, so re-export masks bound values again. Validation uses a separate copy so planner sorting cannot shift those pointers. Deleting the scheme also deletes its pointer metadata. Legacy manual edits can move values; re-review the exported artifact after editing.

Export reads the scheme and its private pointer metadata from one local database snapshot, so concurrent replacement cannot mix versions and expose a formerly bound value.

MCP `get_scheme` redacts configured private value strings. Its redacted display is not a lossless editable backup: do not save it back to reconstruct a bound scheme. Use `generate_data(scheme_id=...)` to execute the locally stored configuration, and `export_scheme` for a portable document. Public rule-code names remain visible.

HTTP import: `POST /scheme/import` with document/dataSource/replacements/name/replaceExisting/confirmTrustedCode; private local UI may supply replacements directly. No source connection is inferred from the document. Payload: at most 2 MiB, 100 tables, 550 columns/table (virtual max 50), 1024 rules/list, 10000 private pointers, nesting depth 40 and 100000 JSON nodes. More semantic/runtime bounds apply. JSON schemas validate structure; DB preflight and actual application acceptance remain separate.

Contracts are available through `GET /contracts`, `GET /contracts/{published-name}`, or `faker://contracts`. Current source is required; this is not a claim that an older published JAR has these interfaces.
