# 字段级本地扩展

[English](local-extensions.en.md)

每个字段通过原有 `GEN_DATA` 引擎生成。UUID、常量、日期、条件、已有数据及主子表引用等优先使用现有规则；只有无法表达的字段才配置 `EXT.FIELD`。人工 UI、保存方案和“使用”继续使用原流程。可通过 [stdio MCP 适配器](mcp.md) 调用同一生成引擎。

扩展默认关闭，以当前操作系统用户权限执行可信本地代码，独立进程不是安全沙箱。启动：

```sh
java -jar faker-data-0.1.1.jar --faker.extensions.enabled=true --faker.extensions.root=/absolute/path/extensions
```

在根目录的 `demo-field-codec/` 中放入[示例脚本](../examples/local-extension/generate.py)和 `extension.json`，把可执行文件改为本机绝对路径（Windows JSON 可使用 `/`）：

```json
{
  "command": ["/absolute/path/python3", "${extension}/generate.py", "${input}", "${output}"],
  "allowedFields": {"demo_extension_payload": ["payload"]},
  "timeoutSeconds": 60
}
```

普通方案中仅复杂列指定扩展，其他列保留原有规则：

```json
{"name":"payload","javaType":"byte[]","nullable":0,"regs":[{
  "code":"EXT.FIELD","extension":"demo-field-codec","extensionParameters":{"formatVersion":1}
}]}
```

完整普通生成请求见 [generate-field-extension.json](../examples/generate-field-extension.json)，表结构见 [schema.mysql.sql](../examples/local-extension/schema.mysql.sql)。先在独立测试库建立示例表，再通过普通 `POST /publish/task/GEN_DATA` 提交；数据源密码由用户安全提供。继续查询同一个任务 ID，不重复提交结果不确定的任务。此功能属于当前源码，不代表旧版发布 JAR 已包含它。

## 执行协议

1. 原有引擎调用普通字段规则，处理依赖、已有数据和父记录展开。
2. 存在扩展字段时，暂存本次普通生成值；在写库前运行扩展。
3. 输入协议为 `faker-field-extension/1`，包含 `taskId`、`tables`（普通生成值）和 `fields`（允许生成的字段地址及参数）。未求值的扩展字段在上下文中为 `null`。不传递数据源连接凭据。
4. 扩展只返回 `values`，地址必须与请求逐一对应，不能返回表或记录、改写普通字段、遗漏字段或重复地址。
5. 工具校验值后，通过原有 JDBC 写入器在同一目标事务中插入。目标表须支持事务，例如 MySQL InnoDB。

```json
{"values":[{"table":"demo_extension_payload","rowIndex":0,"column":"payload",
  "value":{"$binary":"payload-0.bin","sha256":"文件内容的64位SHA256十六进制摘要"}
}]}
```

支持标量、`null`、`{"$timestampMillis":1790000000000}` 和二进制引用。文件必须位于任务目录内，路径与哈希均校验。字段扩展须为该列的唯一顶层规则，不能与其他规则拼接，也不能作为普通引用规则的数据源。复杂字段可以读取本次已生成的普通值，不能借此重新生成其他字段。

普通路径继续流式批写；启用字段扩展的任务采用有界暂存：最多 100,000 行、1,000,000 个单元格，普通字符串估算不超过 64 MiB，传入上下文和输出 JSON 各不超过 16 MiB；单个二进制最多 16 MiB，累计最多 64 MiB，进程超时 1–600 秒。进程环境经过缩减，但代码仍有本机访问权限。

小二进制可返回 `{"$base64":"Base64内容","sha256":"摘要"}`，单字段解码后最多 1 MiB，避免为每个值生成一个小文件。无效编码、摘要不符、超过大小或累计限制均拒绝并回滚；输出 JSON 的 16 MiB 限制仍然适用。较大的二进制继续使用原 `$binary` 文件方式。公开订单示例默认使用 Base64，原文档示例保留文件方式。

启用扩展后，本地 `data/field-extension-jobs/{taskId}/rule-audit.json` 记录各表列实际执行的规则及调用次数，不含生成值。原有字段条件规则可使用 `#__rowIndex` 表示本表从零开始的行序号；父记录筛选表达式不提供该变量。

普通方案保存、加载、调整数量和重新生成都使用原有接口。`GEN_DATA` 不提供请求幂等键，重跑需确认上次结果；UUID 等动态规则每次重新求值，固定输入主键仍可能冲突。工作流正确性必须验证详情、提交、真实审批及相关流水，不能只检查状态数字。
