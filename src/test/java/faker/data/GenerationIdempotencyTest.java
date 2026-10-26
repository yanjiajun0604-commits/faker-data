package faker.data;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static faker.data.GenerationBatchTest.*;
import faker.data.service.generate.engine.GenerationEngine;
import faker.data.domain.GenerateVO;
import faker.data.cache.ReferenceCache;
import faker.data.task.*;
import faker.data.batch.GenerationBatchStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.lang.reflect.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenerationIdempotencyTest {
    private static final String URL="jdbc:h2:mem:idempotency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final Path DIR=GenerationBatchTest.directory();
    private final JdbcTemplate target=new JdbcTemplate(new DriverManagerDataSource(URL,"sa","fixture"));
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate local;
    @Autowired GenerationEngine engine;
    @Autowired GenerationBatchStore batches;
    @Autowired ReferenceCache cache;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("faker.data-dir",DIR::toString);}
    @BeforeEach void setup(){target.execute("DROP ALL OBJECTS");target.execute("CREATE TABLE request_rows(id VARCHAR(36) PRIMARY KEY,label VARCHAR(20))");}
    Map<String,Object> request(String key){return m("requestId",key,"dataSource",m("type","MYSQL","url",URL,"username","sa","password","fixture"),"tables",Arrays.asList(table("request_rows",2,col("id","java.lang.String",m("code","FIX.UUID")),fixed("label","java.lang.String","demo"))));}
    Map post(Map<String,Object> request){return http.postForObject("/publish/task/GEN_DATA",request,Map.class);}
    @SuppressWarnings("unchecked") void done(String task) throws Exception {
        for(int i=0;i<200;i++){Map<String,Object> r=http.getForObject("/asyncTask/queryProcess/"+task,Map.class);Map<String,Object> state=(Map<String,Object>)r.get("data");if(Arrays.asList("FINISHED","ERROR").contains(state.get("state"))){assertEquals("FINISHED",state.get("state"),state.toString());return;}Thread.sleep(25);}fail("Unknown task outcome; do not submit again");
    }
    @Test void sameRequestReturnsOriginalTaskAndChangedPayloadConflicts() throws Exception {
        Map<String,Object> request=request("idem-"+UUID.randomUUID());Map first=post(request);assertEquals(0,first.get("code"));String id=(String)first.get("data");
        Map repeated=post(new TreeMap<>(request));assertEquals(id,repeated.get("data"));done(id);assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM request_rows",Integer.class));
        Map<String,Object> changed=request((String)request.get("requestId"));changed.put("tables",Arrays.asList(table("request_rows",3,col("id","java.lang.String",m("code","FIX.UUID")))));
        Map conflict=post(changed);assertNotEquals(0,conflict.get("code"));assertTrue(conflict.get("msg").toString().contains("IDEMPOTENCY_CONFLICT"));assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM request_rows",Integer.class));
    }
    @Test void concurrentDuplicateClaimsEnqueueOnlyOneTask() throws Exception {
        Map<String,Object> request=request("concurrent-"+UUID.randomUUID());ExecutorService workers=Executors.newFixedThreadPool(2);
        try {Future<Map> one=workers.submit(()->post(request)),two=workers.submit(()->post(request));Map a=one.get(10,TimeUnit.SECONDS),b=two.get(10,TimeUnit.SECONDS);assertEquals(0,a.get("code"),a.toString());assertEquals(a.get("data"),b.get("data"));done((String)a.get("data"));assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM request_rows",Integer.class));}finally{workers.shutdownNow();}
    }
    @SuppressWarnings("unchecked") @Test void rotatedPasswordDoesNotCreateANewRequestOrPersistTheSecret() throws Exception {
        Map<String,Object> request=request("secret-"+UUID.randomUUID());String id=(String)post(request).get("data");done(id);
        ((Map<String,Object>)request.get("dataSource")).put("password","rotated-unused-password");assertEquals(id,post(request).get("data"));
        Map<String,Object> journal=local.queryForMap("SELECT * FROM faker_generation_request WHERE request_id=?",request.get("requestId"));assertFalse(journal.toString().contains("fixture"));assertFalse(journal.toString().contains("rotated-unused-password"));
    }
    @SuppressWarnings("unchecked") @Test void unknownCompleteMatchingSnapshotNeedsExplicitConfirmation() throws Exception {
        String id=(String)post(request("recover-"+UUID.randomUUID())).get("data");done(id);
        local.update("UPDATE faker_generation_batch SET state='UNKNOWN' WHERE batch_id=?",id);
        Map<String,Object> body=m("dataSource",m("type","MYSQL","url",URL,"username","sa","password","fixture"));
        Map response=http.postForObject("/batches/"+id+"/reconcile",body,Map.class);assertEquals(0,response.get("code"),response.toString());Map<String,Object> report=(Map<String,Object>)response.get("data");
        assertEquals("ALL_MATCHING",report.get("observation"));assertEquals(false,report.get("recovered"));assertEquals("UNKNOWN",local.queryForObject("SELECT state FROM faker_generation_batch WHERE batch_id=?",String.class,id));
        body.put("confirmCommit",true);response=http.postForObject("/batches/"+id+"/reconcile",body,Map.class);report=(Map<String,Object>)response.get("data");assertEquals(true,report.get("recovered"));assertEquals("COMMITTED",local.queryForObject("SELECT state FROM faker_generation_batch WHERE batch_id=?",String.class,id));assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM request_rows",Integer.class));
    }
    @SuppressWarnings("unchecked") @Test void missingOrModifiedRowsRemainUnknownAndAreNeverReplayed() throws Exception {
        Map<String,Object> request=request("unproven-"+UUID.randomUUID());String id=(String)post(request).get("data");done(id);local.update("UPDATE faker_generation_batch SET state='UNKNOWN' WHERE batch_id=?",id);target.update("UPDATE request_rows SET label='changed'");
        Map response=http.postForObject("/batches/"+id+"/reconcile",m("dataSource",request.get("dataSource"),"confirmCommit",true),Map.class);assertEquals(0,response.get("code"));Map<String,Object> report=(Map<String,Object>)response.get("data");assertEquals(false,report.get("recovered"));assertEquals("CHANGED_OR_MISSING",report.get("observation"));
        target.update("DELETE FROM request_rows");response=http.postForObject("/batches/"+id+"/reconcile",m("dataSource",request.get("dataSource"),"confirmCommit",true),Map.class);assertEquals("MISSING_ALL",((Map<?,?>)response.get("data")).get("observation"));assertEquals(id,post(request).get("data"));assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM request_rows",Integer.class));assertEquals("UNKNOWN",local.queryForObject("SELECT state FROM faker_generation_batch WHERE batch_id=?",String.class,id));
    }
    @SuppressWarnings("unchecked") @Test void wrongDatabaseSchemaChangesAndPartialSnapshotsCannotBeConfirmed() throws Exception {
        Map<String,Object> request=request("scope-"+UUID.randomUUID());String id=(String)post(request).get("data");done(id);local.update("UPDATE faker_generation_batch SET state='UNKNOWN' WHERE batch_id=?",id);
        Map<String,Object> wrong=m("type","MYSQL","url",URL.replace("mem:idempotency","mem:wrong_idempotency"),"username","sa","password","fixture");
        Map response=http.postForObject("/batches/"+id+"/reconcile",m("dataSource",wrong,"confirmCommit",true),Map.class);assertNotEquals(0,response.get("code"));assertTrue(response.get("msg").toString().contains("IDENTITY_MISMATCH"));
        target.execute("ALTER TABLE request_rows ADD COLUMN extra VARCHAR(5)");response=http.postForObject("/batches/"+id+"/reconcile",m("dataSource",request.get("dataSource"),"confirmCommit",true),Map.class);assertNotEquals(0,response.get("code"));assertTrue(response.get("msg").toString().contains("SCHEMA_CHANGED"));
        local.update("UPDATE faker_generation_batch SET prepared_at=NULL WHERE batch_id=?",id);response=http.postForObject("/batches/"+id+"/reconcile",m("dataSource",request.get("dataSource"),"confirmCommit",true),Map.class);assertEquals("INCOMPLETE_OR_UNTRACKED_SNAPSHOT",((Map<?,?>)response.get("data")).get("observation"));assertEquals("UNKNOWN",local.queryForObject("SELECT state FROM faker_generation_batch WHERE batch_id=?",String.class,id));
    }
    @Test void lostCommitAcknowledgementIsUnknownEvenThoughTargetCommitted() throws Exception {
        DriverManagerDataSource normal=new DriverManagerDataSource(URL,"sa","fixture");
        javax.sql.DataSource fault=new org.springframework.jdbc.datasource.AbstractDataSource(){
            public Connection getConnection() throws SQLException {
                Connection real=normal.getConnection();
                return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
                    try {Object result=method.invoke(real,args);if(method.getName().equals("commit"))throw new SQLException("simulated lost commit acknowledgement");return result;}catch(InvocationTargetException e){throw e.getCause();}
                });
            }
            public Connection getConnection(String user,String password) throws SQLException{return getConnection();}
        };
        Map<String,Object> body=request("fault-"+UUID.randomUUID());body.remove("requestId");
        GenerateVO configured=new ObjectMapper().convertValue(body,GenerateVO.class);
        AsyncTaskExecutor executor=mock(AsyncTaskExecutor.class);String id=UUID.randomUUID().toString();when(executor.getTaskId()).thenReturn(id);
        try {assertThrows(RuntimeException.class,()->engine.generate(fault,configured.getTables(),mock(AsyncTaskMonitor.class),executor));}finally{cache.removeTask(id);}
        assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM request_rows",Integer.class));assertEquals("UNKNOWN",batches.get(id).get("state"));assertNotNull(batches.get(id).get("preparedAt"));
    }
}
