# 不落库的虚拟关联字段（源码预览）

[English](virtual-fields.en.md) · [公开可运行示例](../examples/virtual-fields/README.md)

给**生成表**的列添加 `"virtual": true`，即可用普通规则生成本任务内的逻辑关联标记，默认值为 false。它能参与同表引用、条件规则、父行筛选、跨表引用和 refMatch；JDBC 写入时排除这些字段，不改数据库结构，也不需要扩展代码。

```json
{"name":"owner_marker","javaType":"java.lang.String","virtual":true,
 "regs":[{"code":"REF.OTHER_TABLE","refOtherTableColumn":["documents","id"]}]}
```

例如内容表只有 `id`，没有单据 ID 和输入／输出角色。生成内容时额外生成虚拟 `owner_marker`、`role_marker`，任务表即可准确引用相应 ID：

```json
{"code":"REF.OTHER_TABLE","refOtherTableColumn":["contents","id"],
 "refMatch":{"owner_marker":{"field":"document_id"},"role_marker":{"value":"INPUT"}}}
```

前提是标记确实按所需父记录、角色生成；不能靠声明字段就猜出缺失关联。没有匹配或匹配多条时，仍按原有规则失败，并回滚整次写入。公开示例只用 UUID、同表引用、常量、条件和精确引用，内容表中没有实际 owner／role 列；3 张单据、6 条内容生成 2 条筛选后的任务，不匹配数为 0，扩展字段为 0。

## 使用边界

- 每表最多 50 个虚拟字段，至少保留一个物理写入列，仍需真实目标表；不是独立的虚拟表数据源。
- 虚拟字段不能与真实列重名。预检返回 `VIRTUAL_SHADOWS_DATABASE_COLUMN`，并继续检查遗漏的真实必填列。配置校验与预检摘要均暴露 `virtualColumns`。
- `existingData` 仍只读取明确的物理列，不支持虚拟字段。既有记录中缺失的业务关联需要真实依据，不能自动编造。
- 虚拟字段不是 SQL 筛选列、物理主键、数据库约束或清理归属的物理关联列；归属策略应使用实际生成的父主键。
- 真正不受支持的物理字段使用扩展时，可信扩展上下文仍能读取普通规则生成的虚拟值。原有 EXT.FIELD 依赖、字段白名单及大小限制保持。虚拟值没有写入数据库，也不能单独从插入账本恢复。
- 引用缓存和任务内存预算照常约束；人工高级创建入口由后续 UI 任务补齐，普通方案序列化会保留该标记。

请用当前源码验证，旧 JAR 可能忽略此属性。核对预检摘要、实际数量和关联，并检查 get_batch 只记录物理主键；清理仍先审核计划再明确执行。本例验证关联数据生成，不代表实现了真实业务工作流引擎。
