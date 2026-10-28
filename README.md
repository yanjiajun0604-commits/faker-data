# faker-data

Current-source setup: [unified Java + MCP launcher](docs/launcher.en.md), with isolated dependencies, client configuration and diagnostics. Older Release packages may not include it.

Current source provides [built-in read-only database inspection](docs/database-inspection.en.md) through three MCP tools, plus database preflight and the original five generation/scheme tools. A separate database MCP is optional. This is a source preview, not a claim about an older downloaded JAR.

Current-source preview: [AI MCP setup](docs/mcp.en.md), exact correlated references, and a portable [order demo](examples/create-order-schemes.py). These changes are not included in older release JARs.


> **Access URL:** v0.1.1 and current source builds use `http://127.0.0.1:8085/`, without `/faker`. Older v0.1.0 downloads retain `http://127.0.0.1:8085/faker/`. See [access URLs and version compatibility](docs/access-url.md).

### Test data that belongs together.

**A local visual studio for relational test data — run one JAR, define your rules, and fill your test database.**

**English** · [简体中文](README.zh-CN.md) · [Download](https://github.com/yanjiajun0604-commits/faker-data/releases/latest) · [AI / API guide](AI_USAGE.en.md)

[![Build and test](https://github.com/yanjiajun0604-commits/faker-data/actions/workflows/ci.yml/badge.svg)](https://github.com/yanjiajun0604-commits/faker-data/actions/workflows/ci.yml)
[![MIT License](https://img.shields.io/badge/license-MIT-2a7d59)](LICENSE)
[![Java 8](https://img.shields.io/badge/Java-8%2B-64796a)](pom.xml)
[![Local first](https://img.shields.io/badge/local-first-387b61)](README.md#how-it-runs)

Random names are useful. Orders linked to the right users, a department tree with valid parents, and discounts that follow membership rules are useful **together**. faker-data lets you describe those scenarios in a visual workspace and write the generated rows into your existing test tables.

![The actual faker-data homepage](docs/images/homepage-a-desktop.jpg)

## From empty tables to useful scenarios

| You need | Use faker-data to |
| --- | --- |
| Data for API testing, integration, or a demo | Generate rows with field-level rules rather than hand-written INSERT scripts |
| Related users, orders, or other parent/child records | Generate parent rows and reference them from child tables in the same task |
| Organizations, departments, or menus | Build tree structures with parent references and optional hierarchical codes |
| Business-dependent values | Combine conditional rules, weighted inputs, and references to other fields |
| The same setup next time | Save a local generation scheme and reuse it |
| An AI-driven workflow | Let a local AI agent inspect metadata, prepare rules, submit a task, and poll the HTTP API |

Supports **MySQL, Oracle, and Dameng** as target databases. Switch between **English and 简体中文** in the top-right language selector. Your choice is remembered, and changing language preserves the current connection, rules and running task. The default follows your browser language. English documentation, JSON examples, and an English API guide are included.

Current source also includes opt-in [local field extensions](docs/local-extensions.en.md): ordinary fields use existing rules, while only complex fields invoke trusted local code. Saved schemes retain the Use flow and field editor. A [stdio MCP adapter](docs/mcp.en.md) calls the same engine. Target-specific acceptance testing is required.


## A closer look

<details>
<summary>Field rules, cross-table references, and same-row references</summary>

![Visual field-rule configuration](docs/images/field-rules.png)

![Cross-table reference editor](docs/images/cross-table-reference.png)

![Same-row reference editor](docs/images/same-row-reference.png)

These screenshots demonstrate the editing interface, not a recommended field mapping. Use the tested JSON examples for runnable configurations; do not create circular table dependencies.

</details>

## Run it. No source build required.

1. Download `faker-data-0.1.1.jar` from [Releases](https://github.com/yanjiajun0604-commits/faker-data/releases).
2. With Java installed (Java 8 verified), run:

```sh
java -jar faker-data-0.1.1.jar
```

3. Open **http://127.0.0.1:8085/**. Older v0.1.0 downloads use `/faker/`; v0.1.1 uses the root URL.
4. Connect your authorized test database, select existing tables, configure field rules, and generate.

**The application starts without a MySQL server, Redis, Node.js, or an OpenAI API key.** Your own target database is still required to actually insert generated data. AI integration is optional; the application does not contain a built-in LLM or translate prompts on its own.

## One tool, three workflows

- **Visual:** connect → select tables → set rules → generate and monitor.
- **Repeatable:** save a scheme locally, load it, and adjust the next run.
- **Agent-driven:** person → local AI agent → HTTP API → test database. Give your agent [AI_USAGE.en.md](AI_USAGE.en.md) or the [full Chinese guide](AI_USAGE.md).

```text
“Generate 20 users and 50 orders referencing those users.
 Give VIP users an 80% price factor and other users 100%.”
       ↓ your local AI agent reads the guide and calls the API
metadata → generation rules → task ID → confirmed completion
```

Complete examples: [relational data](examples/generate-demo.json), [tree data](examples/generate-tree.json), [MySQL sample schema](examples/mysql-demo-schema.sql), and a [standard-library Python client](examples/ai_client.py). These examples target their own demo tables; inspect your actual schema before using them elsewhere.

For a configuration-only document example with exact state counts, shared keys and line items, see [document data recipes](docs/document-recipes.en.md). State labels do not create operational workflow instances. Current source also adds [existing-data references and parent expansion](docs/existing-data.en.md) through JSON configuration.

## Rules that fit real test cases

- UUIDs, Chinese names, phone numbers, identity-shaped strings.
- Integer ranges, incrementing sequences, string lengths, regex patterns, and date ranges.
- Constants and weighted candidate values.
- Same-row field references and references to newly generated parent rows.
- SpEL conditions and helper functions.
- Tree parent IDs and hierarchical codes.

Multiple rules on one field concatenate their output. Cross-table dependencies are sorted before execution; circular dependencies are rejected. These are synthetic values, not real identity records.

## How it runs

The JAR bundles the Java backend and built Vue frontend. SQLite stores schemes and task metadata in `./data/faker-data.db`. A bounded in-process queue runs tasks; a task-scoped memory cache stores cross-table reference values by default. No private Maven snapshot artifacts are required.

The default queue has 2 workers and 100 waiting slots. The memory reference cache defaults to 500,000 entries / an estimated 128 MiB. It fails explicitly when full rather than evicting values still needed by another row. This is a reference-cache estimate, not a total JVM heap limit; tree state, row IDs, and JDBC buffers also use memory.

Optional Redis mode can offload reference values:

```sh
java -jar faker-data-0.1.1.jar --faker.cache.backend=redis --faker.cache.redis-host=127.0.0.1 --faker.cache.redis-port=6379
```

Redis mode still uses a **single-process task queue**, not distributed scheduling. See [configuration and design](docs/portable-design.md) and the [Chinese configuration table](README.zh-CN.md#内存缓存和可选-redis) for environment variables.

Change the port without rebuilding the frontend:

```sh
java -jar faker-data-0.1.1.jar --server.port=8095
```

## Build from source

Backend source: `src/main/java/`. Frontend source: `frontend/`. Built web assets are included, so a backend-only build does not require Node.js.

Windows:

```powershell
.\run.cmd
```

Linux / macOS:

```sh
chmod +x mvnw run.sh
./run.sh
```

The start script builds the JAR with Maven Wrapper if it is absent, then runs it. After changing source, rebuild explicitly:

```sh
./mvnw test
./mvnw package
java -jar target/faker-data-0.1.1.jar
```

To rebuild the UI (Node.js and npm required):

```sh
cd frontend
npm ci
npm run build
cd ..
./mvnw package
```

The wrapper downloads public Maven dependencies on its first run. No local `bbs` checkout or private artifact registry is needed. Java 8 and Node.js 24 were verified locally; platform-specific startup should still be checked on your own system.

## Verification and limits

The release has been checked against an isolated **MySQL 8.0.31** instance through the actual HTTP API in memory and Redis modes: metadata discovery, scheme save/load, relational data, same-row references, conditional rules, date/BIGINT/DECIMAL values, tree generation, rollback on a child-table constraint failure, and restart persistence. See [verification details](docs/verification.md).

Unit/integration tests use SQLite and H2 without requiring an external target database. Optional Redis tests run only with an explicit `-Dfaker.redis.test.port=...` value. Oracle and Dameng drivers are included from public Maven coordinates; their live compatibility was **not** verified in this release.

The tool inserts into existing tables. It does not create a schema, clear old data, or automatically avoid existing unique values. Increment sequences restart per task. A task reaches FINISHED/100 only after the target transaction returns successfully; interrupted tasks are marked failed on restart and are not replayed automatically. Use transactional target tables when relying on rollback.

## Local use and credentials

The server binds to `127.0.0.1` by default. It has no authentication and should not be exposed directly to the Internet. Saved schemes contain the supplied connection credentials; protect `data/` and do not commit real request files, database files, or passwords. Use only databases you are authorized to write to.

## Help shape the project

Found a reproducible issue or need a rule for your workflow? [Open an issue](https://github.com/yanjiajun0604-commits/faker-data/issues/new/choose) with a small, redacted example. Contributions that improve database compatibility, agent workflows, usability, and reproducible tests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

If the tool saves you time, a star helps other developers discover it. Real usage reports and small reproducible examples help improve it even more.

## License

[MIT](LICENSE) for project-owned code. Dependencies keep their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md).
