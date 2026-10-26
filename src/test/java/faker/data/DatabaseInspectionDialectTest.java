package faker.data;

import faker.data.domain.DatabaseInspectionRequest;
import faker.data.domain.DataSource;
import faker.data.domain.JdbcType;
import faker.data.service.db.ReadOnlyDatabaseInspector;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 仅验证 ROWNUM 分支和 schema 范围；不替代原生 Oracle／达梦兼容测试。 */
class DatabaseInspectionDialectTest {
    @Test void rownumBranchRespectsOrderAndBoundedProjection() throws Exception {
        for(JdbcType type:Arrays.asList(JdbcType.ORACLE,JdbcType.DM)) {
            String url="jdbc:h2:mem:inspection_"+type+";MODE=Oracle;DB_CLOSE_DELAY=-1";
            try(Connection c=DriverManager.getConnection(url,"sa","fixture")) {
                c.createStatement().execute("CREATE TABLE SAMPLE(ID BIGINT PRIMARY KEY, NAME VARCHAR(1000), PAYLOAD BLOB)");
                c.createStatement().execute("INSERT INTO SAMPLE VALUES(1,'first',NULL),(2,'second',NULL),(3,'third',NULL)");
                c.createStatement().execute("CREATE SCHEMA OTHER");
                c.createStatement().execute("CREATE TABLE OTHER.HIDDEN(ID INT)");
            }
            DataSource source=new DataSource();source.setType(type);source.setUrl(url);source.setUsername("sa");source.setPassword("fixture");
            ReadOnlyDatabaseInspector inspector=new ReadOnlyDatabaseInspector(()->{
                try{return DriverManager.getConnection(url,"sa","fixture");}catch(SQLException e){throw new RuntimeException(e);}
            },null,"PUBLIC",source);
            DatabaseInspectionRequest request=new DatabaseInspectionRequest();request.setTable("SAMPLE");
            request.setColumns(Arrays.asList("ID","NAME","PAYLOAD"));request.setLimit(2);
            DatabaseInspectionRequest.Order order=new DatabaseInspectionRequest.Order();order.setColumn("ID");order.setDirection("DESC");request.setOrderBy(Arrays.asList(order));
            Map<String,Object> data=inspector.read(request);
            assertEquals(2,data.get("rowCount"));assertEquals(true,data.get("truncated"));
            assertEquals("3",((List<?>)((List<?>)data.get("rows")).get(0)).get(0));
            request.setTable("HIDDEN");assertThrows(IllegalArgumentException.class,()->inspector.read(request));
        }
    }
}
