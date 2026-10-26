# 通过 MCP 生成业务测试数据

[English](mcp.en.md)

MCP 适配器把现有 Java 生成器接给 AI 客户端。普通字段仍使用已有规则，特殊字段才使用本地扩展。人工 UI 和 MCP 共用普通方案与 `GEN_DATA`。需要使用包含 `/generation/validate` 的本轮源码构建版本；旧的已发布 JAR 不包含这次 MCP 配套改动。

## 安装与启动

需要 Java 8+、Python 3.10+，以及支持 stdio MCP 的 AI 客户端。在项目根目录执行：

```sh
java -jar faker-data-0.1.1.jar
```

另一个终端安装适配器。Windows：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r integrations/mcp/requirements.txt
```

macOS/Linux：

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r integrations/mcp/requirements.txt
```

把 `integrations/mcp/connections.example.json` 复制到仓库外的私人目录，填写测试数据库连接。示例中的密码是占位符。连接文件不要提交或发送给 AI；Java 数据目录中的已保存方案也包含连接配置，应当保护。

客户端的 MCP JSON 配置示例（替换两个路径为本机绝对路径；Windows 使用正斜杠）：

```json
{
  "mcpServers": {
    "faker-data": {
      "command": "C:/path/to/faker-data/.venv/Scripts/python.exe",
      "args": ["C:/path/to/faker-data/integrations/mcp/server.py"],
      "env": {
        "FAKER_BASE_URL": "http://127.0.0.1:8085",
        "FAKER_CONNECTIONS_FILE": "C:/private/faker-connections.json"
      }
    }
  }
}
```

macOS/Linux 的 command 改为 `/path/to/faker-data/.venv/bin/python`。不同客户端配置位置不同，请放到该客户端的 MCP 设置中。适配器仅通过 stdio 提供 MCP，调用本机 Java HTTP 服务，不开放新的网络监听端口。该版本不自动启动 Java 服务。

## AI 如何使用

可以向 AI 描述：

> 使用 faker-data 的 list_tables、describe_table、read_rows 阅读我的测试库结构。参考 faker-data 的 faker://rules，用已有规则生成订单和明细：保存 50、提交中 20、完成 100。客户引用库中启用的客户。先检查规则，普通字段不要写扩展，确认在我授权的测试库生成，并返回任务结果和验收查询。

AI 应先用内置只读工具理解表结构、关联及业务条件；不必额外安装 dbquery。读取 `faker://rules` 后，为三种状态分别准备普通方案。弱引用和业务状态仍需结合实际样本与业务说明判断，工具不自动推断。详见[数据库调查边界与 HTTP 示例](database-inspection.md)。

| MCP 工具 | 作用 |
| --- | --- |
| `list_tables(name_contains, limit, offset, connection)` | 当前数据库／schema 的普通表分页发现 |
| `describe_table(table, connection)` | 列、Java 类型、注释及驱动报告的主外键／索引 |
| `read_rows(table, columns, where, order_by, limit, connection)` | 参数化单表样本读取；默认 20、最多 100 行 |
| `validate_generation(tables)` | 只校验配置、规则和依赖，不连接目标数据库或执行扩展 |
| `preflight_generation(tables, connection)` | 真实元数据及固定值约束预检；检查 valid/errors/warnings/notChecked，无写入 |
| `save_scheme(name, tables, connection)` | 校验并保存普通方案；同名会更新，人工 UI 可继续使用 |
| `get_scheme(scheme_id)` | 读取方案规则；省略 ID 则列出名称与 ID，不返回连接凭据 |
| `generate_data(scheme_id, connection, confirm_write, request_id)` | `confirm_write=true` 表示处于用户已授权的写入范围，提交一次 GEN_DATA，立即返回任务 ID |
| `get_batch(batch_id, include_rows, offset, limit)` | 读取目标库提交／回滚／未知状态、实际主键与摘要；不清理 |
| `get_request(request_id)` | 超时后按稳定请求标识找回原任务，不重跑 |
| `reconcile_batch(batch_id, connection, confirm_commit)` | 只读核对 UNKNOWN 批次；明确确认时仅更新本机提交回执 |
| `preview_cleanup(batch_id, connection)` | 只读核对目标，保存有期限的本机清理计划；不删除 |
| `execute_cleanup(batch_id, plan_id, plan_hash, connection, confirm_delete)` | 明确授权后复查、事务清理和独立复核；未知状态不重试 |
| `get_cleanup(batch_id, plan_id)` | 读取原清理计划回执，不连接目标或重删 |
| `get_task(task_id)` | 查询状态、进度和脱敏错误 |

`connection` 是私有连接文件中的名称，默认 `default`。任务执行时使用明确选择的连接配置，不盲用方案中遗留的旧连接。校验结果的 `scope=CONFIGURATION_ONLY`，未检查项包括真实数据库约束、已有候选、扩展安装和业务工作流。数据库与业务验收仍需执行。

超时后写入结果可能未知，不要自动重试保存或生成；保存可以按名称核对，生成应保留已拿到的任务 ID 查询。若任务 ID 都未返回，先核对 Java 任务记录与目标数据。三种状态是三次独立事务，不承诺跨任务整体回滚。

## 可运行的通用案例

先在独立的空测试库执行 [订单示例 SQL](../examples/mysql-order-schema.sql)，再生成三个普通配置：

```sh
python examples/create-order-schemes.py --output order-schemes --counts 50 20 100
```

让 AI 读取输出文件中的 `tables`，通过上述 MCP 工具校验、保存、执行即可。候选客户表只读；每张订单生成两条明细、输入/输出各一条内容记录和一条引用记录。引用由当前订单 ID 与 INPUT/OUTPUT 类型精确匹配，普通 ID 无需扩展。

这些状态与引用用于演示数据关系，不代表真实业务工作流引擎。要生成某个系统可继续审批的数据，仍需分析并满足该系统的真实运行协议。

需要二进制演示时：

```sh
python examples/install-field-extension.py --root ./data/extensions --demo orders
python examples/create-order-schemes.py --output order-schemes-binary --counts 50 20 100 --with-binary
java -jar faker-data-0.1.1.jar --faker.extensions.enabled=true
```

安装命令只复制公开 Python 示例和生成本机 manifest，不启用或运行扩展；目标目录必须与 Java 的 `faker.extensions.root` 一致。安装器不覆盖已有扩展。二进制是公开演示格式，普通 UUID、状态及关联仍由规则引擎生成。扩展是可信本地代码，不是沙箱。

## 验证

```sh
python -m unittest discover -s integrations/mcp -p test_server.py -v
```

真实 stdio 协议测试需要独立空示例库和运行中的 Java 服务。显式设置 `FAKER_MCP_E2E=1`、`FAKER_BASE_URL`、`FAKER_CONNECTIONS_FILE` 后执行 `test_protocol.py`。它会写入并保留示例数据；默认不运行。测试中 H2 只能作为隔离夹具，不能据此宣称所有生产数据库兼容性。

生成前自动执行[数据库预检](generation-preflight.md)，不再只依赖配置级检查。

生成完成后读取[批次账本](generation-batches.md)，核对实际写入主键；batchId 等于 taskId。

提交前选定并保留 request_id；详见[请求幂等与恢复](request-recovery.md)。无键的旧 HTTP 请求不受此保障。

[按批次清理](batch-cleanup.md)需先预览并明确授权；普通生成账号不必然具备完整清理元数据权限。可选[后续派生归属](derived-ownership.md)必须在生成前声明，另经基线与引用核查；用 get_cleanup(include_rows=true) 查看持久化的计划主键。
