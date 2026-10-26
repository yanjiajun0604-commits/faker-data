package faker.data;

import java.nio.file.*;
import java.util.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import faker.data.batch.GenerationBatchStore;
import faker.data.batch.CleanupPlanStore;
import faker.data.batch.GenerationBatchCleanup;
import faker.data.domain.BatchCleanupRequest;
import faker.data.domain.DataSource;
import java.lang.reflect.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static faker.data.GenerationBatchTest.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenerationCleanupTest {
    private static final Path DIR=GenerationBatchTest.directory();
    @Autowired TestRestTemplate http;
    @Autowired GenerationBatchStore store;
    @Autowired JdbcTemplate local;
    @Autowired CleanupPlanStore plans;
    private String url;private JdbcTemplate target;private Map<String,Object> source;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("faker.data-dir",DIR::toString);}
    @BeforeEach void setup(){
        url="jdbc:h2:mem:cleanup_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        target=new JdbcTemplate(new DriverManagerDataSource(url,"sa","fixture"));source=m("type","MYSQL","url",url,"username","sa","password","fixture");
        target.execute("CREATE TABLE cleanup_parents(id VARCHAR(36) PRIMARY KEY,label VARCHAR(20))");
        target.execute("CREATE TABLE cleanup_children(id VARCHAR(36) PRIMARY KEY,parent_id VARCHAR(36), FOREIGN KEY(parent_id) REFERENCES cleanup_parents(id) ON DELETE CASCADE)");
        target.execute("CREATE TABLE cleanup_external(id VARCHAR(36) PRIMARY KEY,parent_id VARCHAR(36), FOREIGN KEY(parent_id) REFERENCES cleanup_parents(id) ON DELETE CASCADE)");
        target.execute("INSERT INTO cleanup_parents VALUES('original-parent','keep')");target.execute("INSERT INTO cleanup_children VALUES('original-child','original-parent')");
    }
    @SuppressWarnings("unchecked") String generate(List<Map<String,Object>> tables) throws Exception {
        Map result=http.postForObject("/publish/task/GEN_DATA",m("dataSource",source,"tables",tables),Map.class);assertEquals(0,result.get("code"),result.toString());String id=(String)result.get("data");
        for(int i=0;i<200;i++){Map r=http.getForObject("/asyncTask/queryProcess/"+id,Map.class);Map state=(Map)r.get("data");if(Arrays.asList("FINISHED","ERROR").contains(state.get("state"))){assertEquals("FINISHED",state.get("state"),state.toString());return id;}Thread.sleep(25);}throw new AssertionError("Unknown outcome");
    }
    List<Map<String,Object>> recipe(){
        Map<String,Object> child=table("cleanup_children",0,col("id","java.lang.String",m("code","FIX.UUID")),col("parent_id","java.lang.String",m("code","REF.OTHER_TABLE","refOtherTableColumn",Arrays.asList("cleanup_parents","id"))));child.put("parentRows",m("table","cleanup_parents","rowsPerParent",2));
        return Arrays.asList(table("cleanup_parents",2,col("id","java.lang.String",m("code","FIX.UUID")),fixed("label","java.lang.String","generated")),child);
    }
    @SuppressWarnings("unchecked") Map<String,Object> preview(String id){Map result=http.postForObject("/batches/"+id+"/cleanup/preview",m("dataSource",source),Map.class);assertEquals(0,result.get("code"),result.toString());return (Map<String,Object>)result.get("data");}
    Map execute(String id,Map<String,Object> plan,boolean confirm){return http.postForObject("/batches/"+id+"/cleanup/execute",m("dataSource",source,"planId",plan.get("planId"),"planHash",plan.get("planHash"),"confirmDelete",confirm),Map.class);}
    @SuppressWarnings("unchecked") void blocker(Map<String,Object> plan,String code){assertEquals(false,plan.get("eligible"),plan.toString());assertTrue(((List<Map<String,Object>>)plan.get("blockers")).stream().anyMatch(b->code.equals(b.get("code"))),plan.toString());}
    @SuppressWarnings("unchecked") String parentKey(String id){return ((Map<String,String>)((List<Map<String,Object>>)store.rows(id,0,100).get("rows")).stream().filter(r->"cleanup_parents".equals(r.get("table"))).findFirst().get().get("primaryKey")).get("id");}
    @Test void previewAndIdempotentCleanupProtectOriginalRows() throws Exception {
        String id=generate(recipe());Map<String,Object> plan=preview(id);assertEquals(true,plan.get("eligible"),plan.toString());assertEquals(6,plan.get("rowCount"));assertEquals(3,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));
        assertNotEquals(0,execute(id,plan,false).get("code"));Map result=execute(id,plan,true);assertEquals(0,result.get("code"),result.toString());Map report=(Map)result.get("data");assertEquals("DONE",report.get("state"));assertEquals(6,report.get("deletedRows"));assertEquals(true,report.get("verifiedAbsent"));
        assertEquals("CLEANED",store.get(id).get("state"));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM cleanup_children",Integer.class));assertEquals("keep",target.queryForObject("SELECT label FROM cleanup_parents WHERE id='original-parent'",String.class));assertEquals("DONE",((Map)execute(id,plan,true).get("data")).get("state"));
    }
    @Test void changedRowsInvalidatePlanWithoutDeletingAnything() throws Exception {
        String id=generate(recipe());Map<String,Object> plan=preview(id);target.update("UPDATE cleanup_parents SET label='changed' WHERE id=?",parentKey(id));Map result=execute(id,plan,true);assertNotEquals(0,result.get("code"));assertEquals(3,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));assertEquals(5,target.queryForObject("SELECT COUNT(*) FROM cleanup_children",Integer.class));assertEquals("COMMITTED",store.get(id).get("state"));blocker(preview(id),"ROW_CHANGED_OR_MISSING");
    }
    @Test void externalCascadeReferenceBlocksPreviewAndStaleExecution() throws Exception {
        String id=generate(recipe());Map<String,Object> plan=preview(id);target.update("INSERT INTO cleanup_external VALUES('protected-reference',?)",parentKey(id));blocker(preview(id),"EXTERNAL_REFERENCE");assertNotEquals(0,execute(id,plan,true).get("code"));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM cleanup_external",Integer.class));assertEquals(3,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));assertEquals(5,target.queryForObject("SELECT COUNT(*) FROM cleanup_children",Integer.class));
    }
    @Test void hashExpiryAndWrongTargetAreRejected() throws Exception {
        String id=generate(recipe());Map<String,Object> plan=preview(id);Map<String,Object> wrong=new LinkedHashMap<>(plan);wrong.put("planHash","wrong");assertNotEquals(0,execute(id,wrong,true).get("code"));
        Map otherSource=m("type","MYSQL","url",url.replace("mem:cleanup_","mem:other_cleanup_"),"username","sa","password","fixture");Map response=http.postForObject("/batches/"+id+"/cleanup/execute",m("dataSource",otherSource,"planId",plan.get("planId"),"planHash",plan.get("planHash"),"confirmDelete",true),Map.class);assertNotEquals(0,response.get("code"));
        plan=preview(id);local.update("UPDATE faker_cleanup_plan SET expires_at=0 WHERE plan_id=?",plan.get("planId"));assertNotEquals(0,execute(id,plan,true).get("code"));assertEquals(3,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));
    }
    @Test void unknownDeleteTriggerSideEffectsAreNotExecuted() throws Exception {
        String id=generate(recipe());DeleteTrigger.calls.set(0);target.execute("CREATE TRIGGER guarded_delete BEFORE DELETE ON cleanup_parents FOR EACH ROW CALL \"faker.data.GenerationCleanupTest$DeleteTrigger\"");blocker(preview(id),"DELETE_TRIGGER_PRESENT");assertEquals(0,DeleteTrigger.calls.get());assertEquals(3,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));
    }
    @Test void identicallyRecreatedKeysOwnedByTwoLiveBatchesAreNotAdopted() throws Exception {
        List<Map<String,Object>> tables=Arrays.asList(table("cleanup_parents",1,fixed("id","java.lang.String","reused-key"),fixed("label","java.lang.String","same")));
        String first=generate(tables);target.update("DELETE FROM cleanup_parents WHERE id='reused-key'");String second=generate(tables);blocker(preview(first),"SHARED_BATCH_OWNERSHIP");blocker(preview(second),"SHARED_BATCH_OWNERSHIP");assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));
    }
    @Test void compositeKeysAndReadOnlyCandidateSourcesRemainCorrect() throws Exception {
        target.execute("CREATE TABLE cleanup_sources(id VARCHAR(20) PRIMARY KEY)");target.execute("INSERT INTO cleanup_sources VALUES('existing-source')");target.execute("ALTER TABLE cleanup_parents ADD COLUMN source_id VARCHAR(20) REFERENCES cleanup_sources(id)");
        Map<String,Object> existing=table("cleanup_sources",0,col("id","java.lang.String"));existing.put("existingData",m("maxRows",10));
        List<Map<String,Object>> tables=recipe();List<Map<String,Object>> parentColumns=new ArrayList<>((List<Map<String,Object>>)tables.get(0).get("columns"));parentColumns.add(col("source_id","java.lang.String",m("code","REF.OTHER_TABLE","refOtherTableColumn",Arrays.asList("cleanup_sources","id"))));tables.get(0).put("columns",parentColumns);
        String id=generate(Arrays.asList(existing,tables.get(0),tables.get(1)));Map<String,Object> plan=preview(id);assertEquals(true,plan.get("eligible"),plan.toString());assertEquals(0,execute(id,plan,true).get("code"));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM cleanup_sources",Integer.class));
        target.execute("CREATE TABLE cleanup_composite(tenant INT,id BIGINT,PRIMARY KEY(tenant,id))");target.execute("CREATE TABLE cleanup_composite_child(tenant INT,id BIGINT,parent_tenant INT,parent_id BIGINT,PRIMARY KEY(tenant,id),FOREIGN KEY(parent_tenant,parent_id) REFERENCES cleanup_composite(tenant,id))");
        id=generate(Arrays.asList(table("cleanup_composite",2,fixed("tenant","java.lang.Integer","7"),seq("id")),table("cleanup_composite_child",2,fixed("tenant","java.lang.Integer","9"),seq("id"),fixed("parent_tenant","java.lang.Integer","7"),col("parent_id","java.lang.Long",m("code","REF.OTHER_TABLE","refOtherTableColumn",Arrays.asList("cleanup_composite","id"))))));plan=preview(id);assertEquals(true,plan.get("eligible"),plan.toString());assertEquals(0,execute(id,plan,true).get("code"));assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM cleanup_composite",Integer.class));assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM cleanup_composite_child",Integer.class));
    }
    @Test void selfReferenceCyclesAndUntrackedKeysBlockDeletion() throws Exception {
        target.execute("CREATE TABLE cleanup_nodes(id BIGINT PRIMARY KEY,parent_id BIGINT REFERENCES cleanup_nodes(id))");
        Map<String,Object> condition=m("code","COND.EXPR","condition","#__rowIndex == 0","onTrue",Collections.emptyList(),"onFalse",Arrays.asList(m("code","RES.INPUTAREA","inputs",Arrays.asList(m("value","1","weight",1)))));
        String id=generate(Arrays.asList(table("cleanup_nodes",2,seq("id"),col("parent_id","java.lang.Long",condition))));blocker(preview(id),"CYCLIC_OWNERSHIP_GRAPH");assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM cleanup_nodes",Integer.class));
        target.execute("CREATE TABLE cleanup_untracked(label VARCHAR(20))");id=generate(Arrays.asList(table("cleanup_untracked",2,fixed("label","java.lang.String","demo"))));blocker(preview(id),"UNTRACKED_WRITES");assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM cleanup_untracked",Integer.class));
    }
    BatchCleanupRequest cleanupRequest(Map<String,Object> plan){DataSource data=new DataSource();data.setType(faker.data.domain.JdbcType.MYSQL);data.setUrl(url);data.setUsername("sa");data.setPassword("fixture");BatchCleanupRequest request=new BatchCleanupRequest();request.setDataSource(data);request.setPlanId((String)plan.get("planId"));request.setPlanHash((String)plan.get("planHash"));request.setConfirmDelete(true);return request;}
    @Test void deleteFailureAfterChildWritesRollsTheWholeTransactionBack() throws Exception {
        String id=generate(recipe());Map<String,Object> plan=preview(id);GenerationBatchCleanup fault=new GenerationBatchCleanup(store,plans){
            @Override protected Connection open(BatchCleanupRequest ignored) throws SQLException {
                Connection real=target.getDataSource().getConnection();return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
                    if(method.getName().equals("prepareStatement") && ((String)args[0]).startsWith("DELETE") && ((String)args[0]).contains("cleanup_parents"))throw new SQLException("simulated later delete failure");
                    try{return method.invoke(real,args);}catch(InvocationTargetException e){throw e.getCause();}
                });
            }
        };
        assertThrows(RuntimeException.class,()->fault.execute(id,cleanupRequest(plan)));assertEquals("COMMITTED",store.get(id).get("state"));assertEquals(3,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));assertEquals(5,target.queryForObject("SELECT COUNT(*) FROM cleanup_children",Integer.class));assertEquals("INVALIDATED",plans.get(id,(String)plan.get("planId")).state);
    }
    @Test void lostDeleteCommitAcknowledgementNeverRepeatsDeletion() throws Exception {
        String id=generate(recipe());Map<String,Object> plan=preview(id);AtomicInteger opened=new AtomicInteger();GenerationBatchCleanup fault=new GenerationBatchCleanup(store,plans){
            @Override protected Connection open(BatchCleanupRequest ignored) throws SQLException {
                opened.incrementAndGet();Connection real=target.getDataSource().getConnection();return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
                    try{Object result=method.invoke(real,args);if(method.getName().equals("commit"))throw new SQLException("simulated lost delete commit receipt");return result;}catch(InvocationTargetException e){throw e.getCause();}
                });
            }
        };
        assertThrows(RuntimeException.class,()->fault.execute(id,cleanupRequest(plan)));assertEquals("CLEANUP_UNKNOWN",store.get(id).get("state"));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM cleanup_children",Integer.class));assertEquals("UNKNOWN",fault.execute(id,cleanupRequest(plan)).get("state"));assertEquals(1,opened.get());
    }
    @Test void failedIndependentPostcheckIsUnknownRatherThanARepeatableFailure() throws Exception {
        String id=generate(recipe());Map<String,Object> plan=preview(id);AtomicInteger opened=new AtomicInteger();GenerationBatchCleanup fault=new GenerationBatchCleanup(store,plans){
            @Override protected Connection open(BatchCleanupRequest ignored) throws SQLException {if(opened.incrementAndGet()==2)throw new SQLException("simulated postcheck connection failure");return target.getDataSource().getConnection();}
        };
        assertThrows(RuntimeException.class,()->fault.execute(id,cleanupRequest(plan)));assertEquals("CLEANUP_UNKNOWN",store.get(id).get("state"));assertEquals("UNKNOWN",fault.execute(id,cleanupRequest(plan)).get("state"));assertEquals(2,opened.get());assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));
    }
    @Test void parallelHeavyPreviewsAreBoundedInsteadOfAccumulatingInMemory() throws Exception {
        String id=generate(recipe());Map<String,Object> plan=preview(id);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        GenerationBatchCleanup blocked=new GenerationBatchCleanup(store,plans){
            @Override protected Connection open(BatchCleanupRequest ignored) throws SQLException {entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new SQLException("fixture timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new SQLException(e);}return target.getDataSource().getConnection();}
        };
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try{Future<Map<String,Object>> first=worker.submit(()->blocked.preview(id,cleanupRequest(plan)));assertTrue(entered.await(2,TimeUnit.SECONDS));IllegalStateException busy=assertThrows(IllegalStateException.class,()->blocked.preview(id,cleanupRequest(plan)));assertTrue(busy.getMessage().contains("CLEANUP_BUSY"));release.countDown();assertEquals(true,first.get(5,TimeUnit.SECONDS).get("eligible"));assertEquals(3,target.queryForObject("SELECT COUNT(*) FROM cleanup_parents",Integer.class));}finally{release.countDown();worker.shutdownNow();}
    }
    @Test void oversizedEncodedKeysBlockInsteadOfBeingTruncated() throws Exception {
        target.execute("CREATE TABLE cleanup_large_key(id VARCHAR(10000) PRIMARY KEY)");char[] text=new char[9000];Arrays.fill(text,'x');
        String id=generate(Arrays.asList(table("cleanup_large_key",1,fixed("id","java.lang.String",new String(text)))));blocker(preview(id),"CLEANUP_KEY_BUDGET");assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM cleanup_large_key",Integer.class));
    }
    public static class DeleteTrigger implements org.h2.api.Trigger {
        static final AtomicInteger calls=new AtomicInteger();
        public void init(Connection c,String schema,String trigger,String table,boolean before,int type){}
        public void fire(Connection c,Object[] before,Object[] after){calls.incrementAndGet();}
        public void close(){}
        public void remove(){}
    }
}
