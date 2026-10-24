# 访问地址 / Access URLs

## 中文

2026-10-24：当前源码构建及本地测试版已去除应用路径前缀，尚未发布新版本。请按实际使用的制品选择地址。

| 使用的版本 | 网页地址 | API 基地址 |
| --- | --- | --- |
| 当前源码构建 / 本地测试版 | `http://127.0.0.1:8085/` | `http://127.0.0.1:8085` |
| 已发布 v0.1.0 JAR | `http://127.0.0.1:8085/faker/` | `http://127.0.0.1:8085/faker` |

新版示例：`GET http://127.0.0.1:8085/asyncTask/list`。前端、AI 客户端和 API 都使用根路径，不需要增加 `/faker`。更换端口时只调整端口号。

IDEA 已运行的实例需重启加载新配置；更改源码不会自动改变已有进程的访问地址。新版源码应同时重建前端和后端，不能只给旧版 JAR 改 context-path：旧前端仍会请求旧 API 前缀。

版本过渡期间，AI 调用旧版时显式传入 `--base-url http://127.0.0.1:8085/faker`，调用本地新版时传入 `--base-url http://127.0.0.1:8085`。发布新版时将一起同步下载说明、AI 指南和客户端默认地址。

## English

As of 2026-10-24, current source/local test builds no longer use an application context prefix. No new release has been published. Choose the URL for the actual artifact you are running.

| Build | Web UI | API base URL |
| --- | --- | --- |
| Current source / local test build | `http://127.0.0.1:8085/` | `http://127.0.0.1:8085` |
| Published v0.1.0 JAR | `http://127.0.0.1:8085/faker/` | `http://127.0.0.1:8085/faker` |

For the new local build, use `GET http://127.0.0.1:8085/asyncTask/list`; the UI, agent client and API all use the root path. Restart an IDEA instance to load the new configuration. Rebuild both frontend and backend when using the updated source; changing only an old JAR's context path leaves its frontend calling the old API prefix.

During this transition, pass `--base-url http://127.0.0.1:8085/faker` for v0.1.0 or `--base-url http://127.0.0.1:8085` for the unpublished local build. Download instructions, agent guides and client defaults will be aligned when the next version is published.
