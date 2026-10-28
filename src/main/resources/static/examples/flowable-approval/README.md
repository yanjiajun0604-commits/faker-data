# 公开工作流验收：Flowable 6.8.1

[English](README.en.md)

可独立复现的测试应用，主子表与 Flowable 真正的实例、待办、历史、变量表共用一个空 MySQL schema。它是 **可选示例**，不会给 faker-data 主程序引入 Flowable 依赖，也不含公司内部表、账号、二进制格式或接口。

真实验证目标：保存50、提交中20、完成100；详情能读主子表；提交中有真实 reviewer 待办，能推进到 auditor 并完成；保存单能提交；完成单有已结束实例和两级历史审批；序列化变量能由引擎正常读取。

## 生成与业务操作的边界

1. `init` 建立独立示例 schema 与公开 BPMN 定义。`templates` 临时创建两条参照流程、记录结构后用引擎删除参照数据；它们不是生成结果。
2. `create_recipe.py` 将参照结构编译成普通表方案。所有新业务／引擎 ID 使用工具 UUID 规则，外键使用普通引用，固定字段／时间使用输入规则，行型差异使用条件规则，父子展开和逻辑标记使用现有能力。仅 `ACT_GE_BYTEARRAY.BYTES_` 使用字段扩展。
3. `generate.py` 实际通过 stdio MCP 调用现有工具生成最终态。**不调用示例的提交、审批或 Flowable 启动流程 API 生成这批数据，也不自行执行 INSERT。** 二进制扩展只返回字段值，不连接数据库、不调用业务接口。
4. `verify` 让真实 Flowable 逐单读取实例、任务、历史及反序列化变量；`accept` 在验证后，实际提交一张保存单并审批两张单据。这些是生成后的业务验收操作。

参照属于本次部署和固定引擎版本，不是任意工作流系统的通用适配器；升级版本、修改 BPMN 或换部署后重新捕获和验收。扩展代码是可信本地代码，不是沙箱。源码依据公开引擎 API 编写，没有复制第三方私有实现。

## 1. 构建可选应用

前提：Java8+、Python3.10+、[统一启动器](../../docs/launcher.md)的 MCP 环境、可授权的本机 MySQL。当前验收固定 Flowable6.8.1/MySQL8.0.31；不等同于其他版本已兼容。

在仓库根目录（Windows；POSIX 将 `mvnw.cmd` 换成 `./mvnw`）：

```powershell
.\mvnw.cmd -f examples/flowable-approval/pom.xml -B compile dependency:build-classpath '-Dmdep.outputFile=classpath.txt'
```

该 Maven 项目独立于主 POM。`classpath.txt` 含本机绝对路径，不提交或跨机复用；换机重新构建。下载依赖需要网络。

## 2. 准备专用 schema 和私人目录

自行创建**空的、本机、名称以 `_flowable_demo` 结尾**的测试 schema，并在仓库外准备连接 JSON，结构同 [connections.example.json](../../integrations/mcp/connections.example.json)。这里只接受 `jdbc:mysql://127.0.0.1:端口/xxx_flowable_demo`；账号需有该 schema 的建表和生成权限，若核查清理计划还需[清理元数据权限](../../docs/batch-cleanup.md)。不要扩大真实业务账号权限。

下面将 `/private/demo` 视为统一启动器 home，`/private/connections.json` 是该 schema 的连接文件。首次设置的端口使用独立端口，例如18990，避免占用日常8085。将私人连接内容放到 home/connections.json。

```sh
python faker.py --home /private/demo setup --jar /path/current-faker.jar --port 18990
python examples/flowable-approval/demo.py init --profile /private/connections.json --confirm-schema
python examples/flowable-approval/demo.py templates --profile /private/connections.json --output /private/templates.json
python examples/flowable-approval/demo.py install-extension --home /private/demo
```

init 对非空 schema 拒绝执行；模板捕获要求尚无示例单据。输出和扩展 manifest 不覆盖已有文件。明确设置 home/settings.json 的 `javaArgs` 为 `["--faker.extensions.enabled=true"]`。`--java /absolute/path/java` 可指定应用和扩展解释器；启动器自己的 Java 路径仍在 settings 中。

## 3. 通过 MCP 直接生成最终态

以下 `python` 使用 home/venv 中的解释器（Windows `venv/Scripts/python.exe`，POSIX `venv/bin/python`），或先激活该环境：

```sh
python examples/flowable-approval/generate.py --templates /private/templates.json --home /private/demo --receipt /private/generation.json --counts 50 20 100 --confirm-write
```

默认不写 counts 时各一张。每种状态一个独立任务／事务，不承诺三个任务整体回滚。单状态限制1—200，以适应本例的验证预算。保存的是普通方案，人工 UI 可查看现有字段规则；AI 同样可以读取 `faker://rules`、查看本机结构，自行编制或修改同样的配置。

回执提前保存稳定 requestId，已有回执不覆盖。超时后使用相同 home 和回执，加 `--resume` 只找回原请求、核查，不重跑或补交未提交的后续任务。不要更换 ID 盲目重试。操作前要求示例单据表为空，不覆盖其他运行数据。

## 4. 真实引擎与页面验收

```sh
# 逐单只读验证，生成带精确单据 ID 的本机证据：
python examples/flowable-approval/demo.py verify --profile /private/connections.json --output /private/verified.json
# 打开独立测试页面：
python examples/flowable-approval/demo.py serve --profile /private/connections.json --port 18991
```

访问 `http://127.0.0.1:18991/`。Saved/Submitted/Approved 页签展示数据；View 展示主子表及引擎解码的变量。owner 可提交保存单，reviewer 审批后换 auditor 再审批。角色选择是本机测试模拟，**不是登录认证系统**；页面只监听 loopback，不用于生产部署。错误角色会被业务方法拒绝。

也可用 `accept` 自动验证全部单据，并对每种可办理状态选择一张完成提交／审批：

```sh
python examples/flowable-approval/demo.py accept --profile /private/connections.json --output /private/accepted.json
```

业务操作会改变页签数量；例如两张单据办结后，不应仍断言原50/20/100不变。引擎启动和检查仅限本例 schema；固定 catalog 防止另一个可见 schema 的表干扰初始化。

## 5. 清理的真实边界

**未被业务修改的生成批次**可先用 MCP `preview_cleanup` 审核，再明确授权 `execute_cleanup`。本例已验证原完成态批次仍可形成合格计划。

提交／审批会修改、删除原运行行并新增历史。此时原批次快照清理会保守拒绝；这不是“只声明 state 可变”即可解决的问题，不允许绕过检查。本例为业务操作后的数据提供独立的、引擎感知的精确清理命令：

```sh
python examples/flowable-approval/demo.py purge --profile /private/connections.json --proof /private/verified.json --confirm-delete
```

只使用 proof 中的明确单据 ID，先核对实例业务键归属，由 Flowable 删除对应实例／历史，再事务删除这些单据的明细和主表。它是本例应用的清理，不冒充 faker-data 通用批次清理；若中途失败，先核查，不应盲目重复。schema、流程定义、本机账本和正常日志保留。不要在其他服务数据目录或数据库使用这份回执。

## 规则、依赖和许可

本例自己的源码随仓库 MIT 许可。Flowable 依赖以其 [Apache2.0 开源项目](https://github.com/flowable/flowable-engine/tree/flowable-6.8.1)许可为准。Flowable6与Java8的兼容边界见[官方说明](https://github.com/flowable/flowable-engine)。这是固定版本的本地能力验收，不要求用户安装工作流引擎才能使用通用生成器。
