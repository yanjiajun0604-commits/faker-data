# 给 AI 的 faker-data 接口调用指南

> **访问地址：** 本文适用于 v0.1.1 及当前源码，基地址为 `http://127.0.0.1:8085`。旧版 v0.1.0 JAR 仍使用 `http://127.0.0.1:8085/faker`；调用该旧版时必须调整客户端基地址。版本差异见[访问地址说明](docs/access-url.md)。

本文用于实现 **人 → AI → 本地 faker-data HTTP API → 目标测试数据库**。无需控制网页，也无需理解 Java 源码。本文对应当前便携版源码；旧版只含后端的快照可能没有这些运行条件。

## 1. 先确认任务和运行位置

用户应明确目标测试数据库、表、生成数量、字段规则，以及是否允许向该目标写入。这些信息已明确时直接执行，不要重复确认。缺少目标或写入范围时先澄清，不能自行猜一个生产库或擅自建库、清表、删数据。

用户启动当前源码构建的 JAR（下例文件名按本地构建产物填写）：

```sh
java -jar faker-data-0.1.1.jar
```

默认基地址为 `http://127.0.0.1:8085`，网页在该地址后加 `/`。应用会自动建立自己的 SQLite 状态库，不需要为应用配置 MySQL 或 Redis；但生成数据仍需目标 MySQL、Oracle 或达梦数据库及相应权限。

**调用接口的 AI 工具必须能访问运行程序的机器。** 云端 AI 的 `127.0.0.1` 指向云端自己，不能访问用户电脑。可以让用户的本地 AI/终端工具运行下述客户端；不要为了连通而直接将无认证接口开放到公网。

使用 `GET /asyncTask/list` 检查服务，返回的 `data` 应包含 `type: "GEN_DATA"`。这不是数据库连接检查。所有下文路径直接接在主机与端口之后，不再增加应用路径前缀。

## 2. HTTP 和响应约定

- JSON 请求使用 `Content-Type: application/json; charset=utf-8`。
- 字符串、枚举区分大小写；规则代码、数据库类型使用本文的大写写法。
- HTTP 状态非 2xx 就是请求失败；HTTP 200 **仍须检查业务字段 `code`**。
- `code: 0` 表示该接口调用成功，非 0 时读取 `msg` 并停止当前步骤。
- 返回值通常在 `data` 中。连接测试成功可以没有 `data`。

```json
{"code":0,"msg":"操作成功","data":"返回值"}
```

生成任务提交成功只表示已受理，不表示数据已写入。必须继续查同一个任务 ID，直到明确终态。

## 3. 第一步：检查目标连接

使用同一个数据源对象调用元数据接口和生成接口：

```json
{
  "type": "MYSQL",
  "url": "jdbc:mysql://127.0.0.1:3306/faker_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
  "username": "faker_demo",
  "password": "由用户安全提供"
}
```

`POST /db/connect/test` 的请求体就是这个对象，没有外层 `dataSource`。

| `type` | URL 形状示例 |
| --- | --- |
| `MYSQL` | `jdbc:mysql://主机:3306/数据库名?serverTimezone=UTC` |
| `ORACLE` | `jdbc:oracle:thin:@//主机:1521/服务名` |
| `DM` | `jdbc:dm://主机:5236`，按目标环境补充 schema 等参数 |

这些只是 URL 形状，不是可直接连接的凭据；不要将 MySQL 的测试连接参数照搬到其他驱动。当前数据源校验要求 URL、用户名、密码均非空。SQLite 和 H2 不属于生成目标类型。Oracle、达梦的实际版本兼容性应在用户自己的独立测试库验证。

## 4. 第二步：读取真实表和字段

1. `POST /db/showTables?withSub=0`，请求体仍是数据源对象。返回 `data: [{"name":"实际表名"}]`。
2. 对需要的每张表调用 `POST /db/showColumns/{URL编码后的表名}`，请求体仍是数据源对象。
3. 也可以一次调用 `POST /db/showTables?withSub=1`，每张表会附带 `columns`，大库建议按表读取。
4. `data: null` 不是有效的元数据；停止并检查连接。不要凭空编造字段来继续生成。

字段示例：

```json
{
  "name":"id",
  "javaType":"java.lang.Long",
  "dbType":"bigint",
  "length":20,
  "nullable":0,
  "regs":[],
  "depended":false
}
```

保留接口返回的 `name`、`javaType`、`dbType`、`length`、`nullable`，只配置生成规则 `regs`；不要根据 SQL 类型猜 `javaType`。例如 MySQL 的 DATETIME 在新驱动中可能返回 `java.time.LocalDateTime`。名称大小写使用元数据原值。

表和列必须已存在。API 不建表、不建库、不提供任意 SQL 执行接口。可省略有默认值/自增的列，让目标数据库填写；必须覆盖没有默认值的必填列。仅从元数据读取的列名构造请求，不往表名、列名中拼接 SQL。特殊标识符或保留字的自动引用不完善，遇到这类表先在独立测试环境验证。

## 5. 第三步：把用户需求转换成生成请求

`POST /publish/task/GEN_DATA` 的请求体：

```json
{
  "dataSource": {"type":"MYSQL","url":"用户的真实URL","username":"用户账号","password":"安全取得的密码"},
  "tables": [
    {
      "name":"用户选定的真实表名",
      "size":10,
      "columns":[
        {"name":"id","javaType":"java.lang.String","dbType":"varchar","length":36,"nullable":0,"regs":[{"code":"FIX.UUID"}]},
        {"name":"name","javaType":"java.lang.String","dbType":"varchar","length":64,"nullable":0,"regs":[{"code":"FIX.PEOPLE_NAME"}]}
      ]
    }
  ]
}
```

上面表和字段只是形状示例。完整可运行样例见 [普通与关联数据](examples/generate-demo.json) 和 [树形数据](examples/generate-tree.json)。它们对应 [MySQL 示例表结构](examples/mysql-demo-schema.sql)，只应在用户授权的空测试库中创建，不能对用户已有业务表直接执行该建表脚本。

普通表的 `size` 是本次新增行数，不是最终总行数。项目执行 INSERT，不覆盖、不清空已有行。没有幂等键；重复提交会再次新增，或者撞到唯一约束。每次任务的所有表共用一个 JDBC 事务；MySQL 必须使用支持事务的引擎（如 InnoDB）才能依赖整体回滚。

### 可用字段规则

| 规则 `code` | 必要/常用参数 | 生成含义 |
| --- | --- | --- |
| `FIX.UUID` | 无 | UUID 字符串，通常需 36 字符列 |
| `FIX.PEOPLE_NAME` | 无 | 模拟人名 |
| `FIX.TEL` | 无 | 模拟手机号 |
| `FIX.IDENTIF` | 无 | 模拟身份证样式字符串，不代表真实身份 |
| `REG.RANGE_NUM` | `minSize`、`maxSize` | 闭区间随机整数，最小值不得大于最大值 |
| `REG.INCREMENT_NUM` | `minSize` 起始值、`step` 步长 | 在本任务本字段内递增，默认从 1 开始、步长 1；新任务会重新开始，不会自动查表中最大值 |
| `REG.RANGE_STRING` | `minSize`、`maxSize` | 指定长度范围的随机字母数字串 |
| `REG.STYLE` | `styleReg` | 正则生成表达式，须符合 data-factory 支持范围 |
| `REG.DATE` | `dateRange: [起点毫秒,终点毫秒]` | 时间戳范围，单位是 Unix 毫秒，不能传秒或日期字符串 |
| `RES.INPUTAREA` | `inputs:[{"value":"值","weight":1}]` | 按权重选择非空候选值；单项就是常量。无效或空候选不用于生成 |
| `REF.SELF_COLUMN` | `refSelfColumn`、可选 `refSelfTransform:"FIRST_CHAR"` | 同一行引用另一列，可取首字 |
| `REF.OTHER_TABLE` | `refOtherTableColumn:["表","列"]` | 引用同一任务中另一张表本次生成的值 |
| `COND.EXPR` | `condition`、`onTrue`、`onFalse` | SpEL 条件分支，每个分支仍是规则数组 |
| `STRUCT.TREE_PARENT` | 需表级 `treeConfig` | 当前树节点的父节点 ID；根为 NULL |
| `STRUCT.TREE_CODE` / `REG.TREE_CODE` | 需开启表级树编码 | 当前节点的结构编码 |

每个字段的多个 `regs` **按顺序拼接为一个最终值**，不是多列、不是随机二选一。例如常量前缀规则后接 UUID 会得到带前缀的 UUID；要为列长度留足空间。空规则通常生成 NULL；字符串 NOT NULL 列有空串兜底，但不能把它当作必填业务值的解决方案。

数值最终转换为 JDBC 数值类型；UUID、手机号、身份证样式应放字符串列，不能放数字列以免丢前导零或溢出。金额可用 `RES.INPUTAREA` 的 `"12.50"` 写入 DECIMAL；随机小数没有专用规则，不要假设 `REG.RANGE_NUM` 会生成小数。BLOB/二进制及未知 Java 类型不属于已支持类型，生成会报错并回滚；不要擅自改类型来绕过。

### 跨表引用

被引用的父表和父列必须包含在**同一请求**中，父表生成行数大于 0，父列配置能实际生成非 NULL 值。不能用此规则查询目标库中已有行。引擎按依赖排序生成；请求数组里子表在前也可以。循环表依赖不支持。

子表多列引用同一父表时使用同一个父行索引，可保持父行字段对应关系。引用按行索引轮转选择，不是随机 JOIN。不要手填 `depended` 和 `refOtherTables`，引擎会计算这些运行字段。

跨表引用建议使用顶层 `REF.OTHER_TABLE` 规则；条件分支内部的跨表依赖发现并不完整，不应在嵌套分支中配置跨表引用。先在父/子列上生成引用，再让其他列通过同表规则或条件使用它。

### 条件与同表引用

```json
{
  "code":"COND.EXPR",
  "condition":"#kind == 'vip'",
  "onTrue":[{"code":"RES.INPUTAREA","inputs":[{"value":"80","weight":1}]}],
  "onFalse":[{"code":"RES.INPUTAREA","inputs":[{"value":"100","weight":1}]}]
}
```

条件变量使用 `#列名`；普通生成结果在行内通常是字符串，数值比较建议用 `#between(#age,18,60)`，不要假定 `#age` 已是 Java 整数。函数和字段引用限制见 [SpEL 函数说明](docs/spel-functions-guide.md)。条件字段须使用 SpEL 可识别的变量名；现有代码不适合直接使用中文列名作为 SpEL 变量。不要生成依赖环，且必须给两个分支定义符合列约束的结果。

### 树形数据

在表对象中增加 `treeConfig`，同时在父引用列显式设置 `STRUCT.TREE_PARENT` 规则：API 不会替 AI 做网页端的自动规则注入。

```json
{
  "nodeField":"id",
  "parentRefField":"parent_id",
  "maxDepth":3,
  "minRoots":1,
  "maxRoots":1,
  "minChildrenPerNode":2,
  "maxChildrenPerNode":2,
  "treeCodeEnabled":true,
  "treeCodeField":"tree_code",
  "layerBitWidths":[2,2,2]
}
```

当前引擎的 `maxDepth:3` 表示 **3 层**（深度 0、1、2），上例生成 `1+2+4=7` 个节点，树表 `size` 会由拓扑实际数量覆盖。请先计算节点规模，不要把深度和分叉数随意调大；树拓扑会预先放在内存中。

`nodeField` 必须唯一，可以用 UUID；`parentRefField` 需允许根节点为空，推荐可空列。结构编码列设置 `STRUCT.TREE_CODE` 或 `REG.TREE_CODE`，并开启 `treeCodeEnabled`。`layerBitWidths` 决定每层位宽，超出数组层数时沿用最后一项；位宽须容纳该层最大序号。`reservedRootCodes` 可选，用于指定根编码，启用后根数量取列表长度。重复运行前检查编码唯一约束，编码不会自动避开已有值。

## 6. 第四步：提交一次，查询到终态

提交返回：

```json
{"code":0,"msg":"操作成功","data":"任务UUID"}
```

保存这个 UUID，轮询 `GET /asyncTask/queryProcess/{UUID}`，建议间隔 1 秒，按任务量设置总等待时间。

```json
{"code":0,"msg":"操作成功","data":{"progress":99.0,"state":"PROCESSING","errorMsg":null,"result":null}}
```

| `state` | AI 的处理 |
| --- | --- |
| `WAITING` | 已排队，继续查同一 ID |
| `PROCESSING` | 正在生成，继续查；提交前最多显示 99% |
| `FINISHED` 且 `progress:100` | 目标事务正常提交，可以报告“任务执行完成” |
| `ERROR` | 失败，读取 `errorMsg`，不能报告成功；先核对和修正规则再决定是否重新提交 |
| `NONE` | 本实例找不到任务，核对 ID、端口和 SQLite 数据目录；不要自动重提 |
| `CANCELED` / `OVERTIME` / `OUTOFQUEUE` | 终止或未执行，不能当作成功 |

本版本正常生成路径使用 WAITING、PROCESSING、FINISHED、ERROR；并没有可调用的取消接口。前述其他状态为兼容保留，不能凭空调用取消或恢复 API。

队列满时提交接口返回业务错误，未返回已受理的任务 ID。**提交请求出现网络超时则结果不确定，不要自动重试 POST**；请求可能已被服务受理。记录已知 ID，继续查询；若没有取得 ID，应核对本地任务记录/目标数据再由用户决定。轮询超时也不表示任务失败，不要生成第二个重复任务。

程序重启后，未完成的排队/执行任务会标为 ERROR，不自动重放；之前已完成的任务和方案保留。任务参数不持久化，不能通过 ID 重新执行。Redis 只是可选引用缓存，不能据此假设多实例共享任务队列。

接口的成功状态说明该任务执行完成，不提供逐表实际行数或任意 SELECT API。如果需要向用户证明业务数据正确，使用用户授权的数据库查询工具核查行数、唯一性、外键、范围和业务约束；没有查询能力时明确报告该验证尚未执行。

## 7. 方案保存、复用

| 方法与路径 | 请求/结果 |
| --- | --- |
| `POST /scheme/save` | `{"name":"方案名","dataSource":{...},"tables":[...]}`；返回 `data` 为数值 ID，同名更新保留 ID |
| `GET /scheme/list` | 返回方案数组 |
| `GET /scheme/{id}` | 返回方案对象 |
| `DELETE /scheme/{id}` | 删除该方案，不删除目标数据库数据或任务记录 |

方案对象的 `dataSourceJson` 和 `tablesJson` 是 **JSON 字符串**，需要 JSON 解析一次，再拼成 `{dataSource, tables}` 提交。它们不是已经解析好的嵌套对象。加载方案不等于授权自动重放；应按用户本次要求调整数量、目标及唯一值规则。

方案保存会持久化连接密码，用户不需要复用时不必保存。不要把凭据、SQLite 数据文件、私有生成请求提交到 Git、上传到聊天或写进报告。

## 8. 可直接复用的 Python 客户端

仓库提供 [examples/ai_client.py](examples/ai_client.py)，只依赖 Python 标准库。AI 可以导入 `FakerClient`，按顺序调用 `check → connect → tables/columns → submit → wait`。不需要安装 Python 才能运行 JAR；Python 只是可选调用示例。

已有示例表、账号和目标权限后，在请求文件中调整 URL/用户名，通过环境变量提供密码。先检查连接及元数据：

```powershell
$env:FAKER_TARGET_PASSWORD = '通过安全方式取得的测试密码'
python examples/ai_client.py --request examples/generate-demo.json
```

确认请求内容符合用户本次任务后，仅提交一次：

```powershell
python examples/ai_client.py --request examples/generate-demo.json --submit --timeout 300
python examples/ai_client.py --request examples/generate-tree.json --submit --timeout 300
```

端口改变时追加 `--base-url http://127.0.0.1:8095`。客户端会输出任务 ID 和终态，业务失败和等待超时以非零退出；它不会自动重提任务。示例 JSON 需要按真实元数据调整；自动化调用优先读取元数据后合并 `regs`，不要在程序里固定 JDBC 类型。

最小导入示例：

```python
import json, os, sys
sys.path.insert(0, "examples")
from ai_client import FakerClient

client = FakerClient("http://127.0.0.1:8085")
with open("examples/generate-demo.json", encoding="utf-8") as f:
    request = json.load(f)
request["dataSource"]["password"] = os.environ["FAKER_TARGET_PASSWORD"]
client.check()
client.connect(request["dataSource"])
for table in request["tables"]:
    metadata = {col["name"]: col for col in client.columns(request["dataSource"], table["name"])}
    for column in table["columns"]:
        rules = column["regs"]
        column.update(metadata[column["name"]])
        column["regs"] = rules
task_id = client.submit(request)  # 只在用户已授权该目标和生成范围时调用
print("taskId:", task_id)
print(client.wait(task_id, timeout=300))
```

## 9. 交付结果给人

报告基地址、目标库与表名（不含密码）、任务 ID、请求行数/树节点规模、最终状态和已完成的数据库核查。若只取得任务 ID，说明“已提交”；若仍在运行，说明“处理中”；若错误或结果不确定，保留 ID 和错误摘要，不编造成功或行数。内存缓存达到上限时减小任务、提高上限或经用户选择启用 Redis，不能通过丢弃引用规则来“完成”原任务。
