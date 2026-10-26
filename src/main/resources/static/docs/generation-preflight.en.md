# Database generation preflight (source preview)

[中文](generation-preflight.md)

`validate_generation` / `POST /generation/validate` remains configuration-only and does not connect to the target. New `preflight_generation(tables, connection="default")` / `POST /generation/preflight` reads actual metadata and small fixed unique-key samples. It never generates values, inserts rows or executes extensions.

HTTP accepts the normal `{"dataSource":{...local connection...},"tables":[...ordinary scheme tables...]}`. Check business `code`, then `data.valid`, `errors`, `warnings`, `notChecked`. Proven constraint errors return code 0 with valid=false; connection/invalid-request failures use nonzero business codes. Issues expose `code/table/column/message`, not raw candidate values.

Checks include target table/column existence; missing required columns without reported defaults/auto-increment/generated values; writes to generated columns; static candidate NULLs, character lengths, integer ranges and decimal precision/scale; fully static non-null unique keys that necessarily repeat within a task or already exist. Composite unique indexes are supported. Dynamic, nullable, sensitive and expression-index values remain unproven. Metadata visibility depends on driver/account permissions.

Random, conditional, reference and extension rules are not executed. CHECK expressions and trigger/business semantics are not proven. `existingData` sources get column checks, not insertion requirements. Default presence is separate from its redacted content. The existing required-String binder can coerce NULL to an empty string; preflight warns about that behavior without changing it.

The selected database/schema is inspected. Existing qualified generation targets retain their behavior but receive `QUALIFIED_TARGET_NOT_CHECKED`; valid=true does not validate those targets. Always inspect warnings/notChecked as well. Each issue list is capped at 200; split larger configurations.

MCP `generate_data` automatically checks preflight before submission and refuses proven errors. The formal Java datasource generation entry repeats the check before writes. Embedded callers constructing the lower-level GenerationEngine must invoke preflight themselves. Concurrent changes can occur after preflight; the actual transaction and database constraints remain authoritative. FINISHED is not application/workflow acceptance.

Sequence: inspect schema/samples -> configuration validation -> database preflight -> authorized generation -> task terminal state -> row/reference/business acceptance. Older release JARs do not contain this endpoint.

MySQL write targets must use InnoDB; other engines are rejected in preflight and before journaled writes. Read-only candidates are exempt from this insertion requirement.
