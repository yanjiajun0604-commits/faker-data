# 字段诊断、无写入预览与批次核查（源码预览）

[English](preview-verification.en.md) · [MCP](mcp.md)

三者用途不同：配置诊断定位错误，预览试算有界样本，批次核查读取已经提交的原始记录；都不等同于真实业务系统的提交、审批验收。

## 字段错误定位

配置／预览失败时，已知的规则位置通过 `diagnostic` 返回：`code`、`table`、`column`、`rulePath`、`ruleCode`、可选的从 0 开始的 `rowIndex` 和 `hint`。例如 `regs[0].onTrue[0]` 是嵌套条件的具体规则。不包含原始字段值、规则参数或连接凭据；整表／依赖图错误可能只能定位到表和配置容器。

异步失败后，`get_task` 会保留同样的结构化诊断到本机 SQLite，反复读取不丢失，原有 `errorMsg` 继续可用。MCP 错误为 `isError=true`，文本为包含 message 及现有 diagnostic/error/reason 的 JSON；AI 应先读位置再修改配置。匹配失败要核实真实来源、父记录和角色，不能静默放宽缺失／多条匹配策略。

## 真正不写目标库的预览

MCP：`preview_generation(tables,rows_per_table=5,connection="reader")`。
HTTP：`POST /generation/preview`，参数 `dataSource`、`tables`、`rowsPerTable`。

复用普通规则、任务内引用缓存、抽样父行展开和既有来源 SELECT。预览编排没有 INSERT、DDL、生成账本、扩展执行或本机异步任务提交；不是先插入再回滚，因此不会触发 INSERT 触发器或消耗自增号。可使用只有 SELECT 权限的测试账号。请求结束后释放临时引用／序列状态。

审核 `scope=GENERATED_SAMPLE_NO_TARGET_WRITES`、`sampleValid`、`preflight`、各表 requestedRows／sampledRows、omittedExtensionFields、rows／columns 和 notChecked。静态预检失败返回 sampleValid=false，不能只看 HTTP 成功。样本成功不证明完整任务唯一性、JDBC 转换、动态约束、触发器、扩展结果或业务流程，也不会建立清理归属基线。

展示的是 JDBC 转换和数据库默认值应用之前的规则输出，可能与最终存储形式不同。

限制：1—20 张表，独立生成根表每表 1—20 行、默认 5；父行展开后总计最多 1000 条生成行、生成与来源合计 50000 个单元格；生成输出字符预算 200 万，单元格最多显示 512 字符。只读来源每表最多 1000 行、10000 单元格，最多 50 个标量列、列声明长度最多 1024；实际来源超限会报错，不静默截断后冒充准确匹配。树拓扑最多 1000 个节点，仅输出样本前缀。无法支持或越界时明确失败，不落库试错。

仅执行审查过的精确内置规则类。暂不预览通用 REG.STYLE、自定义规则实现和任意表达式。支持普通条件算术／比较／布尔运算，以及 length、contains、startsWith、endsWith、isEmpty、isNotEmpty、between、toString 纯内置函数；Java 类型、构造器、方法、属性、赋值、自定义函数及其他 AST 形式均拒绝。正式生成原有表达式与扩展行为保持。EXT.FIELD 返回 EXTENSION_NOT_EXECUTED 占位，不要求安装或执行扩展；凭据命名列脱敏，不代表自动识别所有个人信息。

如果匹配来源只在完整生成集合的后面，样本可能覆盖不到；样本匹配失败只能说明本次样本，不等于证明完整任务失败。预检到抽样之间也可能有其他连接修改来源数据。

## 核查已提交的原始批次

MCP：`verify_batch(batch_id,checks=[...],connection="reader")`。
HTTP：`POST /batches/{id}/verify`，参数 `dataSource` 和 `checks`。

```json
[
  {"kind":"ROW_COUNT","table":"orders","expected":50},
  {"kind":"VALUE_SET","table":"orders","column":"state","values":["DRAFT","SUBMITTED"]},
  {"kind":"REFERENCE","table":"items","targetTable":"orders",
   "columns":{"order_id":"id"},"targetScope":"BATCH","allowNull":false}
]
```

在重复读／串行化的一次观测中核对库身份、原始主键／结构、完整行与不可变字段摘要。snapshotStatus 为 UNCHANGED、DECLARED_MUTABLE_CHANGES 或 CHANGED_OR_MISSING；valid 还要求所有声明检查通过。允许的状态修改可以不破坏不可变字段归属，同时不满足显式 VALUE_SET，二者分开报告。

ROW_COUNT 只统计本批次实际存在的记录，不把表中旧数据计入。VALUE_SET 比较有界标量，拒绝探查凭据列。REFERENCE 将子字段映射到完整兼容的父主键；BATCH 要求父记录确属本批次，DATABASE 仅要求当前模式存在；解析遵从数据库键比较，包括不区分大小写的排序规则。只有明确 allowNull=true 才接受空引用。

限制：最多 10000 个原始快照、键／摘要编码 400 万字符、20 个检查、100 个允许值候选；每个检查最多 50000 个选取单元格、400 万值字符，单值最多 2048 字符；每个检查最多返回 10 个失败主键。不接受 SQL、任意连接／表达式和跨模式目标名称。未知、未追踪或非 COMMITTED 状态不会因为核查而获得清理资格。

不会修复数据、确认 UNKNOWN 提交、改变任务／批次状态或执行删除。后续派生数据、缺失的业务关联、完整工作流语义和观测后的变化不在结果保证内；派生生命周期需另看[归属策略](derived-ownership.md)。
