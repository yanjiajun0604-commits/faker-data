# 统一启动与 MCP 配置

[English](launcher.en.md)

当前源码提供 `faker.py`，管理 Java UI 服务和 Python stdio MCP 适配器，不改 AI 客户端设置，不安装全局依赖。需要当前源码对应的 JAR；旧 Release 不一定包含所需 contracts v1。

## 首次安装

准备 Java 8+、Python 3.10+。从项目根目录执行（macOS/Linux 将 `python` 换成 `python3`）：

```sh
python faker.py setup --jar /absolute/path/to/faker-data-0.1.1.jar
python faker.py doctor
```

源码用户先通过 `mvnw.cmd -B package`（Windows）或 `./mvnw -B package` 构建，JAR 位于 `target/`。首次 setup 需要联网下载开源 Python 依赖。仅用界面可加 `--ui-only` 跳过 MCP 安装，也可继续直接 `java -jar`，不需要 Python。`doctor` 包含 MCP 依赖检查，UI-only 模式未安装适配器会明确报告。

默认私人目录为 `~/.faker-data`：

| 路径 | 用途 |
| --- | --- |
| `settings.json` | 本机 JAR、Java、端口、数据与连接文件路径 |
| `connections.json` | 私人数据库连接；只在本机编辑，替换初始 CHANGE_ME |
| `venv/` | 独立 MCP 依赖，可在换机时重建 |
| `data/` | SQLite 方案、任务、批次及扩展等服务数据 |
| `java.log` | 启动／运行日志，分享前先脱敏 |

这些文件不要上传 GitHub、发送给 AI 或作为公开示例。Windows 目录权限由系统继承，建议使用个人用户目录；POSIX 新建目录为 0700，配置文件为 0600。

多实例通过不同 `--home` 和端口隔离，之后每个命令都使用同一 `--home`：

```sh
python faker.py --home /private/faker-demo setup --jar /path/current.jar --port 18085 --java /path/to/java
python faker.py --home /private/faker-demo start
```

setup 不覆盖已有连接文件，也不静默修改不同的 settings；重复安装可修复依赖。改变 JAR 路径或端口时，明确编辑 settings.json，或使用新 home。`javaArgs` 可放额外 Spring 参数，例如 `--faker.extensions.enabled=true`；先审阅可信扩展。端口、监听地址、根路径和数据目录固定使用 settings，避免环境变量误指向其他服务。默认内存缓存，无需 Redis。堆默认 64–512 MiB；更大任务仍可自行配置 `java -jar` 并 attach。

## 人工界面

```sh
python faker.py start
```

打开输出的 `http://127.0.0.1:8085/`。Ctrl+C 仅关闭这次启动的子进程。端口占用则拒绝启动，不停止已有服务；改用独立 home／端口，或明确使用 `start --attach` 检查已有服务并显示地址。

## AI 客户端

```sh
python faker.py config
```

把输出 JSON 放到支持 stdio MCP 的客户端设置中；Codex TOML 使用 `python faker.py config --format toml`。输出为本机绝对路径，不含连接内容，不能原样移给另一台电脑。启动器不自动修改或重载客户端，需要在客户端启用／重连。

默认配置在客户端启动 MCP 时启动专属 Java，stdio 只承载协议，日志写 stderr／私人 java.log。客户端关闭标准输入后，适配器退出，启动器关闭它拥有的 Java。**同一端口只能由一个客户端拥有**，不要同时运行 `start` 和默认 MCP 配置。

请保持客户端连接直到生成任务完成；任务在 Java 中异步运行，提前断开可能中断生成。再次连接后先按原 requestId／batchId 核查，不盲目重跑。长任务或多个客户端共用时，建议手动启动服务并 attach。

需要共用手动启动的服务时：

```sh
python faker.py start
# 保持终端运行，在另一个终端生成配置：
python faker.py config --attach
```

attach 仅检查本机根地址的 contracts v1，退出时不停止服务。它不能证明现有服务与选定 JAR 的 Git 提交完全相同；数据目录、扩展开关和参数以实际服务为准。旧 `/faker` 地址不适用于此入口。[手动 MCP 配置](mcp.md)仍支持自定义适配器环境变量。

## 检查与迁移

`doctor` 检查 Python、Java、选定 JAR 版本／契约、固定 MCP 版本、真实适配器导入、端口和连接文件完整性；不连接目标库，不生成或删除数据。连接就绪不代表账号权限、业务约束或清理权限通过。错误时按提示修复，不盲目重试生成。

换电脑：复制源码和匹配 JAR；私人连接与 data 单独安全备份；安装 Java/Python，重新 setup 建立 venv，校正 settings 路径并重新生成客户端配置。不要复制旧 venv。日志和 SQLite 数据包含本机信息，不作为公开发行内容。
