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
import static faker.data.GenerationBatchTest.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class VirtualFieldIntegrationTest {
    static final Path DIR=GenerationBatchTest.directory();
    @Autowired TestRestTemplate http;
    JdbcTemplate target;Map<String,Object> source;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("faker.data-dir",DIR::toString);}
    @BeforeEach void setup(){String url="jdbc:h2:mem:virtual_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";source=m("type","MYSQL","url",url,"username","sa","password","fixture");target=new JdbcTemplate(new DriverManagerDataSource(url,"sa","fixture"));target.execute("CREATE TABLE virtual_documents(id VARCHAR(36) PRIMARY KEY)");target.execute("CREATE TABLE virtual_contents(id VARCHAR(64) PRIMARY KEY)");target.execute("CREATE TABLE virtual_tasks(id VARCHAR(36) PRIMARY KEY,document_id VARCHAR(36) REFERENCES virtual_documents(id),input_id VARCHAR(64) REFERENCES virtual_contents(id),output_id VARCHAR(64) REFERENCES virtual_contents(id))");target.execute("INSERT INTO virtual_documents VALUES('protected')");}
    Map<String,Object> logical(Map<String,Object> field){field.put("virtual",true);return field;}
    Map<String,Object> ref(String table,String field){return m("code","REF.OTHER_TABLE","refOtherTableColumn",Arrays.asList(table,field));}
    Map<String,Object> same(String field){return m("code","REF.SELF_COLUMN","refSelfColumn",field);}
    Map<String,Object> constant(String value){return m("code","RES.INPUTAREA","inputs",Arrays.asList(m("value",value,"weight",1)));}
    Map<String,Object> exact(String role){return m("code","REF.OTHER_TABLE","refOtherTableColumn",Arrays.asList("virtual_contents","id"),"refMatch",m("owner_marker",m("field","document_id"),"role_marker",m("value",role)));}
    List<Map<String,Object>> recipe(String inputRole){
        Map<String,Object> docs=table("virtual_documents",3,col("id","java.lang.String",m("code","FIX.UUID")),logical(col("selected_marker","java.lang.String",m("code","COND.EXPR","condition","#__rowIndex < 2","onTrue",Arrays.asList(constant("YES")),"onFalse",Arrays.asList(constant("NO"))))));
        Map<String,Object> contents=table("virtual_contents",0,logical(col("owner_marker","java.lang.String",ref("virtual_documents","id"))),col("id","java.lang.String",same("owner_marker"),constant("-"),same("role_marker")),logical(col("role_marker","java.lang.String",m("code","COND.EXPR","condition","#__rowIndex % 2 == 0","onTrue",Arrays.asList(constant("INPUT")),"onFalse",Arrays.asList(constant("OUTPUT"))))));contents.put("parentRows",m("table","virtual_documents","rowsPerParent",2));
        Map<String,Object> tasks=table("virtual_tasks",0,col("id","java.lang.String",m("code","FIX.UUID")),col("input_id","java.lang.String",exact(inputRole)),col("output_id","java.lang.String",exact("OUTPUT")),col("document_id","java.lang.String",ref("virtual_documents","id")));tasks.put("parentRows",m("table","virtual_documents","rowsPerParent",1,"condition","#selected_marker == 'YES'"));return Arrays.asList(tasks,contents,docs);
    }
    @SuppressWarnings("unchecked") Map<String,Object> preflight(List<Map<String,Object>> tables){Map response=http.postForObject("/generation/preflight",m("dataSource",source,"tables",tables),Map.class);assertEquals(0,response.get("code"),response.toString());return (Map<String,Object>)response.get("data");}
    String generate(List<Map<String,Object>> tables,String expected) throws Exception {Map response=http.postForObject("/publish/task/GEN_DATA",m("dataSource",source,"tables",tables),Map.class);assertEquals(0,response.get("code"),response.toString());String id=(String)response.get("data");for(int n=0;n<200;n++){Map state=(Map)http.getForObject("/asyncTask/queryProcess/"+id,Map.class).get("data");if(Arrays.asList("FINISHED","ERROR").contains(state.get("state"))){assertEquals(expected,state.get("state"),state.toString());return id;}Thread.sleep(25);}throw new AssertionError("Unknown result");}
    @Test void markersDriveOrdinaryExactReferencesAndParentFiltersWithoutDatabaseColumns() throws Exception {
        List<Map<String,Object>> tables=recipe("INPUT");Map checked=preflight(tables);assertEquals(true,checked.get("valid"),checked.toString());String task=generate(tables,"FINISHED");
        assertEquals(4,target.queryForObject("SELECT COUNT(*) FROM virtual_documents",Integer.class));assertEquals(6,target.queryForObject("SELECT COUNT(*) FROM virtual_contents",Integer.class));assertEquals(2,target.queryForObject("SELECT COUNT(*) FROM virtual_tasks",Integer.class));
        assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM virtual_tasks WHERE input_id<>CONCAT(document_id,'-INPUT') OR output_id<>CONCAT(document_id,'-OUTPUT')",Integer.class));assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE COLUMN_NAME IN ('owner_marker','role_marker','selected_marker')",Integer.class));
        Map batch=(Map)http.getForObject("/batches/"+task,Map.class).get("data");assertEquals(11,batch.get("rowCount"));assertEquals(true,batch.get("trackable"));
        Map plan=(Map)http.postForObject("/batches/"+task+"/cleanup/preview",m("dataSource",source),Map.class).get("data");assertEquals(true,plan.get("eligible"),plan.toString());Map result=http.postForObject("/batches/"+task+"/cleanup/execute",m("dataSource",source,"planId",plan.get("planId"),"planHash",plan.get("planHash"),"confirmDelete",true),Map.class);assertEquals(0,result.get("code"),result.toString());assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM virtual_documents",Integer.class));
    }
    @Test void absentExactMatchRollsBackEveryPhysicalTable() throws Exception {generate(recipe("MISSING"),"ERROR");assertEquals(1,target.queryForObject("SELECT COUNT(*) FROM virtual_documents",Integer.class));assertEquals(0,target.queryForObject("SELECT COUNT(*) FROM virtual_contents",Integer.class));}
    @SuppressWarnings("unchecked") @Test void virtualFieldsCannotHideRealColumns(){Map<String,Object> virtualId=logical(fixed("id","java.lang.String","new"));Map checked=preflight(Arrays.asList(table("virtual_documents",1,virtualId,fixed("other","java.lang.String","ignored"))));assertEquals(false,checked.get("valid"));assertTrue(((List<Map<String,Object>>)checked.get("errors")).stream().anyMatch(e->"VIRTUAL_SHADOWS_DATABASE_COLUMN".equals(e.get("code"))),checked.toString());}
    @Test void allVirtualTargetsAndVirtualReadOnlySourcesAreRejected(){Map all=table("virtual_documents",1,logical(fixed("marker","java.lang.String","tag")));Map result=http.postForObject("/generation/validate",m("tables",Arrays.asList(all)),Map.class);assertNotEquals(0,result.get("code"));Map existing=table("virtual_documents",0,col("id","java.lang.String"),logical(fixed("marker","java.lang.String","tag")));existing.put("existingData",m("maxRows",100));result=http.postForObject("/generation/validate",m("tables",Arrays.asList(existing)),Map.class);assertNotEquals(0,result.get("code"));}
    @SuppressWarnings("unchecked") @Test void logicalFieldsDoNotSatisfyMissingPhysicalRequiredColumns(){target.execute("CREATE TABLE virtual_required(id VARCHAR(36) PRIMARY KEY,code VARCHAR(20) NOT NULL)");Map checked=preflight(Arrays.asList(table("virtual_required",1,col("id","java.lang.String",m("code","FIX.UUID")),logical(fixed("code_marker","java.lang.String","tag")))));assertEquals(false,checked.get("valid"));assertTrue(((List<Map<String,Object>>)checked.get("errors")).stream().anyMatch(e->"REQUIRED_COLUMN_MISSING".equals(e.get("code")) && "code".equals(e.get("column"))),checked.toString());}
}
