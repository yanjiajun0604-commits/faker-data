# Versioned machine contracts / 版本化机器契约

The normative JSON shapes are Draft 7 schemas with local references; no remote schema is fetched by the Java importer. Shape validation and ordinary semantic/dependency checks are separate from database/runtime/business acceptance.

JSON 结构采用 Draft 7 与本地引用，Java 导入器不下载远程 schema。结构、规则语义与依赖校验，不等于数据库运行与业务验收。

- [manifest-v1.json](manifest-v1.json): version/index. HTTP `GET /contracts`; MCP resource `faker://contracts`.
- [scheme-v1.schema.json](scheme-v1.schema.json): redacted portable ordinary schemes, including virtual fields, source/parent/ownership/tree configuration. Only `formatVersion=1` is accepted. / 脱敏普通方案结构，仅接受版本 1。
- [rules-v1.json](rules-v1.json): built-in rule codes and preview scope. / 内置规则与预览范围。
- [diagnostics-v1.json](diagnostics-v1.json): diagnostic fields and known code examples, not an exhaustive enum of all future failures. / 诊断字段与稳定错误示例。
- Field extension [manifest](field-extension-manifest-v1.schema.json), [request](field-extension-request-v1.schema.json) and [response](field-extension-response-v1.schema.json): current `faker-field-extension/1` shapes. Paths, allowlists, output/binary budgets, hashes and exact requested identities are additionally enforced by runtime. / 扩展当前协议；路径、白名单、容量、摘要与请求身份仍由运行时检查。

Extension executables/manifests are trusted **local** code and are never installed or executed by scheme import. Portable files can retain condition-expression text; advanced expressions/extensions require review and explicit confirmation. Schemas are not a sandbox. Read [sharing and local bindings](../docs/portable-schemes.en.md) before distributing a scheme.

扩展程序／manifest 属于可信本地代码，导入方案不会安装或执行它们。方案可保留条件表达式文本，高级表达式／扩展需要审阅并明确确认；schema 不是沙箱。分享前阅读[方案分享与本机绑定](../docs/portable-schemes.md)。

The Java importer implements only the bounded schema subset authored here and fails startup on an unsupported schema keyword. Independent validation: install existing MCP requirements, then `python -m unittest discover -s contracts -p test_contracts.py`. Changes that break shape/meaning require a new format version rather than silent unknown-field dropping.

Java 导入器只实现这里使用的有界 schema 子集，遇到未支持的 schema 关键字会拒绝启动；独立测试用既有 MCP 依赖中的 jsonschema。改变结构或语义时应升级格式版本，不静默丢弃未知字段。
