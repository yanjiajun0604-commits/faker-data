# 生成请求幂等与未知结果恢复（源码预览）

[English](request-recovery.en.md)

提交前由调用者选定并保留一个稳定请求标识，例如 `orders-demo-20261026-01`。同一个生成意图的重试必须沿用它；改了表配置／数量／目标连接等参数，应视为新意图。不能为了绕过 UNKNOWN 换个标识重跑。

## 提交与查询

- MCP：`generate_data(scheme_id=1, request_id="orders-demo-20261026-01", confirm_write=true)`。当前适配器要求明确 request_id；生成仍由原规则引擎执行。
- HTTP：`POST /publish/task/GEN_DATA` 在普通配置顶层增加 `"requestId":"orders-demo-20261026-01"`，或发送 `Idempotency-Key` 头。两处都填时必须一致。标识为 8—100 位 ASCII 字母、数字、下划线或连字符，首位字母／数字。
- 超时未拿到任务 ID：MCP `get_request(request_id)` 或 HTTP `GET /generation/requests/{requestId}`。found=true 返回原 taskId，继续查 `get_task` 与 `get_batch`。
- 同键同参数返回原任务（包括原任务失败／未知），不再次入队；同键参数变化返回 `IDEMPOTENCY_CONFLICT`。并发抢占与任务建立在同一个 SQLite 事务内。账本只保存请求摘要、ID、时间，不保存请求正文或口令；连接 password 不参与摘要，允许口令轮换后查询／重提同一意图。
- 旧的无 requestId HTTP 调用保持兼容，每次仍是新任务，**不具备此幂等保障**。人工 UI 请求标识接入另列界面改进事项。标准库客户端提交时也要求 requestId／`--request-id`。

```sh
python examples/ai_client.py --request private-demo.json --submit --request-id orders-demo-20261026-01
```

这是请求身份机制，不是随机种子：同一个 key 永远对应原任务；不同 key 即使 seed 相同也是不同请求。方案同名更新和保存不使用此机制。SQLite 数据目录丢失后，旧请求映射也丢失，不能因此盲目重跑。

## 服务中断与提交回执丢失

服务启动不会重放旧队列。上次处理中／排队的任务记录标为中断；未终结的批次标为 UNKNOWN。目标库提交与本机账本不是一个原子事务，ERROR 不必然代表目标库没写入。

先用 `get_request` 找回原 taskId，再读取[批次账本](generation-batches.md)。对于 UNKNOWN：

1. MCP `reconcile_batch(batch_id, connection="default", confirm_commit=false)`，或 `POST /batches/{id}/reconcile` 请求 `{"dataSource":{...本机连接...},"confirmCommit":false}`。
2. 只读检查目标库身份、结构、全部主键与完整行摘要。只有提交前完整快照存在且全量匹配，才返回 `observation=ALL_MATCHING, eligible=true`。这个观察步骤不修改目标或本机回执。
3. 结合执行历史确认这些匹配行就是本次写入，明确调用 `confirm_commit=true`；工具重新核查后将本机回执确认为 COMMITTED，可将相应 ERROR 任务恢复为 FINISHED。不再次插入数据。

缺失／混合／被修改的行、结构变化、错误目标库、无完整提交前快照或存在未追踪写入，都不能确认为提交成功。缺失行返回 MISSING_ALL 仍保留 UNKNOWN：只读不可见不能证明不存在延迟提交。工具不自动判回滚、不自动换 key 重跑，也不清理数据。

连接级身份不能证明同地址的物理数据库从未被替换；相同主键和完整内容也无法区分其他人完全相同的重建，所以提交确认要求使用者结合执行历史，而非仅凭样本自动认领。正常已记录的 COMMITTED／ROLLED_BACK 不需要此恢复。
