package faker.data;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import faker.data.batch.GenerationBatchStore;
import faker.data.batch.TargetRowSnapshot;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenerationBatchTest {
    private static final String URL="jdbc:h2:mem:batch_journal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final Path DIR=directory();
    private final JdbcTemplate target=new JdbcTemplate(new DriverManagerDataSource(URL,"sa","fixture"));
    @Autowired TestRestTemplate http;
    @Autowired GenerationBatchStore store;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("faker.data-dir",DIR::toString);}
    static Path directory(){try{return Files.createTempDirectory("faker-batch-");}catch(Exception e){throw new RuntimeException(e);}}
    @BeforeEach void setup(){
        target.execute("DROP ALL OBJECTS");
        target.execute("CREATE TABLE sources(id BIGINT PRIMARY KEY,label VARCHAR(30))");target.execute("INSERT INTO sources VALUES(9007199254740993,'existing')");
        target.execute("CREATE TABLE headers(id BIGINT PRIMARY KEY, source_id BIGINT REFERENCES sources(id), note VARCHAR(20) DEFAULT 'database-default')");
        target.execute("CREATE TABLE details(tenant INT NOT NULL,id BIGINT NOT NULL, header_id BIGINT REFERENCES headers(id), PRIMARY KEY(tenant,id))");
        target.execute("CREATE TABLE failures(id BIGINT PRIMARY KEY CHECK(id<2))");
        target.execute("CREATE TABLE no_key(label VARCHAR(20))");
        target.execute("CREATE TABLE auto_key(id BIGINT AUTO_INCREMENT PRIMARY KEY,label VARCHAR(20))");
        target.execute("CREATE TABLE fixed_char_key(id CHAR(10) PRIMARY KEY)");
    }
    static Map<String,Object> m(Object...p){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<p.length;i+=2)r.put((String)p[i],p[i+1]);return r;}
    static Map<String,Object> col(String name,String type,Map<String,Object>...rules){return m("name",name,"javaType",type,"regs",Arrays.asList(rules));}
    static Map<String,Object> seq(String name){return col(name,"java.lang.Long",m("code","REG.INCREMENT_NUM","minSize",1,"step",1));}
    static Map<String,Object> fixed(String name,String type,String value){return col(name,type,m("code","RES.INPUTAREA","inputs",Arrays.asList(m("value",value,"weight",1))));}
    static Map<String,Object> table(String name,int size,Map<String,Object>...columns){return m("name",name,"size",size,"columns",Arrays.asList(columns));}
    @SuppressWarnings("unchecked") String generate(List<Map<String,Object>> tables,String expected) throws Exception {
        Map<String,Object> body=m("dataSource",m("type","MYSQL","url",URL,"username","sa","password","fixture"),"tables",tables);
        Map<String,Object> result=http.postForObject("/publish/task/GEN_DATA",body,Map.class);assertEquals(0,result.get("code"),result.toString());String task=(String)result.get("data");
        for(int i=0;i<200;i++) {
            Map<String,Object> r=http.getForObject("/asyncTask/queryProcess/"+task,Map.class);Map<String,Object> state=(Map<String,Object>)r.get("data");
            if(Arrays.asList("FINISHED","ERROR").contains(state.get("state"))){assertEquals(expected,state.get("state"),state.toString());return task;}
            Thread.sleep(25);
        }
        throw new AssertionError("Task timeout; do not repeat generation");
    }
    @SuppressWarnings("unchecked") Map<String,Object> batch(String id){Map<String,Object> response=http.getForObject("/batches/"+id,Map.class);assertEquals(0,response.get("code"),response.toString());return (Map<String,Object>)response.get("data");}
    @SuppressWarnings("unchecked") @Test void recordsActualCompositeKeysAndExcludesReadOnlySources() throws Exception {
        Map<String,Object> source=table("sources",0,col("id","java.lang.Long"));source.put("existingData",m("maxRows",10,"orderBy",Arrays.asList("id")));
        String id=generate(Arrays.asList(source,table("headers",2,seq("id"),col("source_id","java.lang.Long",m("code","REF.OTHER_TABLE","refOtherTableColumn",Arrays.asList("sources","id")))),
            table("details",2,fixed("tenant","java.lang.Integer","7"),seq("id"),col("header_id","java.lang.Long",m("code","REF.OTHER_TABLE","refOtherTableColumn",Arrays.asList("headers","id"))))),"FINISHED");
        Map<String,Object> report=batch(id);assertEquals(id,report.get("batchId"));assertEquals("COMMITTED",report.get("state"));assertEquals(true,report.get("trackable"));assertEquals(4,report.get("rowCount"));
        assertFalse(report.toString().contains("fixture"),report.toString());
        List<Map<String,Object>> tables=(List<Map<String,Object>>)report.get("tables");assertEquals(2,tables.size());assertFalse(tables.stream().anyMatch(t->"sources".equals(t.get("table"))));
        Map<String,Object> response=http.getForObject("/batches/"+id+"/rows?limit=3",Map.class);Map<String,Object> page=(Map<String,Object>)response.get("data");
        List<Map<String,Object>> rows=(List<Map<String,Object>>)page.get("rows");assertEquals(3,rows.size());assertEquals(true,page.get("truncated"));
        assertTrue(rows.stream().anyMatch(r->"details".equals(r.get("table")) && "7".equals(((Map<?,?>)r.get("primaryKey")).get("tenant"))));
        for(Map<String,Object> row:rows)assertEquals(64,((String)row.get("fingerprint")).length());
        assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM headers WHERE note='database-default'",Integer.class));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM sources",Integer.class));
    }
    @Test void rollbackNeverClaimsCommittedRows() throws Exception {
        String id=generate(Arrays.asList(table("headers",2,seq("id")),table("failures",3,seq("id"))),"ERROR");
        Map<String,Object> report=batch(id);assertEquals("ROLLED_BACK",report.get("state"));assertEquals(0,report.get("rowCount"));assertEquals(false,report.get("trackable"));
        assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM headers",Integer.class));
    }
    @SuppressWarnings("unchecked") @Test void marksOmittedAndMissingPrimaryKeysUntrackableWithoutGuessing() throws Exception {
        String id=generate(Arrays.asList(table("no_key",2,fixed("label","java.lang.String","demo")),table("auto_key",2,fixed("label","java.lang.String","auto"))),"FINISHED");
        Map<String,Object> report=batch(id);assertEquals("COMMITTED",report.get("state"));assertEquals(false,report.get("trackable"));assertEquals(0,report.get("rowCount"));assertEquals(4,report.get("untrackedRows"));
        List<Map<String,Object>> tables=(List<Map<String,Object>>)report.get("tables");assertTrue(tables.stream().anyMatch(t->"PRIMARY_KEY_NOT_SUPPLIED".equals(t.get("reason"))));assertTrue(tables.stream().anyMatch(t->"NO_PRIMARY_KEY".equals(t.get("reason"))));
        assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM auto_key",Integer.class));
    }
    @SuppressWarnings("unchecked") @Test void snapshotsFinalOwnedRowsAfterLaterTableTriggers() throws Exception {
        target.execute("CREATE TRIGGER touch_header AFTER INSERT ON details FOR EACH ROW CALL \"faker.data.GenerationBatchTest$TouchHeader\"");
        String id=generate(Arrays.asList(table("headers",1,seq("id")),table("details",1,fixed("tenant","java.lang.Integer","1"),seq("id"),fixed("header_id","java.lang.Long","1"))),"FINISHED");
        assertEquals("touched",target.queryForObject("SELECT note FROM headers WHERE id=1",String.class));
        List<Map<String,Object>> snapshots=(List<Map<String,Object>>)store.rows(id,0,100).get("rows");
        Map<String,Object> recorded=snapshots.stream().filter(r->"headers".equals(r.get("table"))).findFirst().get();
        try(Connection connection=target.getDataSource().getConnection()) {
            List<TargetRowSnapshot.Row> current=store.descriptor(id,"headers").read(connection,Arrays.asList(Collections.singletonMap("id","1")));
            assertEquals(current.get(0).fingerprint,recorded.get("fingerprint"));
        }
    }
    public static class TouchHeader implements org.h2.api.Trigger {
        public void init(Connection c,String schema,String trigger,String table,boolean before,int type){}
        public void fire(Connection c,Object[] before,Object[] after) throws SQLException {try(PreparedStatement statement=c.prepareStatement("UPDATE headers SET note='touched' WHERE id=?")){statement.setObject(1,after[2]);statement.executeUpdate();}}
        public void close(){}
        public void remove(){}
    }
    @Test void fixedCharPaddingDoesNotCauseGuessedOwnership() throws Exception {
        String id=generate(Arrays.asList(table("fixed_char_key",1,fixed("id","java.lang.String","abc"))),"FINISHED");
        Map<String,Object> report=batch(id);assertEquals(false,report.get("trackable"));assertEquals(1,report.get("untrackedRows"));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM fixed_char_key",Integer.class));
    }
}
