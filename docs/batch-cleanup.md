# 按批次预览与安全清理（源码预览）

[English](batch-cleanup.en.md)

清理只处理本轮生成账本明确拥有的原始插入记录。旧版本／无账本数据、只读候选、原有数据和以后业务动作生成的弱关联记录不自动认领。后续业务派生归属另行实现；不能把“原始插入已清理”称为整个业务场景无痕。

## AI 调用

1. `get_batch(batch_id)` 核对目标和批次。
2. `preview_cleanup(batch_id, connection="cleanup")`：目标库只读检查，本机保存一个十分钟有效的计划。阅读 eligible、blockers、rowCount、deleteOrder、scope、planId／planHash，不只检查 HTTP 或工具调用成功。
3. 用户已授权删除该批次且计划符合范围时，`execute_cleanup(batch_id, plan_id, plan_hash, connection="cleanup", confirm_delete=true)`。
4. `get_cleanup(batch_id, plan_id)` 查询同一个计划的结果。DONE 需同时有 verifiedAbsent=true。超时或 UNKNOWN 只核查原计划，不换计划盲删。

HTTP 对应 `POST /batches/{id}/cleanup/preview`（请求 dataSource）、`POST /batches/{id}/cleanup/execute`（dataSource、planId、planHash、confirmDelete）、`GET /batches/{id}/cleanup/plans/{planId}`。连接仍只从本机安全配置提供。读取计划／批次不删除数据。

## 执行保证与拒绝条件

计划绑定目标库身份、列／主键结构、完整写入快照、物理外键拓扑、期限及随机计划身份。执行时在串行化事务内按原主键锁定并重新核对，按外键从子表到父表删除，提交后使用独立连接验证原主键已不存在。不会关闭 FK 检查，不使用 TRUNCATE，不重置自增序列，不清日志。重复同一个 DONE／UNKNOWN 计划不再次删除。

被修改／缺失的行、其他未终结批次也认领的主键、账本不完整、未知结果、未追踪写入、外部记录引用、删除触发器、跨库／schema 引用、引用非主键的 FK、自引用／循环表关系、结构／拓扑变化等均拒绝。自引用没有实际匹配行时无需产生依赖边。复合主外键支持。删除前后并发变化仍须通过执行复查与数据库约束裁决。

失败发生在目标提交尝试前，且明确回滚成功：批次恢复 COMMITTED，该计划 INVALIDATED，重新查看条件后准备新计划。目标提交回执不明、进程中断或独立复核失败：计划 UNKNOWN、批次 CLEANUP_UNKNOWN，不自动重试。删除账本也保留，审计证据不清除。缺失记录本身不能证明之前的删除事务何时提交；UNKNOWN 需要结合执行历史核查。

## 当前数据库与权限边界

第一版原生验收目标是 MySQL InnoDB；H2 admin 用于自动化夹具，不代表原生 Oracle／达梦。其他数据库的清理在能力／权限检查得到原生验证前不放行，原有生成支持不因此扩展声明。

为了避免把隐藏元数据当成“没有外部引用／触发器”，当前 MySQL 清理保守要求 SHOW GRANTS 能直接证明全局 SELECT 与目标 schema 的 TRIGGER（或全局 TRIGGER），且无部分撤销；仅角色继承或模糊授权不能自动认定完整。普通生成账号可以继续用于生成，但未必能清理。**程序不会执行 GRANT 或更改账号权限**；如需清理，使用获批准的测试账号／单独私有 profile。TRIGGER 权限本身还允许修改触发器，应由数据库管理者评估，而不是为跑通示例在真实环境自动扩大权限。

[MySQL 官方说明](https://dev.mysql.com/doc/mysql-infoschema-excerpt/8.0/en/information-schema-triggers-table.html)：查看触发器元数据需要该表的 TRIGGER 权限。没有看到元数据不是不存在的证明。存在 DELETE 触发器则拒绝，不猜测其副作用。生成预检和正式写入也拒绝 MySQL 非 InnoDB 目标，避免误承诺全任务回滚；只读候选可以来自其他表引擎。

资源限制：最多 100 张拥有表、100000 条核实快照；每个编码主键最多 8192 字符，主键／摘要文本合计预算约 16M 字符；单表最多 500 条入向 FK 元数据，总引用扫描最多 100000 行。超过限制报阻塞，不截断后继续删除。行摘要的 64 MiB 读取预算沿用[批次账本](generation-batches.md)。范围限制是第一版边界，不应绕过检查扩大删除。

每个服务同时只执行一个重型清理预览／执行请求；并行请求返回 CLEANUP_BUSY，查询已有回执不受此重型操作闸门影响。
