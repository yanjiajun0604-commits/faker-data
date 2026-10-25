# 访问地址 / Access URLs

## 中文

v0.1.1 与当前源码使用根地址。旧版 v0.1.0 附件保留原访问路径，请按实际制品选择地址。

| 使用的版本 | 网页地址 | API 基地址 |
| --- | --- | --- |
| v0.1.1 / 当前源码 | `http://127.0.0.1:8085/` | `http://127.0.0.1:8085` |
| 已发布 v0.1.0 JAR | `http://127.0.0.1:8085/faker/` | `http://127.0.0.1:8085/faker` |

新版示例：`GET http://127.0.0.1:8085/asyncTask/list`。前端、AI 客户端和 API 都使用根路径，不需要增加 `/faker`。更换端口时只调整端口号。

IDEA 已运行的实例需重启加载新配置；更改源码不会自动改变已有进程的访问地址。新版源码应同时重建前端和后端，不能只给旧版 JAR 改 context-path：旧前端仍会请求旧 API 前缀。

AI 调用旧版时显式传入 `--base-url http://127.0.0.1:8085/faker`，新版使用默认值或 `--base-url http://127.0.0.1:8085`。升级时先停止旧应用，不能让两个实例同时使用同一数据目录。更换工作目录时可通过 `FAKER_DATA_DIR` 指定要保留的数据目录。

## English

v0.1.1 and current source builds use the root URL. Older v0.1.0 downloads retain their original prefix. Choose the URL for the actual artifact you are running.

| Build | Web UI | API base URL |
| --- | --- | --- |
| v0.1.1 / current source | `http://127.0.0.1:8085/` | `http://127.0.0.1:8085` |
| Published v0.1.0 JAR | `http://127.0.0.1:8085/faker/` | `http://127.0.0.1:8085/faker` |

For the new local build, use `GET http://127.0.0.1:8085/asyncTask/list`; the UI, agent client and API all use the root path. Restart an IDEA instance to load the new configuration. Rebuild both frontend and backend when using the updated source; changing only an old JAR's context path leaves its frontend calling the old API prefix.

Pass `--base-url http://127.0.0.1:8085/faker` for v0.1.0; use the client default or `--base-url http://127.0.0.1:8085` for v0.1.1. Stop the older process before upgrading and never run two instances with the same data directory. Set `FAKER_DATA_DIR` when changing working directories to retain your application data.
