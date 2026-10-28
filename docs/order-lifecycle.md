# 公开订单案例：生成、核查和清理

[English](order-lifecycle.en.md)

这是不依赖公司内部系统的关系数据验收包。使用现有普通规则生成所有普通字段；只有可选 `payload` 二进制字段通过本地扩展生成。三个业务状态是数据标签，**不表示真实工作流引擎的提交、待办或审批已经可用**。

要验收真实实例、待办、提交与审批，使用独立的[公开 Flowable 工作流案例](../examples/flowable-approval/README.md)。它复用同一生成器规则，只有序列化字段走扩展，不给主程序增加工作流依赖。

## 准备一次

1. 使用[启动器](launcher.md) setup，准备当前源码对应 JAR。
2. 在独立、空的 MySQL 测试 schema 执行 [mysql-order-schema.sql](../examples/mysql-order-schema.sql)。脚本不包含 DROP/DELETE；重复执行会报表已存在，不要对现有业务库执行。
3. 在 setup 创建的私人 connections.json 配置该测试库。生成需要写入权限；清理还需要[完整元数据可见性等条件](batch-cleanup.md)。不要为了演示扩大真实业务账号的权限。

普通模式可直接运行。要验证复杂字段，先审阅并安装公开 codec：

```sh
python examples/install-field-extension.py --root /private/home/data/extensions --demo orders
```

设置该 home 的 settings.json 中 `javaArgs` 为 `["--faker.extensions.enabled=true"]`。扩展为可信本地代码，不是沙箱。其格式是公开的 FAKER-DEMO + JSON，不兼容任何专有工作流格式。普通 ID、状态、日期、客户、父子和输入／输出引用均不使用扩展。

## 一次生成完整案例

使用 setup 创建的 venv Python（Windows 为 `HOME/venv/Scripts/python.exe`，macOS/Linux 为 `HOME/venv/bin/python`），从源码根目录执行：

```sh
python examples/order-lifecycle.py run --home /private/home --receipt ./run-001.json --counts 50 20 100 --with-binary --confirm-write
```

这里的 python 必须是上述 venv，或先激活该环境。不测二进制时去掉 `--with-binary`，也无需安装／启用扩展。已有手动服务加 `--attach`，否则脚本通过启动器管理自己的 Java。每次调用完成后关闭自有服务，方案和批次账本持久保存在 home/data。

脚本实际经 stdio MCP 调用工具：读取规则、配置验证、数据库预检、保存方案、生成、查询原任务、批次核查。不会自行拼接 INSERT，也不会绕开工具写字段。

默认产生：

| 表 | 新增量 | 生成方式 |
| --- | --- | --- |
| 客户 | 0 | 只引用 enabled=1 的已有客户，客户名按选中的 ID 精确匹配 |
| 订单 | 170 | UUID、状态常量、日期规则；保存50、提交中20、完成100 |
| 明细 | 340 | 每父行两条、数量／价格普通随机规则 |
| 内容 | 340 | 每父行 INPUT/OUTPUT 各一条，可选字段级二进制扩展 |
| 引用记录 | 170 | 同订单、同角色精确关联内容 ID |

总新增 1020 行，分为三个独立批次／事务。每个批次检查四表数量、状态、六组完整主键引用和实际快照；不承诺三个批次整体原子提交。二进制快照一致性不等于业务引擎接受该格式。

## 保留、恢复核查与明确清理

run 成功后数据保留。回执含原 requestId、batchId 和结果，不含连接凭据；请保留本机原数据目录。重复 run 不覆盖已有回执。请求超时不应换新 ID 再跑。

```sh
# 只找回原请求、等待并核查，不重新生成：
python examples/order-lifecycle.py resume --home /private/home --receipt ./run-001.json
# 明确授权仅清理这些批次：
python examples/order-lifecycle.py cleanup --home /private/home --receipt ./run-001.json --confirm-delete
```

cleanup 逐批建立计划并按绑定 hash 执行，父子顺序和独立复核由工具负责。回执在删除前保存原计划；再次 cleanup 只读取已有计划状态，不能用新计划绕过 UNKNOWN。若计划保存后尚未执行，或已过期／被阻挡，应查看原计划并人工判断；脚本不自动重试删除。测试 schema、客户种子、方案、账本、扩展文件和正常审计信息保留，不称“全系统无痕”。

有明确报错时先看回执：部分批次可能已经提交。脚本不会把未提交的后续状态自动补齐；分析后再决定操作范围。回执和服务数据目录需成对保留，不能拿其他实例的回执直接操作。
