# Changelog

## 0.1.0 — Portable studio release

- Bundle the Java backend and Vue frontend in one executable JAR.
- Use local SQLite for schemes/task records; start without an application MySQL database or Redis server.
- Replace unavailable private snapshot dependencies with included task interfaces and a bounded local execution service.
- Add task-scoped memory reference caching, capacity checks, cleanup, and optional Redis caching.
- Keep task records across restarts and mark interrupted tasks failed instead of replaying writes.
- Add Chinese/English documentation, an AI HTTP API guide, runnable clients, and relational/tree examples.
- Redesign the web workspace, simplify rule labels, and show task IDs and query interruptions.
- Support JDBC BIGINT/Long and newer MySQL DATETIME metadata, correct DECIMAL NULL binding, and reject unknown Java types explicitly.
- Add regression checks for caching, queue limits, persistence, commit state, and generation/rollback.
- Release project-owned code under MIT.

Known boundaries: single-process task execution; no built-in LLM, schema creation, cancel endpoint, or automatic duplicate prevention. Oracle/Dameng live database acceptance remains to be verified. The current UI is Chinese.

## Earlier public snapshot

The first public snapshot contained backend source and existing tests only. It did not include a runnable frontend, a license, or a verified public-dependency build. This release replaces that startup path.

- English / 简体中文 UI switching, saved language preference, localized Element UI controls and AI guide links.
