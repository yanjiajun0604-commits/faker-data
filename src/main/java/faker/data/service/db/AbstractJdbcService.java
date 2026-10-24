package faker.data.service.db;

import faker.data.domain.*;
import faker.data.service.generate.engine.GenerationEngine;
import faker.data.task.AsyncTaskExecutor;
import faker.data.task.AsyncTaskMonitor;
import faker.data.utils.ApplicationContextProvider;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StringUtils;
import java.sql.*;
import java.util.*;

public abstract class AbstractJdbcService implements JdbcService {
    public static final int BATCH_SIZE = faker.data.service.generate.engine.JdbcBatchWriter.BATCH_SIZE;
    private final DataSource dataSource;
    private final GenerationEngine generationEngine;

    public AbstractJdbcService(DataSource dataSource) {
        this(dataSource, null);
    }

    public AbstractJdbcService(DataSource dataSource, GenerationEngine generationEngine) {
        this.generationEngine = generationEngine;
        this.dataSource = dataSource;
        checkDataSource(dataSource);
    }

    /**
     * 校验数据源信息是否有效
     *
     * @param dataSource 数据源
     */
    private void checkDataSource(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null. ");
        }
        if (!StringUtils.hasText(dataSource.getUrl())) {
            throw new IllegalStateException("jdbc url is null. ");
        }
        if (!StringUtils.hasText(dataSource.getUsername()) || !StringUtils.hasText(dataSource.getPassword())) {
            throw new IllegalStateException("jdbc user or password is null. ");
        }
    }

    protected DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 关闭(释放)资源
     *
     * @param conn Connection
     */
    protected void close(Connection conn) {
        close(conn, null, null);
    }

    /**
     * 关闭(释放)资源
     *
     * @param conn Connection
     * @param ps   PreparedStatement
     * @param rs   ResultSet
     */
    protected void close(Connection conn, Statement ps, ResultSet rs) {
        //关闭ResultSet
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                rs = null;
            }
        }
        //关闭PreparedStatement
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                ps = null;
            }
        }
        //关闭Connection
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                conn = null;
            }
        }
    }

    @Override
    public boolean test() {
        Connection conn = null;
        try {
            conn = getConnection();
            if (conn == null) {
                return false;
            }

            DatabaseMetaData meta = conn.getMetaData();
            if (meta == null) {
                return false;
            }
        } catch (SQLException e) {
            throw DatabaseConnectionException.from(e, dataSource);
        } finally {
            close(conn);
        }
        return true;
    }

    /**
     * 获取连接
     *
     * @return Connection
     */
    protected Connection getConnection() {
        try {
            Class.forName(loadDriverClass());
            return DriverManager.getConnection(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
        } catch (Exception e) {
            throw DatabaseConnectionException.from(e, dataSource);
        }
    }

    @Override
    public List<Map<String, Object>> listAllTables(Integer withSub) {
        Connection conn = getConnection();
        if (conn == null) {
            return null;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        ResultSet rs = null;
        try {
            //参数1 int resultSetType
            //ResultSet.TYPE_FORWORD_ONLY 结果集的游标只能向下滚动。
            //ResultSet.TYPE_SCROLL_INSENSITIVE 结果集的游标可以上下移动，当数据库变化时，当前结果集不变。
            //ResultSet.TYPE_SCROLL_SENSITIVE 返回可滚动的结果集，当数据库变化时，当前结果集同步改变。

            //参数2 int resultSetConcurrency
            //ResultSet.CONCUR_READ_ONLY 不能用结果集更新数据库中的表。
            //ResultSet.CONCUR_UPDATETABLE 能用结果集更新数据库中的表

            DatabaseMetaData meta = conn.getMetaData();
            //目录名称; 数据库名; 表名称; 表类型;
            rs = meta.getTables(catalog(), schemaPattern(), tableNamePattern(), types());
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", rs.getString("TABLE_NAME"));
                //加上字段详情信息子表，复用同一连接避免 2000 次连接建立
                if (withSub.equals(1)) {
                    List<ColumnInfo> columnInfos = doListAllFields(conn, rs.getString("TABLE_NAME"));
                    map.put("columns", columnInfos);
                }
                result.add(map);
            }
        } catch (Exception e) {
            throw DatabaseConnectionException.from(e, dataSource);
        } finally {
            close(conn, null, rs);
        }

        return result;
    }

    @Override
    public List<ColumnInfo> listAllFields(String tableName) {
        Connection conn = getConnection();
        if (conn == null) {
            return null;
        }
        try {
            return doListAllFields(conn, tableName);
        } finally {
            close(conn, null, null);
        }
    }

    /**
     * 获取单表的列信息，复用外部传入的连接，避免 N+1 连接问题。
     * 内部不关闭 conn，由调用方负责关闭。
     */
    protected List<ColumnInfo> doListAllFields(Connection conn, String tableName) {
        PreparedStatement preparedStatement = null;
        ResultSetMetaData resultSetMetaData;
        List<ColumnInfo> res = new ArrayList<>();
        try {
            preparedStatement = conn.prepareStatement("select * from " + tableName + " where 1=2");
            resultSetMetaData = preparedStatement.executeQuery().getMetaData();
            int columnCount = resultSetMetaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                ColumnInfo columnInfo = new ColumnInfo();
                columnInfo.setName(resultSetMetaData.getColumnName(i));
                columnInfo.setJavaType(resultSetMetaData.getColumnClassName(i));
                columnInfo.setDbType(resultSetMetaData.getColumnTypeName(i).toLowerCase());
                columnInfo.setLength(resultSetMetaData.getColumnDisplaySize(i));
                columnInfo.setNullable(resultSetMetaData.isNullable(i));
                res.add(columnInfo);
            }
        } catch (Exception e) {
            throw DatabaseConnectionException.from(e, dataSource);
        } finally {
            // 只关闭 PreparedStatement，不关闭 conn
            close(null, preparedStatement, null);
        }
        return res;
    }

    /**
     * a catalog name; must match the catalog name as it is stored in the database; "" retrieves those without a catalog; null means that the catalog name should not be used to narrow the search
     */
    protected String catalog() {
        return null;
    }

    /**
     * a table name pattern; must match the table name as it is stored in the database
     */
    protected String tableNamePattern() {
        return "%";
    }

    /**
     * a list of table types, which must be from the list of table types returned from ,to include; null returns all types
     */
    protected String[] types() {

        return new String[]{"TABLE"};
    }

    /**
     * 加载驱动class
     *
     * @return class
     */
    protected abstract String loadDriverClass();

    /**
     * a schema name pattern; must match the schema name as it is stored in the database; "" retrieves those without a schema; null means that the schema name should not be used to narrow the search
     */
    protected abstract String schemaPattern();


    @Override
    public void generateData(List<TableVO> tables, AsyncTaskMonitor monitor, AsyncTaskExecutor executor) {
        if (!test()) throw new IllegalStateException("连接数据库失败");
        // 兼容直接构造适配器的旧入口；正式任务入口显式注入引擎。
        GenerationEngine engine = generationEngine != null ? generationEngine
                : ApplicationContextProvider.getContext().getBean(GenerationEngine.class);
        engine.generate(getDrivierDatasource(), tables, monitor, executor);
    }

    public javax.sql.DataSource getDrivierDatasource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(loadDriverClass());
        dataSource.setUrl(this.dataSource.getUrl());
        dataSource.setUsername(this.dataSource.getUsername());
        dataSource.setPassword(this.dataSource.getPassword());
        return dataSource;
    }


}
