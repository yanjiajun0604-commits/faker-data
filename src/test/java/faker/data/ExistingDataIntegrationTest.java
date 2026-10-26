package faker.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import faker.data.cache.ReferenceCache;
import faker.data.domain.*;
import faker.data.service.generate.*;
import faker.data.service.generate.engine.GenerationEngine;
import faker.data.service.generate.reg.IncrementNumRegMetaType;
import faker.data.task.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.*;
import java.util.*;

import static faker.data.BulkGenerationIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ExistingDataIntegrationTest {
    private static final Path STATE = stateDirectory();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("faker.data-dir", STATE::toString);
        r.add("faker.cache.backend", () -> "memory");
    }
    @Autowired ObjectMapper mapper;
    @Autowired List<IMetaType> implementations;
    @Autowired GenerationEngine engine;
    @Autowired ReferenceCache cache;
    DriverManagerDataSource source;
    JdbcTemplate jdbc;
    String task;
    AsyncTaskExecutor executor;

    private static Path stateDirectory() {
        try { return Files.createTempDirectory("faker-existing-data-"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    @BeforeEach void setup() throws Exception {
        MetaTypeFactory registry = new MetaTypeFactory(); registry.metaTypeList = implementations; registry.afterPropertiesSet();
        source = new DriverManagerDataSource("jdbc:h2:mem:existing_" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "test");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE seed_user(id VARCHAR(36) PRIMARY KEY,display_name VARCHAR(40),org_id VARCHAR(36),enabled INT)");
        jdbc.update("INSERT INTO seed_user VALUES('u1','Alice','org1',1),('u2','Bob','org2',1),('u3','Disabled','org3',0)");
        jdbc.execute("CREATE TABLE documents(id BIGINT PRIMARY KEY,user_id VARCHAR(36) REFERENCES seed_user(id),user_name VARCHAR(40),org_id VARCHAR(36))");
        task = UUID.randomUUID().toString(); executor = mock(AsyncTaskExecutor.class); when(executor.getTaskId()).thenReturn(task);
    }
    @AfterEach void cleanup() {
        cache.removeTask(task); IncrementNumRegMetaType.clearTask(task); jdbc.execute("SHUTDOWN");
    }
    private TableVO existing(String config) throws Exception {
        TableVO users = mapper.readValue("{\"name\":\"seed_user\",\"size\":0,\"existingData\":" + config + "}", TableVO.class);
        users.setColumns(Arrays.asList(column("id","java.lang.String"),column("display_name","java.lang.String"),column("org_id","java.lang.String")));
        return users;
    }
    private TableVO documents() {
        return table("documents",7,column("id","java.lang.Long",sequence()),
                column("user_id","java.lang.String",reference("seed_user","id")),
                column("user_name","java.lang.String",reference("seed_user","display_name")),
                column("org_id","java.lang.String",reference("seed_user","org_id")));
    }
    private void generate(TableVO... tables) { engine.generate(source,Arrays.asList(tables),mock(AsyncTaskMonitor.class),executor); }

    @Test void readsEligibleExistingRowsWithoutInsertingAndKeepsTheSelectedRecordTogether() throws Exception {
        TableVO users = existing("{\"where\":{\"enabled\":1},\"orderBy\":[\"id\"],\"maxRows\":10}");
        assertDoesNotThrow(() -> generate(documents(),users));
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM seed_user",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM documents D JOIN seed_user U ON U.id=D.user_id WHERE U.enabled<>1 OR U.display_name<>D.user_name OR U.org_id<>D.org_id",Integer.class));
        assertEquals(Arrays.asList("u1","u2","u1","u2","u1","u2","u1"),jdbc.queryForList("SELECT user_id FROM documents ORDER BY id",String.class));
    }
    @Test void exactMatchCanSelectAnExistingUserByCurrentOrganization()throws Exception {
        MetaTypeVO user=mapper.readValue("{\"code\":\"REF.OTHER_TABLE\",\"refOtherTableColumn\":[\"seed_user\",\"id\"],\"refMatch\":{\"org_id\":{\"field\":\"org_id\"}}}",MetaTypeVO.class);
        MetaTypeVO name=mapper.readValue("{\"code\":\"REF.OTHER_TABLE\",\"refOtherTableColumn\":[\"seed_user\",\"display_name\"],\"refMatch\":{\"org_id\":{\"field\":\"org_id\"}}}",MetaTypeVO.class);
        TableVO documents=table("documents",3,column("id","java.lang.Long",sequence()),column("user_id","java.lang.String",user),
            column("user_name","java.lang.String",name),column("org_id","java.lang.String",literal("org2")));
        generate(documents,existing("{\"where\":{\"enabled\":1},\"orderBy\":[\"id\"]}"));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE user_id='u2' AND user_name='Bob' AND org_id='org2'",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM seed_user",Integer.class));
    }
    @Test void exactMatchCanUseExplicitNullConstant()throws Exception {
        jdbc.update("UPDATE seed_user SET org_id=NULL WHERE id='u2'");
        MetaTypeVO user=mapper.readValue("{\"code\":\"REF.OTHER_TABLE\",\"refOtherTableColumn\":[\"seed_user\",\"id\"],\"refMatch\":{\"org_id\":{\"value\":null}}}",MetaTypeVO.class);
        generate(table("documents",2,column("id","java.lang.Long",sequence()),column("user_id","java.lang.String",user)),existing("{\"where\":{\"enabled\":1}}"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE user_id='u2'",Integer.class));
    }
    @Test void emptyDatasetFailsBeforeWritingAnyTargetRows() throws Exception {
        TableVO users=existing("{\"where\":{\"display_name\":\"x' OR 1=1 --\"},\"maxRows\":10}");
        assertThrows(RuntimeException.class,()->generate(documents(),users));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM seed_user",Integer.class));
    }
    @Test void datasetLimitRejectsOverflowInsteadOfSilentlyChangingCandidates() throws Exception {
        TableVO users=existing("{\"where\":{\"enabled\":1},\"maxRows\":1}");
        RuntimeException failure=assertThrows(RuntimeException.class,()->generate(documents(),users));
        assertTrue(failure.getMessage().contains("maxRows"),failure.toString());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
    }
    @Test void rejectsSqlInsideIdentifiersWithoutChangingExistingData() throws Exception {
        TableVO users=existing("{\"orderBy\":[\"id; DELETE FROM seed_user\"]}");
        assertThrows(IllegalArgumentException.class,()->generate(documents(),users));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM seed_user",Integer.class));
    }
    @Test void insertedRowsRollBackIfARequiredExistingValueViolatesTargetConstraints() throws Exception {
        jdbc.execute("ALTER TABLE documents ADD CHECK(id<3)");
        TableVO users=existing("{\"where\":{\"enabled\":1}}");
        assertThrows(RuntimeException.class,()->generate(documents(),users));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM seed_user",Integer.class));
    }

    private TableVO expanded(TableVO child,String config) throws Exception {
        Map<String,Object> json=mapper.convertValue(child,Map.class);
        json.put("parentRows",mapper.readValue(config,Map.class));
        return mapper.convertValue(json,TableVO.class);
    }
    @Test void filtersExistingParentsAndExpandsRowsWithoutMixingTheirFields() throws Exception {
        TableVO users=existing("{\"where\":{\"enabled\":1},\"orderBy\":[\"id\"]}");
        TableVO documents=expanded(documents(),"{\"table\":\"seed_user\",\"condition\":\"#org_id == 'org2'\",\"rowsPerParent\":3}");
        generate(documents,users);
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE user_id='u2' AND user_name='Bob' AND org_id='org2'",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM seed_user",Integer.class));
    }
    @Test void expandsOnlyGeneratedParentsMatchingTheRequestedState() throws Exception {
        jdbc.execute("CREATE TABLE headers(id BIGINT PRIMARY KEY,state VARCHAR(16))");
        jdbc.execute("CREATE TABLE detail(id BIGINT PRIMARY KEY,parent_id BIGINT REFERENCES headers(id),parent_state VARCHAR(16))");
        TableVO parents=table("headers",3,column("id","java.lang.Long",sequence()),
                column("state","java.lang.String",condition("#id == '2'",literal("SUBMITTED"),literal("SAVED"))));
        TableVO details=expanded(table("detail",0,column("id","java.lang.Long",sequence()),
                column("parent_id","java.lang.Long",reference("headers","id")),
                column("parent_state","java.lang.String",reference("headers","state"))),
                "{\"table\":\"headers\",\"condition\":\"#state == 'SUBMITTED'\",\"rowsPerParent\":2}");
        generate(details,parents);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM detail WHERE parent_id=2 AND parent_state='SUBMITTED'",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM detail",Integer.class));
    }
    @Test void noMatchingParentProducesNoChildren() throws Exception {
        TableVO users=existing("{\"where\":{\"enabled\":1}}");
        TableVO documents=expanded(documents(),"{\"table\":\"seed_user\",\"condition\":\"#org_id == 'missing'\",\"rowsPerParent\":2}");
        generate(documents,users);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
    }
    @Test void parentExpansionRejectsSelfCyclesBeforeWriting() throws Exception {
        TableVO documents=expanded(documents(),"{\"table\":\"documents\",\"rowsPerParent\":2}");
        assertThrows(RuntimeException.class,()->generate(documents,existing("{}")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
    }
    @Test void rejectsBinaryRuntimeColumnsRatherThanCopyingOpaqueEngineState() throws Exception {
        jdbc.execute("ALTER TABLE seed_user ADD runtime_state BLOB");
        TableVO users=existing("{}");
        List<ColumnInfo> fields=new ArrayList<>(users.getColumns());
        fields.add(column("runtime_state","[B"));users.setColumns(fields);
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->generate(documents(),users));
        assertTrue(failure.getMessage().contains("Unsupported existingData column type"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
    }
    @Test void nestedConditionalReferencesArePlannedBeforeTheirDependentTable() throws Exception {
        TableVO users=existing("{\"where\":{\"enabled\":1}}");
        TableVO documents=documents();
        documents.getColumns().get(1).setRegs(Collections.singletonList(condition("true",reference("seed_user","id"),literal("unused"))));
        generate(documents,users);
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
    }
    @Test void candidatesRemainASingleSnapshotAcrossWriteBatches() throws Exception {
        TableVO users=existing("{\"where\":{\"id\":\"u1\"}}");
        TableVO documents=documents();documents.setSize(1001);
        AsyncTaskMonitor monitor=mock(AsyncTaskMonitor.class);
        doAnswer(call->{
            jdbc.update("UPDATE seed_user SET display_name='Changed' WHERE id='u1'");
            return "progress";
        }).when(monitor).progressAndMessage(same(executor),anyDouble(),isNull());
        engine.generate(source,Arrays.asList(documents,users),monitor,executor);
        assertEquals("Changed",jdbc.queryForObject("SELECT display_name FROM seed_user WHERE id='u1'",String.class));
        assertEquals(1001,jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE user_id='u1' AND user_name='Alice'",Integer.class));
    }
    @Test void publicJsonRecipeUsesExistingCandidatesAndExpandsOnlyMatchingDocuments() throws Exception {
        source.setUrl(source.getUrl()+";MODE=MySQL");
        try(java.sql.Connection connection=source.getConnection();
            java.io.Reader schema=Files.newBufferedReader(Paths.get("examples/mysql-existing-data-schema.sql"),java.nio.charset.StandardCharsets.UTF_8)) {
            org.h2.tools.RunScript.execute(connection,schema);
        }
        GenerateVO request=mapper.readValue(Paths.get("examples/generate-existing-data.json").toFile(),GenerateVO.class);
        engine.generate(source,request.getTables(),mock(AsyncTaskMonitor.class),executor);
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM demo_lookup_user",Integer.class));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM demo_lookup_document",Integer.class));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM demo_lookup_item",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM demo_lookup_document D JOIN demo_lookup_user U ON D.user_id=U.id WHERE U.enabled<>1 OR U.display_name<>D.user_name OR U.org_id<>D.org_id",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM demo_lookup_item I JOIN demo_lookup_document D ON D.id=I.document_id WHERE D.state<>'SUBMITTED' OR I.user_id<>D.user_id",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(DISTINCT document_id) FROM demo_lookup_item",Integer.class));
    }
}
