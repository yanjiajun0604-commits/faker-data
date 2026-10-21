package faker.data.service.db;

import faker.data.domain.DataSource;
import faker.data.domain.JdbcType;
import faker.data.service.db.impl.DmJdbcService;
import faker.data.service.db.impl.MySQLJdbcService;
import faker.data.service.db.impl.OracleJdbcService;

public class JdbcServiceFactory {

    /**
     * 获取jdbc service
     *
     * @param dataSource 数据源
     * @return jdbc service
     */
    public static JdbcService getJdbcService(DataSource dataSource) {
        if (dataSource.getType() == JdbcType.MYSQL) {
            return new MySQLJdbcService(dataSource);
        } else if (dataSource.getType() == JdbcType.ORACLE) {
            return new OracleJdbcService(dataSource);
        } else if (dataSource.getType() == JdbcType.DM) {
            return new DmJdbcService(dataSource);
        } else {
            throw new RuntimeException("不支持该数据库");
        }
    }

}