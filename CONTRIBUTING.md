# Contributing / 参与贡献

Thanks for trying faker-data. Useful contributions include small reproducible bug reports, database-version compatibility checks, generation rules, English UI support, and improvements to agent workflows.

## Report a problem

Use the issue template. Include the JAR/version, Java version, target database/driver version, expected result, actual task state, and a minimal redacted schema/request. Never attach passwords, personal/business data, or your application SQLite file. State whether the issue reproduces in memory mode, Redis mode, or both.

## Develop

1. Fork and branch from main.
2. Keep application SQLite storage separate from the target test database.
3. For a backend change run `./mvnw test`; add a behavioral regression test for a reproducible logic bug.
4. For a frontend change run `npm ci && npm run build` inside frontend; include its rebuilt static assets and verify the affected workflow.
5. Update English/Chinese user instructions or the AI guide if the API or behavior changes.
6. Explain the problem, final behavior, and checks in your PR. Keep unrelated refactoring out of the patch.

Redis tests are opt-in via `-Dfaker.redis.test.port=<isolated-port>`. Do not run tests against a production database or another application's Redis keys. Do not replace unknown fields with fabricated schema details.

本项目欢迎中文 issue 和 PR。请提供可复现的小样例，去掉真实连接信息；涉及行为的修改补充相应验证。界面当前是中文，英文界面与跨平台验证都是有价值的贡献方向。

Contributions to project-owned source are under the repository's MIT license. Third-party components retain their own licensing.
