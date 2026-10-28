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
import static faker.data.GenerationBatchTest.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class DerivedOwnershipTest {
    private static final Path DIR=GenerationBatchTest.directory();
    @Autowired TestRestTemplate http;
    @Autowired GenerationBatchStore store;
    private JdbcTemplate target;private Map<String,Object> source;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("faker.data-dir",DIR::toString);}
    @BeforeEach void setup(){
        String url="jdbc:h2:mem:derived_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";source=m("type","MYSQL","url",url,"username","sa","password","fixture");target=new JdbcTemplate(new DriverManagerDataSource(url,"sa","fixture"));
        target.execute("CREATE TABLE ownership_bills(id VARCHAR(36) PRIMARY KEY,state VARCHAR(20),label VARCHAR(20))");target.execute("INSERT INTO ownership_bills VALUES('protected-bill','OLD','keep')");
        target.execute("CREATE TABLE ownership_instances(id BIGINT PRIMARY KEY,bill_id VARCHAR(36),state VARCHAR(20),other_bill_id VARCHAR(36) REFERENCES ownership_bills(id))");target.execute("CREATE INDEX idx_instance_bill ON ownership_instances(bill_id)");target.execute("INSERT INTO ownership_instances VALUES(100,'protected-bill','OLD',NULL)");
        target.execute("CREATE TABLE ownership_tasks(id BIGINT PRIMARY KEY,instance_id BIGINT,kind VARCHAR(20))");target.execute("CREATE INDEX idx_task_instance ON ownership_tasks(instance_id)");target.execute("INSERT INTO ownership_tasks VALUES(1000,100,'fixture')");
        target.execute("CREATE TABLE ownership_audits(id VARCHAR(36) PRIMARY KEY,bill_id VARCHAR(36),label VARCHAR(20))");target.execute("CREATE INDEX idx_audit_bill ON ownership_audits(bill_id)");target.execute("INSERT INTO ownership_audits VALUES('protected-audit','protected-bill','keep')");
    }
    Map<String,Object> policy(){return m("mutableColumns",Arrays.asList("state"),"derivedRows",Arrays.asList(m("table","ownership_instances","join",m("bill_id","id"),"derivedRows",Arrays.asList(m("table","ownership_tasks","join",m("instance_id","id"),"where",m("kind","fixture"))))));}
    Map<String,Object> recipe(Map<String,Object> ownership){Map<String,Object> root=table("ownership_bills",1,col("id","java.lang.String",m("code","FIX.UUID")),fixed("state","java.lang.String","DRAFT"),fixed("label","java.lang.String","generated"));root.put("ownership",ownership);return root;}
    @SuppressWarnings("unchecked") String generate(Map<String,Object> root,String expected) throws Exception {
        Map response=http.postForObject("/publish/task/GEN_DATA",m("dataSource",source,"tables",Arrays.asList(root)),Map.class);assertEquals(0,response.get("code"),response.toString());String task=(String)response.get("data");
        for(int i=0;i<200;i++){Map r=http.getForObject("/asyncTask/queryProcess/"+task,Map.class);Map state=(Map)r.get("data");if(Arrays.asList("FINISHED","ERROR").contains(state.get("state"))){assertEquals(expected,state.get("state"),state.toString());return task;}Thread.sleep(25);}throw new AssertionError("Unknown task result");
    }
    String bill(String task){return target.queryForObject("SELECT id FROM ownership_bills WHERE label='generated'",String.class);}
    @SuppressWarnings("unchecked") Map<String,Object> preview(String task){Map response=http.postForObject("/batches/"+task+"/cleanup/preview",m("dataSource",source),Map.class);assertEquals(0,response.get("code"),response.toString());return (Map<String,Object>)response.get("data");}
    Map execute(String task,Map<String,Object> plan){return http.postForObject("/batches/"+task+"/cleanup/execute",m("dataSource",source,"planId",plan.get("planId"),"planHash",plan.get("planHash"),"confirmDelete",true),Map.class);}
    @SuppressWarnings("unchecked") void blocked(Map<String,Object> plan,String code){assertEquals(false,plan.get("eligible"),plan.toString());assertTrue(((List<Map<String,Object>>)plan.get("blockers")).stream().anyMatch(b->code.equals(b.get("code"))),plan.toString());}
    @SuppressWarnings("unchecked") @Test void declaredStatusChangesAndNestedNewRowsCleanWithoutAdoptingOldData() throws Exception {
        String task=generate(recipe(policy()),"FINISHED"),bill=bill(task);target.update("UPDATE ownership_bills SET state='SUBMITTED' WHERE id=?",bill);target.update("INSERT INTO ownership_instances VALUES(101,?,'RUNNING',NULL)",bill);target.update("INSERT INTO ownership_tasks VALUES(1001,101,'fixture')");target.update("INSERT INTO ownership_instances VALUES(102,'unrelated-later','OTHER',NULL)");
        Map<String,Object> plan=preview(task);assertEquals(true,plan.get("eligible"),plan.toString());assertEquals(3,plan.get("rowCount"));assertEquals(2,plan.get("derivedRowCount"));Map result=execute(task,plan);assertEquals(0,result.get("code"),result.toString());assertEquals(true,((Map)result.get("data")).get("verifiedAbsent"));
        assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM ownership_bills",Integer.class));assertEquals("keep",target.queryForObject("SELECT label FROM ownership_bills WHERE id='protected-bill'",String.class));assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM ownership_instances",Integer.class));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM ownership_tasks",Integer.class));assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM ownership_audits",Integer.class));
        Map response=http.getForObject("/batches/"+task+"/cleanup/plans/"+plan.get("planId")+"/rows?limit=100",Map.class);List<Map<String,Object>> rows=(List<Map<String,Object>>)((Map)response.get("data")).get("rows");assertEquals(3,rows.size());assertEquals(2,rows.stream().filter(r->"DERIVED".equals(r.get("origin"))).count());
    }
    @Test void immutableChangesBlockAndMutableChangesAfterPreviewInvalidate() throws Exception {
        String task=generate(recipe(policy()),"FINISHED"),bill=bill(task);target.update("UPDATE ownership_bills SET state='SUBMITTED' WHERE id=?",bill);Map<String,Object> plan=preview(task);assertEquals(true,plan.get("eligible"),plan.toString());target.update("UPDATE ownership_bills SET state='APPROVED' WHERE id=?",bill);assertNotEquals(0,execute(task,plan).get("code"));assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM ownership_bills",Integer.class));target.update("UPDATE ownership_bills SET label='someone-else' WHERE id=?",bill);blocked(preview(task),"ROW_CHANGED_OR_MISSING");
    }
    @Test void keysCannotBeDeclaredMutableAndOldWeakReferencesPreventGeneration() throws Exception {
        generate(recipe(m("mutableColumns",Arrays.asList("id"))),"ERROR");assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM ownership_bills",Integer.class));
        Map<String,Object> root=recipe(policy());root.put("columns",Arrays.asList(fixed("id","java.lang.String","reserved-bill"),fixed("state","java.lang.String","DRAFT"),fixed("label","java.lang.String","generated")));target.update("INSERT INTO ownership_instances VALUES(101,'reserved-bill','OLD',NULL)");generate(root,"ERROR");assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM ownership_bills",Integer.class));assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM ownership_instances",Integer.class));
    }
    @Test void fullKeyBaselineRejectsPreexistingRowsMovedToThisBatch() throws Exception {
        Map<String,Object> ownership=m("derivedRows",Arrays.asList(m("table","ownership_audits","join",m("bill_id","id"))));String task=generate(recipe(ownership),"FINISHED"),bill=bill(task);target.update("INSERT INTO ownership_audits VALUES('new-audit',?,'new')",bill);Map<String,Object> plan=preview(task);assertEquals(true,plan.get("eligible"),plan.toString());assertEquals(2,plan.get("rowCount"));target.update("UPDATE ownership_audits SET bill_id=? WHERE id='protected-audit'",bill);blocked(preview(task),"PREEXISTING_DERIVED_REFERENCE");assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM ownership_audits",Integer.class));
    }
    @Test void sharedReferencesAndLowerThanWatermarkIdsAreNotProvenOwned() throws Exception {
        String task=generate(recipe(policy()),"FINISHED"),bill=bill(task);target.update("INSERT INTO ownership_instances VALUES(101,?,'RUNNING','protected-bill')",bill);blocked(preview(task),"SHARED_DERIVED_REFERENCE");target.update("DELETE FROM ownership_instances WHERE id=101");target.update("INSERT INTO ownership_instances VALUES(99,?,'RUNNING',NULL)",bill);blocked(preview(task),"PREEXISTING_DERIVED_REFERENCE");assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM ownership_bills",Integer.class));
    }
    @Test void missingJoinIndexAndBaselineOverflowFailBeforeWrites() throws Exception {
        target.execute("DROP INDEX idx_instance_bill");generate(recipe(policy()),"ERROR");assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM ownership_bills",Integer.class));
        Map<String,Object> ownership=m("derivedRows",Arrays.asList(m("table","ownership_audits","join",m("bill_id","id"),"baselineLimit",1)));target.update("INSERT INTO ownership_audits VALUES('another-old','other-bill','keep')");generate(recipe(ownership),"ERROR");assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM ownership_bills",Integer.class));
    }
    @Test void newDescendantsAndChangedDescendantsInvalidatePlanAndPagesStayBounded() throws Exception {
        String task=generate(recipe(policy()),"FINISHED"),bill=bill(task);target.update("INSERT INTO ownership_instances VALUES(101,?,'RUNNING',NULL)",bill);
        Map<String,Object> first=preview(task);target.update("INSERT INTO ownership_tasks VALUES(1001,101,'fixture')");assertNotEquals(0,execute(task,first).get("code"));assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM ownership_bills",Integer.class));
        Map<String,Object> next=preview(task);target.update("UPDATE ownership_instances SET state='changed' WHERE id=101");assertNotEquals(0,execute(task,next).get("code"));
        Map<String,Object> finalPlan=preview(task);Map page=http.getForObject("/batches/"+task+"/cleanup/plans/"+finalPlan.get("planId")+"/rows?limit=1",Map.class);assertEquals(true,((Map)page.get("data")).get("truncated"));assertEquals(1,((List)((Map)page.get("data")).get("rows")).size());
        Map invalid=http.getForObject("/batches/"+task+"/cleanup/plans/"+finalPlan.get("planId")+"/rows?limit=201",Map.class);assertNotEquals(0,invalid.get("code"));assertEquals(0,execute(task,finalPlan).get("code"));
    }
    @Test void deleteTriggersAndUndeclaredChildrenStillBlockDerivedCleanup() throws Exception {
        String task=generate(recipe(policy()),"FINISHED"),bill=bill(task);target.update("INSERT INTO ownership_instances VALUES(101,?,'RUNNING',NULL)",bill);
        target.execute("CREATE TABLE external_history(id BIGINT PRIMARY KEY,instance_id BIGINT REFERENCES ownership_instances(id))");target.execute("INSERT INTO external_history VALUES(1,101)");blocked(preview(task),"EXTERNAL_REFERENCE");
        target.update("DELETE FROM external_history");target.execute("CREATE TRIGGER derived_delete_trigger BEFORE DELETE ON ownership_instances FOR EACH ROW CALL \"faker.data.GenerationCleanupTest$DeleteTrigger\"");blocked(preview(task),"DELETE_TRIGGER_PRESENT");
    }
    @Test void candidatesAreBoundedAndPolicyRetainedInBatchJournal() throws Exception {
        Map<String,Object> ownership=policy();((Map)((List)ownership.get("derivedRows")).get(0)).put("maxRows",1);Map<String,Object> root=recipe(ownership);
        String task=generate(root,"FINISHED"),bill=bill(task);target.update("INSERT INTO ownership_instances VALUES(101,?,'RUNNING',NULL),(102,?,'RUNNING',NULL)",bill,bill);blocked(preview(task),"DERIVED_SCAN_LIMIT_OR_INVALID");
        Map batch=store.get(task);assertEquals(2,((List)batch.get("declaredOwnership")).size());assertEquals(Arrays.asList("state"),store.descriptor(task,"ownership_bills").mutableColumns);
    }
    @Test void predicateExcludedWeakChildrenPreventOrphaning() throws Exception {
        String task=generate(recipe(policy()),"FINISHED"),bill=bill(task);target.update("INSERT INTO ownership_instances VALUES(101,?,'RUNNING',NULL)",bill);target.execute("INSERT INTO ownership_tasks VALUES(1001,101,'other-kind')");blocked(preview(task),"EXCLUDED_DERIVED_REFERENCE");assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM ownership_bills",Integer.class));
    }
    @Test void anotherLiveBatchPlanCannotClaimTheSameWeaklyLinkedRows() throws Exception {
        Map<String,Object> ownership=m("derivedRows",Arrays.asList(m("table","ownership_audits","join",m("bill_id","id"))));Map<String,Object> root=recipe(ownership);
        root.put("columns",Arrays.asList(fixed("id","java.lang.String","same-id"),fixed("state","java.lang.String","DRAFT"),fixed("label","java.lang.String","generated")));String first=generate(root,"FINISHED");
        target.execute("CREATE TABLE other_bills(id VARCHAR(36) PRIMARY KEY,state VARCHAR(20),label VARCHAR(20))");Map<String,Object> other=recipe(ownership);other.put("name","other_bills");other.put("columns",root.get("columns"));String second=generate(other,"FINISHED");
        target.update("INSERT INTO ownership_audits VALUES('new-shared','same-id','new')");assertEquals(true,preview(first).get("eligible"));blocked(preview(second),"SHARED_BATCH_OWNERSHIP");
    }
}
