package faker.data.service.db.impl;

import faker.data.domain.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 锁定 MySQL 表列表过滤的行为：
 * <ul>
 *   <li>MySQL 中 schema 无实际意义，schemaPattern() 必须返回 null，否则 Connector/J 8.x
 *       会忽略 schemaPattern 并返回所有库的所有表</li>
 *   <li>MySQL 中 catalog 才是过滤数据库（database）的真正参数</li>
 * </ul>
 */
class MySQLJdbcServiceTest {

    private DataSource ds(String url) {
        DataSource ds = new DataSource();
        ds.setUrl(url);
        ds.setUsername("root");
        ds.setPassword("any");
        return ds;
    }

    @Test
    void schemaPattern_returnsNull_because_mysql_has_no_schema() {
        MySQLJdbcService svc = new MySQLJdbcService(ds("jdbc:mysql://localhost:3306/fakertest"));
        assertNull(svc.schemaPattern());
    }

    @Test
    void catalog_returnsDatabaseNameFromUrl() {
        MySQLJdbcService svc = new MySQLJdbcService(ds("jdbc:mysql://localhost:3306/fakertest"));
        assertEquals("fakertest", svc.catalog());
    }

    @Test
    void catalog_stripsQueryStringFromUrl() {
        MySQLJdbcService svc = new MySQLJdbcService(
                ds("jdbc:mysql://localhost:3306/fakertest?useSSL=false&serverTimezone=UTC"));
        assertEquals("fakertest", svc.catalog());
    }

    @Test
    void catalog_returnsNull_whenUrlHasNoDatabase() {
        // URL 缺少 /db 段时，catalog 必须为 null，避免被误传空串
        MySQLJdbcService svc = new MySQLJdbcService(ds("jdbc:mysql://localhost:3306"));
        assertNull(svc.catalog());
    }
}
