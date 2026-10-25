# v0.1.1 — Root URL, homepage A and generation fixes

## 中文

下载 `faker-data-0.1.1.jar` 后运行：

```sh
java -jar faker-data-0.1.1.jar
```

打开 **http://127.0.0.1:8085/**，不需要 `/faker/`。

相比 v0.1.0：

- 使用选定的首页 A 主题，保留中英切换、连接与规则交互。
- 修复图标字体在资源复制时被文本转码的问题，并加入字节完整性回归测试。
- 数据库连接返回脱敏的具体原因，避免同一失败重复提醒。
- 修复跨批关联与树形进度；规则预编译、行容器与 JDBC statement 复用降低重复开销。
- 保留整任务事务回滚、默认内存缓存、可选 Redis、SQLite 方案与任务记录。
- 统一源码、启动脚本、内置 AI 指南和版本标识。

先停止旧应用再启动新 JAR。沿用原工作目录，或通过 `FAKER_DATA_DIR` 指定原数据目录；不要并行共享同一数据目录。旧 v0.1.0 附件仍保留 `/faker/` 地址。

验证包括前端测试、Java verify、真实 JAR 独立启动、根路径 API 和字体资源完整性。Oracle／达梦真实环境兼容、所有规则组合与生产规模吞吐仍需专项验收。

## English

Run `java -jar faker-data-0.1.1.jar`, then open **http://127.0.0.1:8085/**.

This release adds homepage A, fixes icon-font resource corruption and duplicate error notifications, provides actionable redacted database diagnostics, and includes cross-batch reference and tree-progress fixes with rule/JDBC preparation improvements. Existing transaction rollback and local application storage are preserved.

Stop the older process before upgrading. Keep the working directory or set `FAKER_DATA_DIR` to retain the intended application data. Never share one data directory between concurrent instances. Older v0.1.0 downloads retain the `/faker/` prefix.

Frontend/Java checks and isolated packaged-JAR smoke checks cover this release. Live Oracle/Dameng acceptance and production-scale throughput are not claimed.
