package faker.data.service.db;

import faker.data.domain.DataSource;
import faker.data.domain.JdbcType;
import faker.data.service.db.impl.DmJdbcService;
import faker.data.service.db.impl.MySQLJdbcService;
import faker.data.service.db.impl.OracleJdbcService;
import faker.data.service.generate.engine.GenerationEngine;

public class JdbcServiceFactory {

    /**
     * 获取jdbc service
     *
     * @param dataSource 数据源
     * @return jdbc service
     */
    public static JdbcService getJdbcService(DataSource dataSource) {
        return getJdbcService(dataSource, null);
    }

    public static JdbcService getJdbcService(DataSource dataSource, GenerationEngine engine) {
        if(dataSource==null || dataSource.getType()==null)throw new IllegalArgumentException("Database connection type is required");
        if (dataSource.getType() == JdbcType.MYSQL) {
            return new MySQLJdbcService(dataSource, engine);
        } else if (dataSource.getType() == JdbcType.ORACLE) {
            return new OracleJdbcService(dataSource, engine);
        } else if (dataSource.getType() == JdbcType.DM) {
            return new DmJdbcService(dataSource, engine);
        } else {
            throw new RuntimeException("不支持该数据库");
        }
    }

}
