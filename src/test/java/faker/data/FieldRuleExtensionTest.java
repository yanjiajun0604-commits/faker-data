package faker.data;
import com.fasterxml.jackson.databind.*;
import faker.data.cache.ReferenceCache;
import faker.data.domain.GenerateVO;
import faker.data.extension.FieldExtensionFixtureMain;
import faker.data.service.generate.engine.GenerationEngine;
import faker.data.task.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class FieldRuleExtensionTest {
    static final Path ROOT=directory();
    static Path directory(){try{return Files.createTempDirectory("faker-field-extension-");}catch(Exception e){throw new RuntimeException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("faker.data-dir",()->ROOT.resolve("state").toString());r.add("faker.extensions.root",()->ROOT.resolve("extensions").toString());r.add("faker.extensions.enabled",()->true);}
    @Autowired GenerationEngine engine;
    @Autowired ObjectMapper mapper;
    @Autowired ReferenceCache cache;
    void install(String id,String mode)throws Exception {
        Path folder=ROOT.resolve("extensions").resolve(id);Files.createDirectories(folder);
        Map<String,Object> spec=new LinkedHashMap<>();
        spec.put("command",Arrays.asList(Paths.get(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),FieldExtensionFixtureMain.class.getName(),"${input}","${output}",mode));
        spec.put("allowedFields",Collections.singletonMap("children",Collections.singletonList("payload")));
        mapper.writeValue(folder.resolve("extension.json").toFile(),spec);
    }
    GenerateVO request(String extension)throws Exception {
        return mapper.readValue("{\"tables\":[{\"name\":\"parents\",\"size\":1,\"columns\":[{\"name\":\"id\",\"javaType\":\"java.lang.String\",\"nullable\":0,\"regs\":[{\"code\":\"FIX.UUID\"}]}]},{\"name\":\"children\",\"size\":2,\"columns\":[{\"name\":\"id\",\"javaType\":\"java.lang.String\",\"nullable\":0,\"regs\":[{\"code\":\"FIX.UUID\"}]},{\"name\":\"parent_id\",\"javaType\":\"java.lang.String\",\"nullable\":0,\"regs\":[{\"code\":\"REF.OTHER_TABLE\",\"refOtherTableColumn\":[\"parents\",\"id\"]}]},{\"name\":\"payload\",\"javaType\":\"byte[]\",\"nullable\":0,\"regs\":[{\"code\":\"EXT.FIELD\",\"extension\":\""+extension+"\",\"extensionParameters\":{}}]}]}]}",GenerateVO.class);
    }
    @Test void ordinaryRulesProduceFreshIdsAndReferencesWhileOnlyPayloadUsesExtension()throws Exception {
        install("ok","ok");GenerateVO request=request("ok");
        DriverManagerDataSource target=new DriverManagerDataSource("jdbc:h2:mem:field_ok;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1","sa","test");JdbcTemplate jdbc=new JdbcTemplate(target);
        jdbc.execute("CREATE TABLE parents(id VARCHAR(36) PRIMARY KEY)");jdbc.execute("CREATE TABLE children(id VARCHAR(36) PRIMARY KEY,parent_id VARCHAR(36),payload BLOB)");
        try {
            for(int i=0;i<2;i++) {
                AsyncTaskExecutor executor=mock(AsyncTaskExecutor.class);String id=UUID.randomUUID().toString();when(executor.getTaskId()).thenReturn(id);
                try{engine.generate(target,request.getTables(),mock(AsyncTaskMonitor.class),executor);}finally{cache.removeTask(id);}
            }
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM parents",Integer.class));
            assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM children",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM children C LEFT JOIN parents P ON C.parent_id=P.id WHERE P.id IS NULL",Integer.class));
            for(Map<String,Object> row:jdbc.queryForList("SELECT parent_id,payload FROM children"))assertArrayEquals(row.get("parent_id").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),(byte[])row.get("payload"));
        }finally{jdbc.execute("SHUTDOWN");}
    }
    @Test void extensionCannotOverwriteAnOrdinaryField()throws Exception {
        install("bad","tamper");DriverManagerDataSource target=new DriverManagerDataSource("jdbc:h2:mem:field_bad;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1","sa","test");JdbcTemplate jdbc=new JdbcTemplate(target);
        jdbc.execute("CREATE TABLE parents(id VARCHAR(36) PRIMARY KEY)");jdbc.execute("CREATE TABLE children(id VARCHAR(36) PRIMARY KEY,parent_id VARCHAR(36),payload BLOB)");
        AsyncTaskExecutor executor=mock(AsyncTaskExecutor.class);String id=UUID.randomUUID().toString();when(executor.getTaskId()).thenReturn(id);
        try {
            assertThrows(RuntimeException.class,()->engine.generate(target,request("bad").getTables(),mock(AsyncTaskMonitor.class),executor));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM parents",Integer.class));
        }finally{cache.removeTask(id);jdbc.execute("SHUTDOWN");}
    }
    @Test void aLaterDatabaseConstraintRollsBackOrdinaryAndExtensionValuesTogether()throws Exception {
        install("rollback","ok");DriverManagerDataSource target=new DriverManagerDataSource("jdbc:h2:mem:field_rollback;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1","sa","test");JdbcTemplate jdbc=new JdbcTemplate(target);
        jdbc.execute("CREATE TABLE parents(id VARCHAR(36) PRIMARY KEY)");jdbc.execute("CREATE TABLE children(id VARCHAR(36) PRIMARY KEY,parent_id VARCHAR(36),payload BLOB,CHECK(parent_id='never-a-generated-uuid'))");
        AsyncTaskExecutor executor=mock(AsyncTaskExecutor.class);String id=UUID.randomUUID().toString();when(executor.getTaskId()).thenReturn(id);
        try {
            assertThrows(RuntimeException.class,()->engine.generate(target,request("rollback").getTables(),mock(AsyncTaskMonitor.class),executor));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM parents",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM children",Integer.class));
        }finally{cache.removeTask(id);jdbc.execute("SHUTDOWN");}
    }
    @Test void smallBinaryCanBeReturnedInlineWithoutChangingItsBytes()throws Exception {
        install("inline","inline");DriverManagerDataSource target=new DriverManagerDataSource("jdbc:h2:mem:field_inline;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1","sa","test");JdbcTemplate jdbc=new JdbcTemplate(target);
        jdbc.execute("CREATE TABLE parents(id VARCHAR(36) PRIMARY KEY)");jdbc.execute("CREATE TABLE children(id VARCHAR(36) PRIMARY KEY,parent_id VARCHAR(36),payload BLOB)");
        AsyncTaskExecutor executor=mock(AsyncTaskExecutor.class);String id=UUID.randomUUID().toString();when(executor.getTaskId()).thenReturn(id);
        try {
            engine.generate(target,request("inline").getTables(),mock(AsyncTaskMonitor.class),executor);
            for(Map<String,Object> row:jdbc.queryForList("SELECT parent_id,payload FROM children"))assertArrayEquals(row.get("parent_id").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),(byte[])row.get("payload"));
            try(java.util.stream.Stream<Path> files=Files.list(ROOT.resolve("state/field-extension-jobs").resolve(id).resolve("inline"))) {
                assertEquals(0,files.filter(p->p.toString().endsWith(".bin")).count());
            }
        }finally{cache.removeTask(id);jdbc.execute("SHUTDOWN");}
    }
    @Test void invalidInlineEncodingIsRejectedAndRollsBack()throws Exception {
        install("invalid-inline","invalid-base64");DriverManagerDataSource target=new DriverManagerDataSource("jdbc:h2:mem:field_inline_bad;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1","sa","test");JdbcTemplate jdbc=new JdbcTemplate(target);
        jdbc.execute("CREATE TABLE parents(id VARCHAR(36) PRIMARY KEY)");jdbc.execute("CREATE TABLE children(id VARCHAR(36) PRIMARY KEY,parent_id VARCHAR(36),payload BLOB)");
        AsyncTaskExecutor executor=mock(AsyncTaskExecutor.class);String id=UUID.randomUUID().toString();when(executor.getTaskId()).thenReturn(id);
        try {
            RuntimeException e=assertThrows(RuntimeException.class,()->engine.generate(target,request("invalid-inline").getTables(),mock(AsyncTaskMonitor.class),executor));
            assertTrue(e.getMessage().contains("INVALID_EXTENSION_BASE64"),e.toString());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM parents",Integer.class));
        }finally{cache.removeTask(id);jdbc.execute("SHUTDOWN");}
    }
    @Test void inlineHashAndSizeLimitsAreEnforcedBeforeWriting()throws Exception {
        for(String mode:Arrays.asList("bad-inline-hash","large-inline")) {
            install(mode,mode);DriverManagerDataSource target=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1","sa","test");JdbcTemplate jdbc=new JdbcTemplate(target);
            jdbc.execute("CREATE TABLE parents(id VARCHAR(36) PRIMARY KEY)");jdbc.execute("CREATE TABLE children(id VARCHAR(36) PRIMARY KEY,parent_id VARCHAR(36),payload BLOB)");
            AsyncTaskExecutor executor=mock(AsyncTaskExecutor.class);String id=UUID.randomUUID().toString();when(executor.getTaskId()).thenReturn(id);
            try {
                RuntimeException e=assertThrows(RuntimeException.class,()->engine.generate(target,request(mode).getTables(),mock(AsyncTaskMonitor.class),executor));
                assertTrue(e.getMessage().contains(mode.equals("large-inline")?"INVALID_EXTENSION_BASE64":"EXTENSION_BINARY_HASH_MISMATCH"),e.toString());
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM parents",Integer.class));
            }finally{cache.removeTask(id);jdbc.execute("SHUTDOWN");}
        }
    }
}
