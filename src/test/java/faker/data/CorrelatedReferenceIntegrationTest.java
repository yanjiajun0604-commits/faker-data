package faker.data;

import com.fasterxml.jackson.databind.*;
import faker.data.cache.ReferenceCache;
import faker.data.domain.*;
import faker.data.service.generate.*;
import faker.data.service.generate.engine.GenerationEngine;
import faker.data.task.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import java.nio.file.*;
import java.util.*;
import static faker.data.BulkGenerationIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class CorrelatedReferenceIntegrationTest {
    static final Path STATE=directory();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("faker.data-dir",STATE::toString);}
    static Path directory(){try{return Files.createTempDirectory("faker-match-");}catch(Exception e){throw new IllegalStateException(e);}}
    @Autowired ObjectMapper mapper;
    @Autowired List<IMetaType> implementations;
    @Autowired GenerationEngine engine;
    @Autowired ReferenceCache cache;
    DriverManagerDataSource source;
    JdbcTemplate jdbc;
    String task;
    AsyncTaskExecutor executor;
    @BeforeEach void setup() throws Exception {
        MetaTypeFactory f=new MetaTypeFactory();f.metaTypeList=implementations;f.afterPropertiesSet();
        source=new DriverManagerDataSource("jdbc:h2:mem:match_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false","sa","test");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE documents(id VARCHAR(36) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE contents(id VARCHAR(36) PRIMARY KEY,document_id VARCHAR(36) REFERENCES documents(id),role VARCHAR(16))");
        jdbc.execute("CREATE TABLE tasks(id VARCHAR(36) PRIMARY KEY,document_id VARCHAR(36) REFERENCES documents(id),input_id VARCHAR(36) REFERENCES contents(id),output_id VARCHAR(36) REFERENCES contents(id))");
        task=UUID.randomUUID().toString();executor=mock(AsyncTaskExecutor.class);when(executor.getTaskId()).thenReturn(task);
    }
    @AfterEach void cleanup(){cache.removeTask(task);jdbc.execute("SHUTDOWN");}
    MetaTypeVO matched(String role,String options)throws Exception{
        // Before implementation this ignores the new options, exposing legacy round-robin selection.
        return mapper.readerFor(MetaTypeVO.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(
            "{\"code\":\"REF.OTHER_TABLE\",\"refOtherTableColumn\":[\"contents\",\"id\"],\"refMatch\":{\"document_id\":{\"field\":\"document_id\"},\"role\":{\"value\":\""+role+"\"}}"+options+"}");
    }
    TableVO parent(TableVO t,int count)throws Exception{
        ParentRowsConfig p=new ParentRowsConfig();p.setTable("documents");p.setRowsPerParent(count);t.setParentRows(p);return t;
    }
    List<TableVO> recipe(String inputRole,String options,int contentCount)throws Exception{
        TableVO docs=table("documents",2,column("id","java.lang.String",rule("FIX.UUID")));
        MetaTypeVO role=condition("#__rowIndex % 2 == 0",literal("INPUT"),literal("OUTPUT"));
        TableVO contents=parent(table("contents",0,column("id","java.lang.String",rule("FIX.UUID")),
            column("document_id","java.lang.String",reference("documents","id")),column("role","java.lang.String",role)),contentCount);
        TableVO tasks=parent(table("tasks",0,column("id","java.lang.String",rule("FIX.UUID")),
            column("input_id","java.lang.String",matched(inputRole,options)),
            column("output_id","java.lang.String",matched("OUTPUT",",\"refOnMissing\":\"NULL\"")),
            column("document_id","java.lang.String",reference("documents","id"))),1);
        return Arrays.asList(tasks,contents,docs);
    }
    void generate(List<TableVO> tables){engine.generate(source,tables,mock(AsyncTaskMonitor.class),executor);}
    @Test void selectsContentByCurrentDocumentAndRoleAcrossRepeatedRuns()throws Exception{
        generate(recipe("INPUT","",2));generate(recipe("INPUT","",2));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM tasks",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tasks t JOIN contents i ON i.id=t.input_id JOIN contents o ON o.id=t.output_id WHERE i.document_id<>t.document_id OR o.document_id<>t.document_id OR i.role<>'INPUT' OR o.role<>'OUTPUT'",Integer.class));
    }
    @Test void missingRequiredMatchRollsBackTheWholeGeneration()throws Exception{
        RuntimeException e=assertThrows(RuntimeException.class,()->generate(recipe("ABSENT","",2)));
        assertTrue(e.getMessage().contains("REF_MATCH_NONE"),e.toString());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
    }
    @Test void ambiguousMatchFailsInsteadOfSilentlyChoosingAnotherRow()throws Exception{
        RuntimeException e=assertThrows(RuntimeException.class,()->generate(recipe("INPUT","",4)));
        assertTrue(e.getMessage().contains("REF_MATCH_MULTIPLE"),e.toString());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM contents",Integer.class));
    }
    @Test void explicitOptionalMatchReturnsNull()throws Exception{
        generate(recipe("ABSENT",",\"refOnMissing\":\"NULL\"",2));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE input_id IS NULL AND output_id IS NOT NULL",Integer.class));
    }
    @Test void explicitFirstMatchIsStableAndStillBelongsToTheSameDocument()throws Exception{
        List<TableVO> tables=recipe("INPUT",",\"refOnMultiple\":\"FIRST\"",4);
        tables.get(0).getColumns().stream().filter(c->c.getName().equals("output_id")).findFirst().get().getRegs().get(0).setRefOnMultiple("FIRST");
        generate(tables);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tasks t JOIN contents c ON c.id=t.input_id WHERE c.document_id<>t.document_id OR c.role<>'INPUT'",Integer.class));
    }
    @Test void missingMatchColumnIsRejectedBeforeWriting()throws Exception{
        List<TableVO> tables=recipe("INPUT","",2);
        MetaTypeVO rule=tables.get(0).getColumns().get(1).getRegs().get(0);
        rule.getRefMatch().put("unknown_column",Collections.singletonMap("value","x"));
        assertThrows(IllegalArgumentException.class,()->generate(tables));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM documents",Integer.class));
    }
}
