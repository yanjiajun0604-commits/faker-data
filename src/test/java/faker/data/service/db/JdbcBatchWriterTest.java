package faker.data.service.db;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.engine.JdbcBatchWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JdbcBatchWriterTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void driversWithoutBatchSupportStillWriteTheFinalPartialBatch(boolean metadataUnavailable) throws Exception {
        DriverManagerDataSource target = new DriverManagerDataSource(
                "jdbc:h2:mem:writer_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "test");
        JdbcTemplate jdbc = new JdbcTemplate(target);
        jdbc.execute("CREATE TABLE samples(id BIGINT PRIMARY KEY,label VARCHAR(30))");
        AbstractDataSource fallback = new AbstractDataSource() {
            public Connection getConnection() throws SQLException { return wrap(target.getConnection()); }
            public Connection getConnection(String user, String password) throws SQLException { return wrap(target.getConnection(user,password)); }
            private Connection wrap(Connection connection) {
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy,method,args) -> {
                    if (method.getName().equals("getMetaData")) {
                        DatabaseMetaData metadata = connection.getMetaData();
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class}, (p,m,a) -> {
                            if(m.getName().equals("supportsBatchUpdates")) {
                                if(metadataUnavailable)throw new SQLFeatureNotSupportedException("Batch support metadata unavailable");
                                return false;
                            }
                            return invoke(m,metadata,a);
                        });
                    }
                    return invoke(method,connection,args);
                });
            }
        };
        try {
            ColumnInfo id=new ColumnInfo();id.setName("id");id.setJavaType("java.lang.Long");
            ColumnInfo label=new ColumnInfo();label.setName("label");label.setJavaType("java.lang.String");
            new JdbcBatchWriter("samples",Arrays.asList(id,label)).write(new JdbcTemplate(fallback),1003,
                    index->new Object[]{String.valueOf(index+1),"row-"+(index+1)},count->{});
            assertEquals(1003,jdbc.queryForObject("SELECT COUNT(*) FROM samples",Integer.class));
            assertEquals("row-1003",jdbc.queryForObject("SELECT label FROM samples WHERE id=1003",String.class));
        } finally { jdbc.execute("SHUTDOWN"); }
    }
    private static Object invoke(Method method,Object target,Object[] args) throws Throwable {
        try {return method.invoke(target,args);}
        catch(InvocationTargetException ex){throw ex.getCause();}
    }
}
