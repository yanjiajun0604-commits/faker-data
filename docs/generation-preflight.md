# 数据库约束预检（源码预览）

[English](generation-preflight.en.md)

`validate_generation` / `POST /generation/validate` 保持原来的配置级检查，不连接目标库。新增 `preflight_generation(tables, connection="default")` / `POST /generation/preflight` 使用同一私有连接读取真实元数据和少量固定唯一键样本，不生成、不插入、不执行扩展。

HTTP 请求形状为 `{"dataSource":{...本机连接...},"tables":[...普通方案表配置...]}`，与生成配置相同。响应检查业务 `code` 后读取 `data.valid`、`errors`、`warnings`、`notChecked`。已知约束错误返回 `code=0, data.valid=false`；连接或非法请求失败则业务 code 非零。每条问题包含 `code/table/column/message`，不返回固定候选的原值。

可检查：目标表／列存在；漏配 NOT NULL 且无驱动报告默认值、自增或生成机制的列；禁止向数据库生成列插入；固定候选的空值、文本长度、整数范围、金额精度／小数位；全部由非空固定值构成的唯一键在单任务内必然重复或已在数据库存在。支持复合唯一键；动态键、表达式索引和含空值的唯一键只报告未证明。数据库索引、列可见性由驱动权限决定。

不执行随机、条件、引用、扩展，不能证明所有动态值有效，不读取 CHECK／触发器业务语义。`existingData` 仅检查来源列，不把只读来源算成待插入表。默认值是否存在与默认内容分开报告，凭据列的默认内容仍脱敏。当前 String 绑定器可能将必填配置的空值转为空字符串，预检会明确提示，不改变旧的生成语义。

范围为当前选定数据库／schema；带限定前缀的旧生成目标保持原行为，但预检以 `QUALIFIED_TARGET_NOT_CHECKED` 警告，不能把 valid=true 理解为这些表已经验过。完整结论必须同时阅读 warnings/notChecked。每类问题最多 200 条，过大配置应拆分检查。

MCP `generate_data` 提交任务前自动执行预检，发现确定错误时不提交。正式 Java 数据源生成入口也在实际写入前复查；直接构造低层 GenerationEngine 的嵌入调用方需自行调用预检。预检与最终事务之间可能有并发变更，数据库约束仍是最终裁决。`FINISHED` 不能证明目标应用工作流可用。

建议顺序：调查结构和样本 → 配置级检查 → 数据库预检 → 用户授权范围内生成 → 查询任务终态 → 数量、引用和真实业务验收。旧发行 JAR 不包含此接口。

MySQL 写入目标必须为 InnoDB；其他引擎在预检和正式账本入口前拒绝，只读候选不受此插入限制。
