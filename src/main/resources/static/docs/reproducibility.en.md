# Seeds, name language and fixed time ranges

[中文](reproducibility.md)

Generated tables accept optional `generationSeed` (a safe integer) and `locale` (`zh_CN` or `en`) in ordinary schemes/MCP tables, portable documents or the manual Rows & links dialog.

```json
{"name":"orders","size":100,"generationSeed":42,"locale":"en","columns":[{"name":"id","javaType":"java.lang.String","regs":[{"code":"FIX.UUID"}]}]}
```

Algorithm version1 derives a cell stream from the seed, normalized table/column names and row index. Concurrent tasks, recursive self references and column reordering do not share or reset a global random generator. Preview and generation use the same cell stream. Keep the project version and full recipe for cross-run comparisons.

Supported built-ins include UUIDs, names, Chinese-format phones, numeric/string/date ranges, weighted inputs, increments, references, restricted conditions and tree rules. Locale affects **person names only**; English uses a finite first/last-name vocabulary. Weighted values provide category distributions, while fixed inputs/range endpoints can create boundary examples; small samples need not match exact weights.

With a seed, `FIX.IDENTIF`, `REG.STYLE` and custom regex rules fail explicitly with `SEEDED_RULE_UNSUPPORTED`; conditions use restricted built-in expressions without calls to external randomness/time. Existing unseeded rules remain available. Applying read-only source mode clears seed/name-language settings.

`REG.DATE.dateRange` already uses explicit **absolute epoch-millisecond endpoints**, not a moving “today”. Retain the same range and JVM/database timezone to compare time representations. Task timestamps, batch IDs and request IDs remain independent runtime records.

Seeds do not control changing source snapshots/order, database defaults/identities/triggers, arbitrary extension output, version/timezone/precision differences or business workflow behavior. Freeze source data and ordering; extension authors must make their own parameters, dependencies and algorithms deterministic. Continue verifying generated results.

Repeated seeds also repeat UUIDs, which can conflict in the same target table. **A seed is not request idempotency.** After timeout, inspect the original request/batch rather than resubmitting with a new request ID. Use separate empty databases or explicitly cleaned original batches for repeatability experiments.
