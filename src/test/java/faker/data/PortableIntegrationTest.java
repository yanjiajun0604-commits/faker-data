package faker.data;

import faker.data.cache.*;
import faker.data.scheme.*;
import faker.data.task.*;
import faker.data.domain.*;
import faker.data.service.db.AbstractJdbcService;
import faker.data.service.generate.MetaTypeVO;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"faker.tasks.workers=1","faker.tasks.queue-capacity=1","faker.cache.backend=memory"})
class PortableIntegrationTest {
    private static final Path DIR=createDirectory();
    private static Path createDirectory(){try{return Files.createTempDirectory("faker-portable-test-");}catch(Exception e){throw new RuntimeException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("faker.data-dir",DIR::toString);}
    @Autowired LocalTaskService tasks;
    @Autowired ReferenceCache cache;
    @Autowired FakerSchemeService schemes;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @org.springframework.boot.web.server.LocalServerPort int port;
    static volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);

    @Test void savesAndUpdatesSchemesWithoutExternalDatabase(){
        FakerScheme first=schemes.save("portable",null,Collections.emptyList());
        FakerScheme second=schemes.save("portable",null,Collections.emptyList());
        assertEquals(first.getId(),second.getId());assertEquals(first.getCreatedAt(),second.getCreatedAt());
        assertEquals(1,schemes.listAll().size());
        DriverManagerDataSource reopened=new DriverManagerDataSource("jdbc:sqlite:"+DIR.resolve("faker-data.db"));
        assertEquals("portable",new JdbcTemplate(reopened).queryForObject("SELECT name FROM faker_scheme WHERE id=?",String.class,first.getId()));
        schemes.delete(first.getId());assertNull(schemes.getById(first.getId()));
    }
    @Test void queueIsBoundedAndCommitControlsSuccess() throws Exception {
        entered=new CountDownLatch(1);release=new CountDownLatch(1);
        String first=tasks.submit("TEST_BLOCK",null);
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertEquals(TaskState.PROCESSING,tasks.query(first).getState());assertTrue(tasks.query(first).getProgress()<100);
            String second=tasks.submit("TEST_ERROR",null);
            assertEquals(TaskState.WAITING,tasks.query(second).getState());
            assertThrows(IllegalStateException.class,()->tasks.submit("TEST_ERROR",null));
            release.countDown();await(first,TaskState.FINISHED);await(second,TaskState.ERROR);
            assertFalse(tasks.query(second).getErrorMsg().contains("secret"));
            assertEquals(100D,tasks.query(first).getProgress());
            for(int i=0;i<100 && ((MemoryReferenceCache)cache).size()!=0;i++)Thread.sleep(10);
            assertEquals(0,((MemoryReferenceCache)cache).size());
            assertEquals("FINISHED",jdbc.queryForObject("SELECT state FROM faker_task WHERE id=?",String.class,first));
        } finally {release.countDown();}
    }
    @Test void unknownTypeIsRejectedAndHttpContractIsAvailable(){
        assertThrows(IllegalArgumentException.class,()->tasks.submit("UNKNOWN",null));
        assertEquals(TaskState.NONE,tasks.query("missing").getState());
        Map<?,?> response=http.getForObject("/asyncTask/queryProcess/missing",Map.class);
        assertEquals(0,response.get("code"),response.toString());assertEquals("NONE",((Map<?,?>)response.get("data")).get("state"));
    }
    @Test void defaultWebUiGuidesAndApiAreAvailableAtTheRootPath(){
        String base="http://127.0.0.1:"+port;
        org.springframework.http.ResponseEntity<String> page=http.getForEntity(base+"/",String.class);
        assertEquals(200,page.getStatusCodeValue());assertTrue(page.getBody().contains("<div id=\"app\"></div>"));
        org.springframework.http.ResponseEntity<String> guide=http.getForEntity(base+"/AI_USAGE.md",String.class);
        assertEquals(200,guide.getStatusCodeValue());assertTrue(guide.getBody().contains("http://127.0.0.1:8085"));
        Map<?,?> response=http.getForObject(base+"/asyncTask/list",Map.class);
        assertEquals(0,response.get("code"));
        assertEquals(404,http.getForEntity(base+"/faker/asyncTask/list",String.class).getStatusCodeValue());
    }
    @Test void realGenerationEngineCommitsReferencesAndRollsBackFailure() throws Exception {
        String id=tasks.submit("TEST_ENGINE",Boolean.FALSE);await(id,TaskState.FINISHED);
        JdbcTemplate target=target();
        assertEquals(50,target.queryForObject("SELECT count(*) FROM children",Integer.class));
        assertEquals(0,target.queryForObject("SELECT count(*) FROM children c LEFT JOIN parents p ON c.parent_id=p.id WHERE p.id IS NULL",Integer.class));
        String failed=tasks.submit("TEST_ENGINE",Boolean.TRUE);await(failed,TaskState.ERROR);
        assertEquals(0,target.queryForObject("SELECT count(*) FROM parents",Integer.class));
        assertEquals(0,((MemoryReferenceCache)cache).size());
    }
    private void await(String id,TaskState expected) throws Exception {
        for(int i=0;i<500;i++){
            TaskProcessVO state=tasks.query(id);
            if(state.getState()==TaskState.FINISHED || state.getState()==TaskState.ERROR){assertEquals(expected,state.getState(),state.getErrorMsg());return;}
            Thread.sleep(20);
        }fail("task timeout: "+id);
    }
    private static JdbcTemplate target(){return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:portable;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false","sa","test"));}
    private static TableVO table(String name,int count,String column,MetaTypeVO rule){
        TableVO t=new TableVO();t.setName(name);t.setSize(count);
        ColumnInfo c=new ColumnInfo();c.setName(column);c.setDbType("VARCHAR");c.setJavaType("java.lang.String");c.setRegs(Collections.singletonList(rule));
        t.setColumns(Collections.singletonList(c));return t;
    }
    @TestConfiguration static class Fixtures {
        @Bean AsyncTaskExecutor blocking(ReferenceCache cache){return new AsyncTaskExecutor(){
            public String getTaskPoolType(){return "TEST_BLOCK";}public String getTitle(){return "test";}
            public void execute(Object args,AsyncTaskMonitor monitor){
                cache.put(getTaskId(),"c","r","value");monitor.progressAndMessage(this,100,"before commit");entered.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){throw new RuntimeException(e);}
            }
        };}
        @Bean AsyncTaskExecutor failing(ReferenceCache cache){return new AsyncTaskExecutor(){
            public String getTaskPoolType(){return "TEST_ERROR";}public String getTitle(){return "test";}
            public void execute(Object args,AsyncTaskMonitor monitor){cache.put(getTaskId(),"c","r","v");throw new IllegalStateException("password=secret failed");}
        };}
        @Bean AsyncTaskExecutor engine(){return new AsyncTaskExecutor(){
            public String getTaskPoolType(){return "TEST_ENGINE";}public String getTitle(){return "test";}
            public void execute(Object args,AsyncTaskMonitor monitor){
                JdbcTemplate jdbc=target();jdbc.execute("DROP ALL OBJECTS");
                jdbc.execute("CREATE TABLE parents(id VARCHAR(100) PRIMARY KEY)");
                if(!Boolean.TRUE.equals(args))jdbc.execute("CREATE TABLE children(parent_id VARCHAR(100) REFERENCES parents(id))");
                faker.data.domain.DataSource ds=new faker.data.domain.DataSource();ds.setUrl("jdbc:h2:mem:portable;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");ds.setUsername("sa");ds.setPassword("test");
                AbstractJdbcService service=new AbstractJdbcService(ds){protected String loadDriverClass(){return "org.h2.Driver";}protected String schemaPattern(){return "PUBLIC";}};
                MetaTypeVO id=new MetaTypeVO();id.setCode("FIX.UUID");
                MetaTypeVO ref=new MetaTypeVO();ref.setCode("REF.OTHER_TABLE");ref.setRefOtherTableColumn(new String[]{"parents","id"});
                service.generateData(Arrays.asList(table("children",50,"parent_id",ref),table("parents",20,"id",id)),monitor,this);
            }
        };}
    }
}
