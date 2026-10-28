package faker.data;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatabaseInspectionTest {
    private static final String URL="jdbc:h2:mem:inspection;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final Path DIR=directory();
    private final JdbcTemplate fixture=new JdbcTemplate(new DriverManagerDataSource(URL,"sa","inspection-fixture"));
    @Autowired TestRestTemplate http;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("faker.data-dir",DIR::toString);}
    static Path directory(){try{return Files.createTempDirectory("faker-inspect-");}catch(Exception e){throw new RuntimeException(e);}}

    @BeforeEach void setup(){
        fixture.execute("DROP ALL OBJECTS");
        fixture.execute("CREATE TABLE customers(id BIGINT PRIMARY KEY, name VARCHAR(100), password VARCHAR(100), memo VARCHAR(2000), payload BLOB)");
        fixture.execute("COMMENT ON TABLE customers IS 'fixture customers'");
        fixture.execute("COMMENT ON COLUMN customers.name IS 'display name'");
        fixture.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, amount DECIMAL(12,2), CONSTRAINT fk_customer FOREIGN KEY(customer_id) REFERENCES customers(id))");
        fixture.execute("CREATE UNIQUE INDEX uq_customer_name ON customers(name)");
        fixture.execute("CREATE SCHEMA other");
        fixture.execute("CREATE TABLE other.private_rows(id BIGINT)");
        fixture.execute("CREATE TABLE customerXarchive(id BIGINT)");
        fixture.execute("CREATE TABLE customer_archive(id BIGINT)");
        fixture.execute("CREATE VIEW customer_view AS SELECT id FROM customers");
        char[] chars=new char[1000];Arrays.fill(chars,'x');
        fixture.update("INSERT INTO customers VALUES(?,?,?,?,?)",9007199254740993L,"O'Reilly","fixture-hidden",new String(chars),new byte[]{1,2,3});
        for(int i=1;i<=110;i++)fixture.update("INSERT INTO customers VALUES(?,?,?,?,?)",i,"customer-"+i,"fixture-hidden","memo",null);
        fixture.update("INSERT INTO orders VALUES(1,?,12.30)",9007199254740993L);
    }
    Map<String,Object> request(){return map("dataSource",map("type","MYSQL","url",URL,"username","sa","password","inspection-fixture"));}
    static Map<String,Object> map(Object...kv){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
    @SuppressWarnings("unchecked") Map<String,Object> call(String endpoint,Map<String,Object> body){
        ResponseEntity<Map> response=http.postForEntity(endpoint,body,Map.class);
        assertEquals(200,response.getStatusCodeValue(),String.valueOf(response.getBody()));
        assertNotNull(response.getBody());return response.getBody();
    }
    @SuppressWarnings("unchecked") Map<String,Object> data(String endpoint,Map<String,Object> body){
        Map<String,Object> r=call(endpoint,body);assertEquals(0,r.get("code"),r.toString());return (Map<String,Object>)r.get("data");
    }
    @SuppressWarnings("unchecked") @Test void discoversOnlyCurrentScopeAndEscapesMetadataWildcards(){
        Map<String,Object> r=request();r.put("limit",100);
        List<Map<String,Object>> tables=(List<Map<String,Object>>)data("/db/inspect",r).get("tables");
        assertTrue(tables.stream().anyMatch(t->"customers".equals(t.get("name"))));
        assertFalse(tables.stream().anyMatch(t->"private_rows".equals(t.get("name"))||"customer_view".equals(t.get("name"))));
        r.put("nameContains","customer_");
        tables=(List<Map<String,Object>>)data("/db/inspect",r).get("tables");
        assertEquals(1,tables.size());assertEquals("customer_archive",tables.get(0).get("name"));
    }
    @SuppressWarnings("unchecked") @Test void describesColumnsCommentsKeysAndIndexes(){
        Map<String,Object> r=request();r.put("table","orders");
        Map<String,Object> d=data("/db/inspect",r);
        List<Map<String,Object>> pk=(List<Map<String,Object>>)d.get("primaryKeys");
        List<Map<String,Object>> fk=(List<Map<String,Object>>)d.get("foreignKeys");
        assertEquals("id",pk.get(0).get("column"));
        assertEquals("customer_id",fk.get(0).get("column"));assertEquals("customers",fk.get(0).get("referencedTable"));
        r.put("table","customers");d=data("/db/inspect",r);
        List<Map<String,Object>> columns=(List<Map<String,Object>>)d.get("columns");
        assertTrue(columns.stream().anyMatch(c->"name".equals(c.get("name"))&&"display name".equals(c.get("remarks"))));
        assertTrue(columns.stream().anyMatch(c->"id".equals(c.get("name"))&&"java.lang.Long".equals(c.get("javaType"))));
        List<Map<String,Object>> indexes=(List<Map<String,Object>>)d.get("indexes");
        assertTrue(indexes.stream().anyMatch(i->"uq_customer_name".equals(i.get("name"))&&Boolean.TRUE.equals(i.get("unique"))));
        assertEquals(Boolean.FALSE,d.get("weakRelationsInferred"));
    }
    @SuppressWarnings("unchecked") @Test void readsBoundedRowsWithBoundValuesAndPreciseNumbers(){
        Map<String,Object> r=request();r.put("table","customers");r.put("columns",Arrays.asList("id","name"));
        r.put("where",Arrays.asList(map("column","name","op","eq","value","O'Reilly")));
        Map<String,Object> d=data("/db/readRows",r);List<List<Object>> rows=(List<List<Object>>)d.get("rows");
        assertEquals(1,rows.size());assertEquals("9007199254740993",rows.get(0).get(0));assertEquals("O'Reilly",rows.get(0).get(1));
        r.put("where",Arrays.asList(map("column","name","op","eq","value","x' OR 1=1 --")));
        assertEquals(0,data("/db/readRows",r).get("rowCount"));
        r.put("where",Collections.emptyList());r.put("limit",2);r.put("orderBy",Arrays.asList(map("column","id","direction","DESC")));
        d=data("/db/readRows",r);assertEquals(2,d.get("rowCount"));assertEquals(Boolean.TRUE,d.get("truncated"));
        rows=(List<List<Object>>)d.get("rows");assertEquals("9007199254740993",rows.get(0).get(0));
        r.put("table","orders");r.put("columns",Arrays.asList("id","amount"));r.put("orderBy",Collections.emptyList());
        rows=(List<List<Object>>)data("/db/readRows",r).get("rows");assertEquals("12.30",rows.get(0).get(1));
    }
    @SuppressWarnings("unchecked") @Test void summarizesBinaryRedactsCredentialsAndMarksTextTruncation(){
        Map<String,Object> r=request();r.put("table","customers");r.put("columns",Arrays.asList("password","payload","memo"));
        r.put("where",Arrays.asList(map("column","name","op","eq","value","O'Reilly")));
        Map<String,Object> d=data("/db/readRows",r);List<List<Object>> rows=(List<List<Object>>)d.get("rows");
        assertEquals("REDACTED",((Map<?,?>)rows.get(0).get(0)).get("kind"));
        assertEquals("BINARY",((Map<?,?>)rows.get(0).get(1)).get("kind"));
        assertEquals(Boolean.TRUE,((Map<?,?>)rows.get(0).get(2)).get("truncated"));
        assertFalse(d.toString().contains("fixture-hidden"));
    }
    @Test void rejectsSqlExpressionsCrossSchemaAndUnboundedRequestsWithoutChangingData(){
        for(Map<String,Object> change:Arrays.asList(
            map("table","customers; DELETE FROM customers"),map("table","other.private_rows"),
            map("columns",Arrays.asList("id,(SELECT 1)")),map("columns",Collections.emptyList()),
            map("limit",101),map("where",Arrays.asList(map("column","name","op","sql","value","1=1"))),
            map("orderBy",Arrays.asList(map("column","id","direction","DESC; DELETE FROM customers"))))) {
            Map<String,Object> r=request();r.put("table","customers");r.put("columns",Arrays.asList("id"));r.putAll(change);
            assertNotEquals(0,call("/db/readRows",r).get("code"),r.keySet().toString());
        }
        assertEquals(111,fixture.queryForObject("SELECT COUNT(*) FROM customers",Integer.class));
        Map<String,Object> r=request();r.put("table","customer_");assertNotEquals(0,call("/db/inspect",r).get("code"));
    }
    @SuppressWarnings("unchecked") @Test void supportsPaginationAndTypedPredicatesForRelatedRows(){
        Map<String,Object> r=request();r.put("limit",1);
        Map<String,Object> first=data("/db/inspect",r);assertEquals(1,first.get("nextOffset"));
        r.put("offset",first.get("nextOffset"));Map<String,Object> second=data("/db/inspect",r);
        assertNotEquals(first.get("tables"),second.get("tables"));
        r=request();r.put("table","orders");r.put("columns",Arrays.asList("id","customer_id"));
        r.put("where",Arrays.asList(map("column","customer_id","op","in","value",Arrays.asList("9007199254740993")),map("column","amount","op","gte","value","10.00")));
        Map<String,Object> d=data("/db/readRows",r);assertEquals(1,d.get("rowCount"));
        List<List<Object>> rows=(List<List<Object>>)d.get("rows");assertEquals("9007199254740993",rows.get(0).get(1));
        r.put("where",Arrays.asList(map("column","customer_id","op","is_null")));assertEquals(0,data("/db/readRows",r).get("rowCount"));
    }
    @Test void invalidConnectionAndCredentialProbesAreRejected(){
        Map<String,Object> r=map("table","customers");assertNotEquals(0,call("/db/inspect",r).get("code"));
        r=request();r.put("table","customers");r.put("columns",Arrays.asList("id"));
        r.put("where",Arrays.asList(map("column","password","op","eq","value","fixture-hidden")));
        Map<String,Object> result=call("/db/readRows",r);assertNotEquals(0,result.get("code"));
        assertFalse(result.toString().contains("fixture-hidden"));
    }
}
