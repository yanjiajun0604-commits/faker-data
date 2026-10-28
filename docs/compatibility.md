# 验证范围

[English](compatibility.en.md)

支持列表不等于每一条组合都经过真实业务验收。当前源码的证据分为：

| 范围 | 已做检查 | 边界 |
| --- | --- | --- |
| Windows / Java 8 / Python 3.11 | 打包 JAR、字体、真实 stdio MCP、19 工具、公开 MySQL 8 生命周期 | 本机隔离环境；不是全部 AI 客户端 |
| Linux / Windows，Java 8 / 17，Python 3.10 / 3.13 | CI `runtime-matrix` 启动打包产物、读取规则与契约、检查协议和字体 | 以指定提交的实际 CI 结果为准；不交叉穷举所有组合 |
| Linux / MySQL 8 | CI `native-mysql` 通过真实 MCP 发现表、读取结构与样本、预检、预览、生成、幂等、核查及精确清理 | 专用空 schema；保留三条原客户，四张生成表最终零行 |
| H2 | Java 逻辑回归测试 | 不代替原生数据库测试 |
| Oracle / 达梦 | 既有适配器保留 | 本轮未提供原生隔离环境，不声称已完整验收 |
| macOS / 外部 AI 客户端 | 提供标准 stdio 配置与迁移说明 | 本轮没有原生 macOS／外部客户端登录验收 |

仓库 Actions 的 `portable` 负责构建和基础检查，其后的矩阵读取同一 JAR。检查结果绑定提交；旧 CI 成功不能证明新改动。真实 SDK 握手只能证明协议兼容，不能证明某个客户端的安装和长任务体验。

## 本地重复验证

在已安装 MCP 依赖的 Python 环境执行：

```sh
python integrations/mcp/verify_runtime.py --jar /path/current.jar --report ./runtime-result.json
```

默认不连接目标数据库，不写目标数据，使用临时本地服务目录和空闲回环端口，结束后只关闭自有 Java。报告不含连接凭据；启动失败需要检查环境，不自动停止占用端口的其他服务。

完整原生测试：先在**独立空 MySQL schema**执行 `examples/mysql-order-schema.sql`，在本机私人文件中配置 `default` 连接，确保该测试账号满足清理元数据要求，再执行：

```sh
python integrations/mcp/verify_runtime.py --jar /path/current.jar --profiles /private/connections.json --confirm-demo-write --report ./native-result.json
```

该选项会真实生成两轮各 12 行并按批次删除，仅保留客户种子。失败时不扩大清理范围。测试的临时本地账本结束时会移除；它仅用于可丢弃的空验收 schema，不适合需要人工保留调查现场的业务环境。日常案例使用[持久回执与私人 home](order-lifecycle.md)。
