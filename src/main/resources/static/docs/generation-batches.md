# 生成批次账本（源码预览）

[English](generation-batches.en.md)

正式 Java 生成服务为每个生成任务创建本机 SQLite 批次账本，`batchId = taskId`。普通规则、父子表、候选来源和字段扩展共用账本；不需要为了追踪数据改写字段生成方式。人工界面原有使用流程不变。

生成后调用 MCP `get_batch(batch_id=taskId, include_rows=true, offset=0, limit=100)`；或 `GET /batches/{taskId}`、`GET /batches/{taskId}/rows?offset=0&limit=100`。主键页最多 200 行，按 nextOffset 继续。查看不执行清理或重跑。

## 记录的内容

- 目标数据库身份摘要（产品、catalog/schema、连接指纹；原生 MySQL 另含 server UUID），方案配置摘要、任务身份、表生成顺序。
- 支持追踪的表：实际写入并回读确认的主键（含复合键）、目标列／主键结构摘要、完整目标行摘要。长文本和二进制流式计算摘要，不将完整字段复制进账本；每行摘要读取预算 64 MiB。
- 在同一目标事务内回读，提交前再次固定最终行摘要，以覆盖后续生成表的触发器对本批已拥有行的修改。不能确认显式主键时回滚。
- `existingData` 只读候选不登记为新建记录。账本也不自动认领以后用户提交／审批等操作新建的待办、流水或其他记录。
- 账本不保存连接密码或完整 JDBC URL，也不保存整份业务行；主键与库／表名称仍属本机数据，应保护应用 data 目录。

## 状态与边界

`WRITING` → `PREPARED` → `COMMITTED`；目标事务明确回滚时为 `ROLLED_BACK`。SQLite 与目标数据库不属于一个原子事务：回执不明或服务中断后，未终结记录变为 `UNKNOWN`。不得把 UNKNOWN 当成可以重试的失败，也不自动重放。COMMITTED 的 `rowCount` 只统计已核实的主键快照；其他状态中 `stagedRowCount` 不代表已提交。

第一版可靠追踪显式提供的VARCHAR／NVARCHAR／整数／数值主键（固定 CHAR／NCHAR 的填充语义暂不追踪）。无主键、未配置／自动生成的主键、二进制或凭据式主键、超过 32 列的主键、暂不支持摘要的对象列等，会标为 `UNTRACKED`，并返回具体 reason。生成仍可进行，但 `trackable=false`；`untrackedRows` 是成功执行插入路径的未追踪行数，不能据此猜测实际主键。省略的自增主键暂不支持自动追踪。行数为零的表不产生待清理记录。

连接指纹可防止误选其他地址／库；缺少服务器唯一标识时返回 `strength=CONNECTION_SCOPE`，不声称能识别被同地址替换的物理数据库。`trackable=true` 只是原始写入快照完整，不代表可以跳过后续清理预览、引用／修改核查或业务验收。

当前读取接口不删除任何数据。当前源码另提供[请求恢复](request-recovery.md)、[显式批次清理](batch-cleanup.md)和可选[派生归属](derived-ownership.md)。旧版本生成的数据没有此账本，不能补猜归属。直接使用不带账本依赖的低层嵌入式 GenerationEngine 构造方式也不具备此能力。

可选[派生归属声明](derived-ownership.md)必须在生成前确定；声明可变字段时额外保存不可变字段摘要。原始主键页仍是插入账本，完整计划清理范围请查 get_cleanup(...,include_rows=true)。
