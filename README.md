# faker-data

基于 Java / Spring Boot 的数据库测试数据生成后端，根据字段规则批量生成模拟数据，支持跨表引用、条件规则和树形结构。

## 功能

- MySQL、Oracle、达梦数据库连接及表、字段元数据读取。
- 姓名、手机号、标识符、UUID 等固定类型生成规则。
- 数值、日期、字符串区间及自增规则。
- 手工输入数据、加权选取、同表与跨表引用。
- 基于 SpEL 的条件规则及内置函数。
- 树形拓扑、父节点引用与层级编码。
- 生成方案保存、读取和删除，异步生成任务执行。

本仓库为后端项目，前端不包含在此次发布中。

## 技术与依赖

- Spring Boot 2.3.9.RELEASE、Maven、Java。
- MySQL（应用配置及方案存储）、Redis（数据引用缓存）。
- JDBC 驱动、Hutool、data-factory、Lombok。

当前 POM 依赖 `pers.bbs:asynctask:0.0.1-SNAPSHOT` 和 `dm:dm-connector-java:8.1.1.126`。本仓库未附带这些依赖的源码或二进制，也未配置它们的公开仓库。构建前需要在具有使用权限的前提下，将相应制品及其传递依赖安装到本机 Maven 仓库，或配置可访问的制品仓库。首次克隆后能否直接构建尚未验证。

## 本地运行

先准备独立的 MySQL 测试库、数据库账号和 Redis，再设置环境变量。下面是 PowerShell 示例，密码需要替换为你自己的值：

```powershell
$env:FAKER_DB_URL = 'jdbc:mysql://localhost:3306/faker_data?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC'
$env:FAKER_DB_USERNAME = 'faker'
$env:FAKER_DB_PASSWORD = '<your-local-database-password>'
$env:FAKER_REDIS_HOST = 'localhost'
$env:FAKER_REDIS_PORT = '6379'
$env:FAKER_REDIS_PASSWORD = ''
mvn spring-boot:run
```

默认端口为 `8085`，接口前缀为 `/faker`。端口可通过 `SERVER_PORT` 修改。

应用的 JPA 配置为 `ddl-auto: update`，启动时可能创建或调整应用表。请使用独立测试库。

## 部分接口

以下路径均带 `/faker` 前缀；数据库元数据接口接收 JSON 数据源对象。

| 路径 | 用途 |
| --- | --- |
| `/db/connect/test` | 测试目标数据库连接 |
| `/db/showTables` | 获取目标数据库表 |
| `/db/showColumns/{tableName}` | 获取表字段 |
| `POST /scheme/save` | 保存或更新同名生成方案 |
| `GET /scheme/list` | 查看生成方案 |
| `GET /scheme/{id}` | 获取生成方案详情 |
| `DELETE /scheme/{id}` | 删除生成方案 |

数据源对象包含 `type`、`url`、`username`、`password`。生成任务使用异步任务组件，具体参数参考 `GenerateVO`、`TableVO` 和 `MetaTypeVO`。

## 测试与文档

依赖就绪后可运行：

```shell
mvn test
```

仓库保留 MySQL 元数据过滤、条件规则、自增规则及树形拓扑等现有测试。此次发布未执行构建或测试，不代表运行验收已通过。

- [SpEL 内置函数说明](docs/spel-functions-guide.md)
- [树形数据查询示例](docs/tree_visualize.sql)

## 使用边界

本项目会向目标数据库写入数据；仅在授权的测试环境使用。目前接口未提供完整的访问控制，生成方案还会序列化保存所填的数据源信息（含密码），因此仅适合受控的本地或测试网络，勿直接开放至公网。

此次 GitHub 发布采用当前源码快照，未迁移原 Git 历史、本机配置、日志或 AI 工具目录。配置中的连接凭据已改为环境变量。

## 许可证

当前尚未指定开源许可证。公开仓库不等于已授予任意使用、修改和分发的许可；正式采用前需由维护者确定许可证，并核对第三方依赖的许可要求。
