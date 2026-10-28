# Advanced manual configuration

[中文](manual-advanced.md)

Applying read-only mode clears candidate-field generation rules and write-ownership declarations; the dialog explains this before applying. Opening table options refreshes full column metadata, so fields omitted from a projection can be selected again without removing/recreating the table.

After connecting to a test database and selecting tables, open **Rows & links** beside a table on the rule configuration page. Existing ordinary rules, tree settings, scheme **Use**, and Chinese/English switching remain available.

## Row sources

- **Generate new rows** uses the table count or its existing tree configuration.
- **Read existing rows** lets you choose source columns, AND equality filters, stable ordering and a source row limit. Source tables execute no generation rules, receive no inserts and are not owned by the generated batch. Prefer only the scalar columns needed for references; existing binary columns are unsuitable source projections. Unquoted text stays a string; booleans, numbers and null follow JSON scalar syntax. Use JSON strings for large integers, exact decimals and numeric identifiers, such as `"9007199254740993"` or `"001"`.
- **Expand parent rows** chooses a parent table, rows per parent and an optional parent condition such as `#state == 'SAVED'`. Condition fields belong to the parent. Configure ordinary cross-table references separately for physical child relationship IDs.

Tree configuration cannot be combined with the other two source modes. Disable it before switching. Backend preflight and generation remain authoritative for capacity, types, columns and dependencies.

## Virtual fields and exact references

Add virtual fields through Rows & links, then configure ordinary rules in the expanded field list. They can provide role markers, parent keys, conditions and references without being inserted. Read-only sources do not support virtual fields. After removing a referenced virtual field, update dependent rules and run preflight again.

Under **Reference fields → Other-table fields**, choose the referenced column. The match helper adds a source-table column equal to a current-row field or a fixed value. The JSON below remains editable; adding the same source column replaces its condition. Specify missing/multiple-match policies as needed. Prefer ordinary rules for supported fields. Use the existing **Field extension** editor only for unsupported fields, specifying a trusted installed extension and JSON parameters. Sharing a scheme does not install extensions.

## Preview, preflight and sharing

**Preview sample** displays bounded sample tables and virtual fields without target writes or extension execution. **Database preflight** checks metadata and statically verifiable values. Neither proves full-volume correctness, dynamic defaults, triggers or business workflows. Expand the complete diagnostics and limitations for details.

**My saved schemes** keeps Use/Delete and adds Export/Import shared scheme. Export omits connections and replaces some private configuration with local binding placeholders. Before importing, fill your local connection in the home form, paste the shared JSON and a private value object keyed by its `requiredLocalValues` pointers; use `{}` when no values are required. Review extensions/advanced expressions before explicitly trusting them. Import only saves configuration, does not generate, and rejects an existing name by default. Enter private values locally; do not send them to the AI or commit them.

Review business names and ordinary text before sharing: masking is not arbitrary PII detection. See [portable contracts](portable-schemes.en.md) and [preview/verification limits](preview-verification.en.md).
