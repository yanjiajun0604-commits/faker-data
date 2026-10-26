# 方案脱敏分享与本机绑定（源码预览）

[English](portable-schemes.en.md) · [机器契约](../contracts/README.md)

分享请用 export_scheme。旧 `/scheme/{id}` 响应和本机 SQLite 数据库属于**私人备份**，包含连接配置，不是公开分享格式。

## 导出

MCP：`export_scheme(scheme_id,connection_ref="local")`；HTTP：`GET /scheme/{id}/export?connectionRef=local`。返回 format 为 faker-data.scheme、formatVersion 为 1 的文档，保留普通规则、虚拟字段、既有来源、父行展开、归属和树配置，去掉运行时依赖标志。connectionRef 只是公开逻辑标签，不是主机、JDBC URL 或凭据。

不携带连接对象、可执行文件和本机扩展 manifest。默认遮蔽凭据命名列的规则、existingData.where 的本地筛选值、敏感匹配／筛选常量、识别出的连接秘密／JDBC／授权／私钥／路径字符串，以及不透明扩展参数；仅保留 sourceField、binaryEncoding、role、formatVersion 等少量有界参数。遮蔽节点变为 null，精确 RFC6901 JSON 指针列在 requiredLocalValues 中；不要删掉清单或猜值填充。

发布前仍需检查业务名称、结构和常量，不承诺自动识别全部个人信息或业务秘密。高级条件表达式文本可能保留并标为需可信代码审阅，但不打包程序文件。导入／导出均不修改目标库。

## 不把私有值发送给 AI 的导入方式

1. 阅读文档的 requiredLocalValues、requiredExtensions、trustedCodeRequired。
2. 在接收机器明确选择本机连接配置，不导入来源机器的连接。
3. 如需替换值，将 [scheme-values.example.json](../integrations/mcp/scheme-values.example.json) 复制到**仓库外**，用实际导出的指针替换示例，再私下填写。JSON 类型需正确：regs 节点需要规则数组，常量节点需要标量。
4. 为适配器设置可选 FAKER_SCHEME_VALUES_FILE，指向该私人文件；连接继续使用 FAKER_CONNECTIONS_FILE。MCP 只传逻辑名称：

```text
import_scheme(document=已审阅文档, connection="test", name="本机订单案例",
              values_profile="demo", replace_existing=false, confirm_trusted_code=false)
```

工具参数里没有替换值／密码。适配器像读取连接配置一样，从私人文件读取后经本机回环请求提交；不要把文件发给 AI、提交仓库或放进截图和公开示例。缺少绑定、未知属性／版本、保留的反序列化类型元数据和额外任意补丁都会拒绝，不静默丢配置。

导入只校验并保存普通方案，不连接目标库、不生成记录、不安装扩展、不执行规则或函数提供者代码。EXT.FIELD、自定义实现和受限纯表达式之外的表达式，需在授权后明确 confirm_trusted_code=true；服务端重新计算，不信任文件自带标志。此门槛只针对导入，正式生成仍属于可信本地执行，不是沙箱，不能用原始保存接口绕过审阅。

同名默认报冲突，只有明确要覆盖时才设置 replace_existing=true；超时先查方案名称与 ID，不盲目重试。导入返回方案 ID，真正生成仍需单独写入授权和稳定请求标识。

## 保存、再次导出与限制

本机 Java 方案仍包含私人连接和绑定值，应保护 data 目录。私有指针元数据与方案原子保存，再导出时会重新遮蔽；校验使用副本，避免排序移动指针。删除方案时同步删除指针元数据。旧式人工编辑可能移动值，改后仍应重新审核导出文件。

导出在同一个本机数据库快照中读取方案与私有指针，避免并发覆盖时混用版本而漏出已绑定的值。

MCP get_scheme 会遮蔽私人配置文件里的字符串；这个脱敏视图不是无损备份，不应保存回去重建绑定方案。用 generate_data(scheme_id=...) 执行本机保存配置，用 export_scheme 生成分享文档；普通规则代码名保持可见。

HTTP 导入为 POST /scheme/import，参数 document/dataSource/replacements/name/replaceExisting/confirmTrustedCode，本机 UI 可以直接提供私人替换值。不会从分享文档推断连接。格式限制：2 MiB、100 张表、每表 550 列（虚拟字段最多 50）、每个规则列表 1024 项、10000 个私人指针、嵌套 40 层、100000 个 JSON 节点；另受语义和运行限制约束。结构校验仍需结合数据库预检和真实业务验收。

契约通过 GET /contracts、GET /contracts/{公开文件名} 或 faker://contracts 获取。需要当前源码，不代表旧发行 JAR 已包含新接口。
