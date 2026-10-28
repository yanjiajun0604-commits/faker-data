# 已有数据引用与父记录展开

[English](existing-data.en.md)

本页对应当前源码中的新增功能，尚未发布到旧版 JAR。配置通过现有 `GEN_DATA` HTTP API 和 JSON 使用，目前没有新增可视化配置入口。旧方案不需要增加这些属性。

## 弱引用字段如何配置

没有外键的字段，也可能必须引用合法的用户、组织、客户或商品。`RES.INPUTAREA` 适合常量和少量固定候选。需要维护真实候选记录、保持 ID/姓名/组织等字段一致时，用 `existingData`。

把来源表放进同一请求的 `tables` 数组，声明投影字段和读取配置：

```json
{
  "name": "demo_lookup_user",
  "existingData": {
    "where": {"enabled": 1},
    "orderBy": ["id"],
    "maxRows": 10000
  },
  "columns": [
    {"name":"id","javaType":"java.lang.String"},
    {"name":"display_name","javaType":"java.lang.String"},
    {"name":"org_id","javaType":"java.lang.String"}
  ]
}
```

此项只读取已有记录，不插入来源表，忽略 `size`。来源列不能配置生成规则。写入开始前，每个来源读取一次，此后使用该数据集在本任务中的快照，不会每生成一个字段就查询数据库。不承诺多个来源之间具有相同时间点的跨表快照；有强一致需求时先确认目标数据库的隔离与业务约束。

目标列仍使用已有的引用规则：

```json
{"code":"REF.OTHER_TABLE","refOtherTableColumn":["demo_lookup_user","id"]}
```

`user_name` 引用同一来源的 `display_name`，`org_id` 引用其 `org_id`，三者就来自同一条候选记录，不会各自随机选择。普通模式按目标行号轮转选取来源行。

读取边界：

- 使用当前请求的目标数据库连接，需要相应表的 SELECT 权限。
- `where` 是 AND 连接的等值条件，值为字符串、数值、布尔或 NULL；NULL 转为 `IS NULL`。过滤字段不必包含在投影中。值使用参数绑定，不接受 SQL 片段。
- 表名可使用受限的 `schema.table` 形式，字段名和 `orderBy` 仅接受普通标识符，不接受表达式、排序方向或任意 SQL。默认按全部投影字段升序排序，建议显式用唯一键排序。
- `maxRows` 默认 10000，范围 1–100000。查不到记录或超过上限均报错，不静默截断。缓存的条目和字节容量限制仍然有效。
- 日期值进入规则上下文时转为 Unix 毫秒文本；数值和布尔转为文本；NULL 保持为空。BLOB、二进制、CLOB 等复杂类型不支持。
- 同一请求的表名必须唯一；暂不支持同一物理表的多个别名、JOIN、逐目标行参数化查询或随机抽样。

## 按当前记录精确选择关联行

同一来源中有多个内容类型时，可以在原有 `REF.OTHER_TABLE` 上增加匹配配置：

```json
{
  "code": "REF.OTHER_TABLE",
  "refOtherTableColumn": ["demo_order_contents", "id"],
  "refMatch": {
    "order_id": {"field": "order_id"},
    "role": {"value": "INPUT"}
  }
}
```

匹配来源行的 `order_id` 必须等于目标当前行的 `order_id`，同时 `role=INPUT`。`field` 引用当前行字段，`value` 为固定标量或 null；条件以 AND 连接。支持任务生成的来源和 `existingData` 候选。每个规则/任务按匹配列建立一次索引，不逐行查库；来源列必须真实存在于来源投影中，不能按二进制内部未解码内容匹配。

默认无匹配报 `REF_MATCH_NONE`，多条匹配报 `REF_MATCH_MULTIPLE`，任务回滚。可显式配置 `refOnMissing: "NULL"` 或 `refOnMultiple: "FIRST"`；FIRST 按候选读取/生成顺序选择。null 按配置值相等匹配，不采用 SQL 的三值逻辑。省略 `refMatch` 保持原轮转或父行绑定行为；不接受循环表/字段依赖。人工规则编辑器“其他表字段”支持查看和编辑高级匹配 JSON。

## 只为符合条件的父行生成子行

在目标子表配置：

```json
{
  "parentRows": {
    "table": "demo_lookup_document",
    "condition": "#state == 'SUBMITTED'",
    "rowsPerParent": 2
  }
}
```

引擎先生成或读取父记录，筛选父行，为每条匹配记录生成两条子行。子表 `size` 被实际展开数量替换。指向该父表的全部 `REF.OTHER_TABLE` 均绑定到当前父行；引用其他表的规则仍按子行序号轮转。

`condition` 使用父记录字段作为 SpEL 变量，数值比较推荐 `between(#amount,1,100)` 等函数，因为规则上下文中的值通常是文本。条件省略时选取全部父行；没有匹配父行时生成零条子行。父表既可以是本任务生成的表，也可以是 `existingData`。普通字段递增序号仍在整张子表内递增，不会每个父行重置。

`rowsPerParent` 默认 1，范围 1–10000；当前只能配置固定数量。`parentRows` 不能与同一表上的 `treeConfig`、`existingData` 同时使用。循环依赖和数量整数溢出均失败，不允许生成部分结果后视为成功。

## 可运行示例

[示例 SQL](../examples/mysql-existing-data-schema.sql) 在独立测试库创建三张新表和三个虚构用户，其中两个启用；不要对已有业务表执行它。

[请求 JSON](../examples/generate-existing-data.json) 读取两个合法用户，生成六张示例单据，并且只为两张 `SUBMITTED` 单据各生成两条子记录。用户 ID、姓名、组织保持对应，来源用户不新增。

```sh
python examples/ai_client.py --request examples/generate-existing-data.json
python examples/ai_client.py --request examples/generate-existing-data.json --submit --request-id example-existing-data-run-001 --timeout 300
```

按 [AI 指南](../AI_USAGE.md) 配置连接、提供本地密码、授权写入并查询原任务。集成测试在临时 H2 数据库验证了上述 JSON、字段一致性、快照复用、容量拒绝、空结果、参数绑定、父行筛选和事务回滚。MySQL、Oracle、达梦的真实数据库兼容和业务验收仍需独立验证。

这两项能力生成的是关系数据。示例子记录不是真实工作流待办；创建可执行流程仍需满足目标引擎的运行协议。当前没有 HTTP 动作执行、生成清单或自动清理功能。
