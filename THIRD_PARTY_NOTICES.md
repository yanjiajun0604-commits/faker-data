# 第三方组件

根目录 MIT 许可证适用于本项目自有源码，不替换依赖的许可证。源码通过 Maven/npm 引用第三方组件；可执行 JAR 含 Maven 运行依赖，静态前端含 npm 构建依赖。各组件的版权、许可、NOTICE 以其发布制品及上游仓库为准，二次分发时应保留相应声明。

主要组件：Spring Boot/Spring Framework、SQLite JDBC（含 SQLite 原生库）、Lettuce、Fastjson、Hutool、data-factory、MySQL Connector/J、Oracle JDBC/orai18n、达梦 JDBC、Vue、Element UI、Axios、core-js。版本及完整依赖入口为 `pom.xml` 与 `frontend/package-lock.json`；测试使用 JUnit、Spring Boot Test 和 H2。

特别注意 JDBC 驱动分别由对应厂商授权，不应因本项目采用 MIT 就将第三方驱动视作 MIT 代码。本说明不是将第三方代码重新许可。

- [MySQL Connector/J](https://github.com/mysql/mysql-connector-j)
- [Oracle JDBC 下载与许可入口](https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html)
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc)
- [Vue](https://github.com/vuejs/vue)
- [Element UI](https://github.com/ElemeFE/element)
