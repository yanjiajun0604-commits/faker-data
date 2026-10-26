package faker.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import faker.data.cache.ReferenceCache;
import faker.data.domain.GenerateVO;
import faker.data.service.db.AbstractJdbcService;
import faker.data.service.generate.IMetaType;
import faker.data.service.generate.MetaTypeFactory;
import faker.data.service.generate.reg.IncrementNumRegMetaType;
import faker.data.task.AsyncTaskExecutor;
import faker.data.task.AsyncTaskMonitor;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DocumentRecipeIntegrationTest {
    private static final Path STATE = stateDirectory();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("faker.data-dir", STATE::toString);
        registry.add("faker.cache.backend", () -> "memory");
    }

    @Autowired List<IMetaType> implementations;
    @Autowired ReferenceCache cache;
    @Autowired ObjectMapper mapper;

    private static Path stateDirectory() {
        try { return Files.createTempDirectory("faker-document-recipe-"); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    @Test void documentedRecipeProducesExactStatesSharedKeysAndBalancedDetails() throws Exception {
        // Exercise the public recipe, not a separately reconstructed test configuration.
        GenerateVO request = mapper.readValue(Paths.get("examples/generate-document-states.json").toFile(), GenerateVO.class);
        MetaTypeFactory registry = new MetaTypeFactory();
        registry.metaTypeList = implementations;
        registry.afterPropertiesSet();
        String url = "jdbc:h2:mem:documents_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        DriverManagerDataSource target = new DriverManagerDataSource(url, "sa", "test");
        JdbcTemplate jdbc = new JdbcTemplate(target);
        String taskId = UUID.randomUUID().toString();
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        when(executor.getTaskId()).thenReturn(taskId);
        faker.data.domain.DataSource source = new faker.data.domain.DataSource();
        source.setUrl(url); source.setUsername("sa"); source.setPassword("test");
        AbstractJdbcService service = new AbstractJdbcService(source) {
            protected String loadDriverClass() { return "org.h2.Driver"; }
            protected String schemaPattern() { return "PUBLIC"; }
        };
        try {
            try (Connection connection = target.getConnection();
                 java.io.Reader schema = Files.newBufferedReader(Paths.get("examples/mysql-document-schema.sql"), StandardCharsets.UTF_8)) {
                RunScript.execute(connection, schema);
            }
            service.generateData(request.getTables(), mock(AsyncTaskMonitor.class), executor);
            assertEquals(170, count(jdbc, "SELECT COUNT(*) FROM demo_document"));
            assertEquals(50, count(jdbc, "SELECT COUNT(*) FROM demo_document WHERE state='SAVED'"));
            assertEquals(20, count(jdbc, "SELECT COUNT(*) FROM demo_document WHERE state='SUBMITTED'"));
            assertEquals(100, count(jdbc, "SELECT COUNT(*) FROM demo_document WHERE state='APPROVED'"));
            assertEquals(170, count(jdbc, "SELECT COUNT(DISTINCT document_no) FROM demo_document"));
            assertEquals(170, count(jdbc, "SELECT COUNT(*) FROM demo_document WHERE batch_tag='DOC-DEMO-CHANGE-ME'"));
            assertEquals(170, count(jdbc, "SELECT COUNT(*) FROM demo_document_extension"));
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM demo_document D LEFT JOIN demo_document_extension E ON E.id=D.id WHERE E.id IS NULL OR E.state_copy<>D.state OR E.category<>'EXPENSE'"));
            assertEquals(340, count(jdbc, "SELECT COUNT(*) FROM demo_document_line"));
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM demo_document_line L LEFT JOIN demo_document D ON D.id=L.document_id WHERE D.id IS NULL OR L.document_state<>D.state"));
            assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM demo_document D WHERE (SELECT COUNT(*) FROM demo_document_line L WHERE L.document_id=D.id)<>2 OR D.total_amount<>(SELECT SUM(L.amount) FROM demo_document_line L WHERE L.document_id=D.id)"));
            assertEquals(0, new BigDecimal("4250.00").compareTo(jdbc.queryForObject("SELECT SUM(total_amount) FROM demo_document", BigDecimal.class)));
        } finally {
            cache.removeTask(taskId);
            IncrementNumRegMetaType.clearTask(taskId);
            jdbc.execute("SHUTDOWN");
        }
    }

    private int count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
