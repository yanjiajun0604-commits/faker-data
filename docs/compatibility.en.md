# Verification scope

[中文](compatibility.md)

| Scope | Evidence | Limit |
| --- | --- | --- |
| Windows / Java 8 / Python 3.11 | Packaged JAR, icon fonts, real stdio MCP, 19 tools and MySQL 8 lifecycle | Isolated local fixture; not every AI client |
| Linux / Windows, Java 8 / 17, Python 3.10 / 3.13 | `runtime-matrix` CI boots the same packaged JAR and reads tools, rules and contracts | Check the exact commit's CI results; not every possible combination |
| Linux / MySQL 8 | `native-mysql` CI exercises discovery, metadata, samples, preflight, preview, generation, idempotency, verification and exact cleanup through real MCP | Empty dedicated schema; three original customers remain and all four generated tables end empty |
| H2 | Java logic regression tests | Not native database acceptance |
| Oracle / Dameng | Existing adapters retained | Native isolated environments not provided for this round; not claimed as fully verified |
| macOS / external AI clients | Standard stdio configuration and migration instructions | No native macOS or external-client login acceptance in this round |

The `portable` job builds the artifact consumed by the matrix. Results belong to a commit; previous green checks do not validate later changes. SDK protocol compatibility does not establish a particular client's onboarding or long-running-task experience.

## Repeat locally

Use a Python environment with the pinned MCP dependencies:

```sh
python integrations/mcp/verify_runtime.py --jar /path/current.jar --report ./runtime-result.json
```

By default this does not connect to or write a target database. It uses a temporary private runtime and a free loopback port, and stops only its own Java child.

For actual database testing, initialize `examples/mysql-order-schema.sql` in an **empty dedicated MySQL schema**, configure a private `default` connection with the required cleanup metadata visibility, then run:

```sh
python integrations/mcp/verify_runtime.py --jar /path/current.jar --profiles /private/connections.json --confirm-demo-write --report ./native-result.json
```

This writes two batches of 12 rows and cleans each by its exact batch, preserving the customers. Failures do not expand cleanup. The temporary local journal is removed when this disposable fixture test ends; do not use it for business environments needing a retained investigation record. For normal use, keep the [persistent receipt and private home](order-lifecycle.en.md).
