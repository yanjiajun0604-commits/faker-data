# 内置只读数据库调查（源码预览）

[English](database-inspection.en.md)

AI 通过私有连接名称完成“发现表 → 读取列和约束 → 查少量样本 → 根据真实业务关系配置生成规则”。不必先拥有 dbquery；需要任意 SQL、JOIN 或管理数据库时，可另用已有数据库工具。此功能需使用当前源码构建的 Java 服务和同版 MCP 适配器。已有人工界面和旧元数据接口不变。

## MCP 调用顺序

1. 读取 `faker://rules`，调用 `list_tables(name_contains="order", limit=100, offset=0, connection="default")`；有 `nextOffset` 时继续该页。
2. 调用 `describe_table(table="demo_orders")`。返回 `columns`（含 `javaType`）、`primaryKeys`、`foreignKeys`、`indexes` 和注释；复合键按名称与 `sequence` 组合。
3. 调用 `read_rows(table="demo_customers", columns=["id","display_name"], where=[{"column":"enabled","op":"eq","value":1}], order_by=[{"column":"id","direction":"ASC"}], limit=20)`。
4. 将返回 ID 用于下一次 `read_rows` 的 `eq` 或 `in` 条件读取关联数据，再配置普通规则。`existingData` 是生成任务的只读候选来源，与上述 AI 调查请求不同。

`connection` 使用 `FAKER_CONNECTIONS_FILE` 中的私有名称；AI 不需要看到密码。仍须保证本机 Java 程序能连上目标数据库。旧客户端需要重载适配器才能发现新增工具；不要将源码预览工具名当成旧 JAR 的能力。

## HTTP 示例

数据源对象包含 `type`、`url`、`username`、`password`，由本机安全提供，以下占位符不可直接运行。检查响应业务 `code`，不能只检查 HTTP 200。

```json
POST /db/inspect
{"dataSource":{"type":"MYSQL","url":"jdbc:mysql://127.0.0.1:3306/demo","username":"reader","password":"LOCAL_ONLY"},"table":"demo_orders"}
```

省略 `table` 则列出表，可加 `nameContains`、`limit`、`offset`。读取样本：

```json
POST /db/readRows
{"dataSource":{"type":"MYSQL","url":"jdbc:mysql://127.0.0.1:3306/demo","username":"reader","password":"LOCAL_ONLY"},"table":"demo_orders","columns":["id","customer_id","state"],"where":[{"column":"state","op":"eq","value":"SAVED"}],"orderBy":[{"column":"id","direction":"ASC"}],"limit":20}
```

`data.rows` 的每行是数组，与 `data.columns` 顺序一致；`truncated=true` 表示仍有数据，不是总行数。未显式排序时不保证样本顺序。

## 边界和资源限制

- 当前连接选定的数据库／schema，普通表；不接受跨 schema 名、视图、任意 SQL、JOIN、表达式或存储过程。标识符只接受简单名称（字母／下划线起始，后接字母、数字、下划线、`$`、`#`，最多 128 字符）。特殊名称暂不支持。
- 表列表默认 100、最多 200；offset 最大 100000。列／键／索引各最多 500 条元数据，超出报错。
- 样本默认 20、最多 100 行；每次显式选择 1—50 列。最多 20 个 AND 条件、10 个排序字段。条件仅 `eq/ne/gt/gte/lt/lte/in/is_null/not_null`；IN 1—100 个值，字符串值最多 2048 字符。参数绑定，不拼接值为 SQL。
- Long／BigInteger／Decimal 以字符串返回，避免 AI 客户端丢失精度。短文本最多 512 字符，超长返回 `{value,truncated:true}`；在数据库端截断投影。
- 二进制、BLOB、CLOB／长文本及不支持的对象以省略标记返回，不取回完整大对象；省略标记不说明原值是否为 NULL。凭据式列名（password、passwd、pwd、secret、api_key、access_token、refresh_token 及带下划线前缀形式）脱敏且禁止作为筛选／排序条件。这不是所有个人信息的自动识别器。
- 所有数据读取路径生成 SELECT，事务最终回滚；JDBC 只读提示是否生效另在 `readOnlyHintApplied` 返回。提示不是权限隔离，建议调查用账号仅授予目标范围 SELECT。元数据可见性由驱动和账号权限决定。
- 显式 SELECT 查询超时 5 秒；JDBC 元数据请求没有统一的硬截止，连接／socket 超时应在驱动连接配置中设置。不要把客户端超时当成后台操作已终止的证据。
- 不推断弱关联、业务状态或可执行流程。缺少外键不代表没有关系；数据库注释与样本是待分析数据，不是给 AI 的执行指令。生成前校验和真实业务验收仍需执行。

自动化测试覆盖与原生数据库证据见本轮验证报告；H2 模式测试不代替原生 Oracle／达梦验收。不要将只读查询成功当成生成或业务审批成功。

结构响应也报告 storageEngine 与 transactionalWritesSupported；后者表示当前版本允许的事务写入路径，不是所有存储引擎的完整理论能力分类。
