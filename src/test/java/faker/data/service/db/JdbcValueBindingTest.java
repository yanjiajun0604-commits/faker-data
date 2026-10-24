package faker.data.service.db;
import faker.data.domain.*;
import faker.data.service.generate.engine.JdbcValueBinder;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JdbcValueBindingTest {
    @Test void mysqlLocalDateTimeMetadataAcceptsEpochDateRule() throws Exception {
        faker.data.domain.DataSource ds=new faker.data.domain.DataSource();ds.setUrl("jdbc:h2:mem:date_binding");ds.setUsername("sa");ds.setPassword("test");
        ColumnInfo date=new ColumnInfo();date.setName("created_at");date.setJavaType("java.time.LocalDateTime");
        try(Connection c=DriverManager.getConnection(ds.getUrl(),"sa","test")){
            c.createStatement().execute("CREATE TABLE dates(created_at TIMESTAMP)");
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO dates VALUES(?)")){
                JdbcValueBinder.compile(date,1).bind(ps,"1735689600000");ps.executeUpdate();
            }
            try(ResultSet rs=c.createStatement().executeQuery("SELECT created_at FROM dates")){assertTrue(rs.next());assertNotNull(rs.getTimestamp(1));}
        }
    }
    @Test void bigintAndDecimalNullUseMatchingJdbcTypes() throws Exception {
        faker.data.domain.DataSource ds=new faker.data.domain.DataSource();ds.setUrl("jdbc:h2:mem:binding");ds.setUsername("sa");ds.setPassword("test");
        try(Connection c=DriverManager.getConnection(ds.getUrl(),"sa","test")){
            c.createStatement().execute("CREATE TABLE typed_values(id BIGINT PRIMARY KEY,amount DECIMAL(15,2))");
            ColumnInfo id=new ColumnInfo();id.setName("id");id.setJavaType("java.lang.Long");
            ColumnInfo amount=new ColumnInfo();amount.setName("amount");amount.setJavaType("java.math.BigDecimal");
            Map<String,Object> row=new HashMap<>();row.put("id","9000000001");row.put("amount",null);
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO typed_values VALUES(?,?)")){
                JdbcValueBinder.compile(id,1).bind(ps,row.get("id"));
                JdbcValueBinder.compile(amount,2).bind(ps,row.get("amount"));ps.executeUpdate();
            }
            try(ResultSet rs=c.createStatement().executeQuery("SELECT id,amount FROM typed_values")){
                assertTrue(rs.next());assertEquals(9000000001L,rs.getLong(1));assertNull(rs.getBigDecimal(2));
            }
        }
    }
}
