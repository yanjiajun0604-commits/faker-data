# Release verification / 发布验证

## Environment and scope

Validated locally on Windows 11 x64, Amazon Corretto Java 8u372, Maven 3.9.11, Node.js 24.18.0, MySQL Community Server 8.0.31, and an isolated Redis 5.0.14.1 instance. Application state used fresh SQLite directories. The target MySQL test instance used a separate port and data directory; existing application databases and Redis keys were not used.

The public Maven dependencies were resolved through a new local repository and explicit settings without a private artifact mirror. The official Maven Wrapper was also checked from a fresh wrapper distribution directory. npm dependencies were installed from the public registry.

## Behavioral tests

49 automated tests passed with the optional Redis tests enabled. Normal tests use SQLite and H2 and do not require an external target MySQL server. Coverage includes:

- task-isolated reference values, nulls, replacement accounting, limits, concurrent tasks, and cleanup;
- SQLite scheme insert/update/delete, stable scheme identity, and persisted records;
- waiting/running/success/failure states, queue capacity, task-ID isolation, error redaction, and completion after execution returns;
- actual generation-engine cross-table references and transactional rollback in H2;
- JDBC Long/BIGINT and LocalDateTime/date binding and DECIMAL NULL handling;
- existing MySQL metadata filtering, conditions, incrementing rules and tree topology;
- release of completed-task increment counters without resetting another task;
- opt-in Redis serialization, task isolation, TTL and selective deletion.

## Real HTTP API and MySQL

The bundled application was run in both memory and Redis modes. The included AI client and JSON examples were used against actual MySQL through the HTTP API to verify:

1. target connection, table list and column metadata;
2. saving/loading generation rules;
3. 20 parent records and 50 children, with no dangling references;
4. same-row surname references, VIP/normal condition values, dates, BIGINT sequences and DECIMAL constants;
5. a 7-node tree with one root and valid parent references;
6. a deliberate duplicate child primary key after parent generation, with the whole InnoDB transaction rolled back;
7. scheme/task persistence after application restart and failure marking for interrupted waiting/running records;
8. HTML, JavaScript and CSS served from the JAR.

Browser checks exercised connection, table selection, saved-scheme loading, field expansion, the rule editor, task submission and the FINISHED result with its task ID. Actual target counts and referential integrity were checked separately. The 390-pixel responsive layout had no horizontal page overflow; browser console checks showed no frontend errors in the exercised path. These are scoped checks, not a guarantee that every database type, rule combination or browser is fault-free.

## Reference-cache microbenchmark

One local run, after warmup, performed 2,000 string writes and 2,000 reads plus task cleanup per round using the actual implementations:

| Round | Memory | Local Redis |
| --- | --- | --- |
| 1 | 1.517 ms | 713.034 ms |
| 2 | 1.480 ms | 582.544 ms |
| 3 | 1.577 ms | 448.727 ms |

This measures only reference-cache operations. Redis uses synchronous network calls and refreshes TTL on write. It does not measure complete data generation, remote databases, many users, large trees, or production throughput. It supports choosing memory as the convenient local default; it does not establish an end-to-end speed multiplier. Redis remains useful to move reference values out of the Java heap.

## Not verified

- Live Oracle and Dameng database compatibility, although their public driver artifacts are packaged.
- macOS/Linux runtime acceptance, until the corresponding CI/platform run is actually observed.
- Production-scale throughput, long-running mixed workloads, or arbitrary binary/custom JDBC types.
- Public adoption, download popularity, or OpenAI program eligibility.

No production deployment, original Gitee checkout commit, or original repository push was performed during these checks.

## English and Chinese interface

The bundled JAR was checked on port 18087 with a separate SQLite state directory and an isolated MySQL fixture. English/Chinese switching, saved language after reload, translated saved schemes and rule labels, English calendar weekdays/months, required-field prompts, and retained edited rule values were verified in the browser. Both languages fit a 390px viewport without page-level horizontal overflow. Four frontend locale tests passed.

One browser generation was submitted in English, task `136c58d8-a79d-4eec-aae8-039d16f13d5f`, then viewed in Chinese and English. The HTTP task response confirmed FINISHED at 100%; the identifier remained unchanged across language switches. Earlier Java/MySQL coverage above remains applicable because no Java logic changed for UI translation. Vendor diagnostics and user-entered data are preserved in their original form; generated Chinese names and phone/ID formats are not changed by UI language.
