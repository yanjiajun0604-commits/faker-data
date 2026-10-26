package faker.data.service.db;

import java.sql.*;
import java.util.Locale;

/** MySQL 必须核对表引擎，不能仅凭连接支持事务就承诺整次回滚。 */
public final class JdbcTableTransactions {
    private JdbcTableTransactions(){}
    public static String storageEngine(Connection connection,String catalog,String table) throws SQLException {
        if(!connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql"))return null;
        try(PreparedStatement statement=connection.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
            statement.setString(1,catalog);statement.setString(2,table);statement.setQueryTimeout(5);
            try(ResultSet rows=statement.executeQuery()){return rows.next()?rows.getString(1):null;}
        }
    }
    public static boolean transactional(Connection connection,String engine) throws SQLException {
        if(!connection.getMetaData().supportsTransactions())return false;
        return !connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql") || "InnoDB".equalsIgnoreCase(engine);
    }
    public static void require(Connection connection,String catalog,String table) throws SQLException {
        if(!transactional(connection,storageEngine(connection,catalog,table)))throw new IllegalArgumentException("NON_TRANSACTIONAL_TARGET: "+table);
    }
}
