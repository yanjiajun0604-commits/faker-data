# Security policy / 安全政策

## Reporting a vulnerability / 报告漏洞

Use **Security → Advisories → Report a vulnerability** in this repository for a private report, when available:

https://github.com/yanjiajun0604-commits/faker-data/security/advisories/new

Please include the affected commit or release, a minimal reproduction, expected and actual behavior, and the impact. Remove credentials, personal data, database contents and internal addresses from attachments. Do not open a public issue containing an exploit or secret. If private reporting is unavailable, open a public issue asking the maintainer to enable a private reporting channel, without disclosing vulnerability details.

请优先使用仓库 Security → Advisories → Report a vulnerability 私密报告。提供受影响版本或提交、最小复现及影响，并脱敏附件。不要在公开 Issue 中提交漏洞利用细节或密钥；若私密报告不可用，只公开请求维护者开启报告渠道。

## Safe operation / 安全使用

- Run faker-data on a trusted local machine. It is an unauthenticated tool and binds to loopback by default; do not expose it directly to the Internet.
- Use an authorized test database and an account limited to the intended test schema. Generating data writes to the target database.
- Saved schemes contain connection credentials. Protect the data directory and its backups; never commit them or real connection details to GitHub. Rotate any exposed secret at its provider; deleting a file does not remove an already leaked credential.
- Check the actual artifact and checksum. The published v0.1.0 JAR uses `/faker/`; current source builds use `/`. A fix on main is not automatically present in an older release.

程序默认仅监听本机且无登录认证，不要直接开放公网。只连接已获授权的测试库，限制数据库账号权限。方案含连接凭据，需保护 data 目录及备份；已泄露凭据须在提供方撤销或轮换。main 上的修复不代表旧下载包已更新。

## Maintenance / 维护边界

Security fixes are developed against main. Older releases may not receive backports. Dependabot alerts and update pull requests require maintainer review and passing CI; they are not automatically merged or released. No response-time guarantee or production certification is implied.

安全修复以 main 为基础，旧版不保证回补。依赖更新需人工审核与 CI，不自动合并、发布；本政策不承诺响应时限或生产认证。
