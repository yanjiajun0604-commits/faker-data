package faker.data;

import faker.data.cache.ReferenceCache;
import faker.data.domain.*;
import faker.data.service.db.AbstractJdbcService;
import faker.data.service.generate.*;
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
import java.util.concurrent.*;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BulkGenerationIntegrationTest {
    private static final Path STATE = directory();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("faker.data-dir", STATE::toString);
        r.add("faker.cache.backend", () -> "memory");
    }
    @Autowired List<IMetaType> implementations;
    @Autowired ReferenceCache cache;
    private JdbcTemplate target;
    private AbstractJdbcService service;
    private String taskId;
    private final AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
    private final AsyncTaskMonitor monitor = mock(AsyncTaskMonitor.class);

    private static Path directory() {
        try { return Files.createTempDirectory("faker-bulk-state-"); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    @BeforeEach void setup() throws Exception {
        // Existing unit tests replace the static registry. Restore actual application strategies.
        MetaTypeFactory registry = new MetaTypeFactory();
        registry.metaTypeList = implementations;
        registry.afterPropertiesSet();
        String url = "jdbc:h2:mem:bulk_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        DataSource ds = new DataSource(); ds.setUrl(url); ds.setUsername("sa"); ds.setPassword("test");
        target = new JdbcTemplate(new DriverManagerDataSource(url, "sa", "test"));
        service = new AbstractJdbcService(ds) {
            protected String loadDriverClass() { return "org.h2.Driver"; }
            protected String schemaPattern() { return "PUBLIC"; }
        };
        taskId = UUID.randomUUID().toString();
        when(executor.getTaskId()).thenReturn(taskId);
    }
    @AfterEach void cleanup() {
        cache.removeTask(taskId);
        IncrementNumRegMetaType.clearTask(taskId);
        target.execute("SHUTDOWN");
    }
    @Test void rotatesAcrossTheWholeParentTableAndKeepsReferencedColumnsTogether() {
        target.execute("CREATE TABLE parents(id BIGINT PRIMARY KEY,label VARCHAR(100))");
        target.execute("CREATE TABLE children(id BIGINT PRIMARY KEY,parent_id BIGINT REFERENCES parents(id),parent_label VARCHAR(100))");
        MetaTypeVO self = rule("REF.SELF_COLUMN"); self.setRefSelfColumn("id");
        TableVO parents = table("parents", 2301,
                column("id", "java.lang.Long", sequence()), column("label", "java.lang.String", literal("P"), self));
        TableVO children = table("children", 3207,
                column("id", "java.lang.Long", sequence()),
                column("parent_id", "java.lang.Long", reference("parents", "id")),
                column("parent_label", "java.lang.String", reference("parents", "label")));
        service.generateData(Arrays.asList(children, parents), monitor, executor);
        assertEquals(3207, target.queryForObject("SELECT COUNT(*) FROM children", Integer.class));
        assertEquals(0, target.queryForObject("SELECT COUNT(*) FROM children WHERE parent_id <> MOD(id-1,2301)+1 OR parent_label <> CONCAT('P',CAST(parent_id AS VARCHAR))", Integer.class));
    }
    @Test void treeRowsUseGlobalIndicesForExternalReferencesAndKeepTreeParents() {
        target.execute("CREATE TABLE parents(id BIGINT PRIMARY KEY)");
        target.execute("CREATE TABLE nodes(id BIGINT PRIMARY KEY,parent_id BIGINT REFERENCES nodes(id),external_id BIGINT REFERENCES parents(id))");
        TreeConfig config = new TreeConfig(); config.setNodeField("id"); config.setParentRefField("parent_id");
        config.setMinRoots(1); config.setMaxRoots(1); config.setMaxDepth(4);
        config.setMinChildrenPerNode(10); config.setMaxChildrenPerNode(10);
        TableVO nodes = table("nodes", 1111, column("id", "java.lang.Long", sequence()),
                column("parent_id", "java.lang.Long", rule("STRUCT.TREE_PARENT")),
                column("external_id", "java.lang.Long", reference("parents", "id")));
        nodes.setTreeConfig(config);
        service.generateData(Arrays.asList(nodes, table("parents", 2301, column("id", "java.lang.Long", sequence()))), monitor, executor);
        assertEquals(1111, target.queryForObject("SELECT COUNT(*) FROM nodes", Integer.class));
        assertEquals(0, target.queryForObject("SELECT COUNT(*) FROM nodes WHERE external_id <> id", Integer.class));
        assertEquals(0, target.queryForObject("SELECT COUNT(*) FROM nodes WHERE parent_id >= id", Integer.class));
        assertEquals(1, target.queryForObject("SELECT COUNT(*) FROM nodes WHERE parent_id IS NULL", Integer.class));
    }
    @Test void aFailureInTheSecondBatchRollsBackEveryTable() {
        target.execute("CREATE TABLE parents(id BIGINT PRIMARY KEY)");
        target.execute("CREATE TABLE children(id BIGINT PRIMARY KEY CHECK(id<=1001),parent_id BIGINT REFERENCES parents(id))");
        TableVO parents = table("parents", 1200, column("id", "java.lang.Long", sequence()));
        TableVO children = table("children", 1200, column("id", "java.lang.Long", sequence()),
                column("parent_id", "java.lang.Long", reference("parents", "id")));
        assertThrows(RuntimeException.class, () -> service.generateData(Arrays.asList(children, parents), monitor, executor));
        assertEquals(0, target.queryForObject("SELECT COUNT(*) FROM parents", Integer.class));
        assertEquals(0, target.queryForObject("SELECT COUNT(*) FROM children", Integer.class));
    }
    @Test void nestedConditionsNullAndConcatenationKeepExistingSemantics() {
        target.execute("CREATE TABLE samples(id BIGINT PRIMARY KEY,kind VARCHAR(20),result VARCHAR(20),empty_value VARCHAR(20))");
        MetaTypeVO inner = condition("#id == '2'", literal("second"), literal("other"));
        MetaTypeVO outer = condition("contains(#kind,'vip')", inner, literal("normal"));
        MetaTypeVO noValue = condition("#id == '1'", null, null);
        TableVO samples = table("samples", 3,
                column("result", "java.lang.String", literal("pre-"), outer),
                column("empty_value", "java.lang.String", noValue),
                column("kind", "java.lang.String", literal("vip")),
                column("id", "java.lang.Long", sequence()));
        service.generateData(Collections.singletonList(samples), monitor, executor);
        assertEquals(Arrays.asList("pre-other", "pre-second", "pre-other"), target.queryForList("SELECT result FROM samples ORDER BY id", String.class));
        assertEquals(3, target.queryForObject("SELECT COUNT(*) FROM samples WHERE empty_value IS NULL", Integer.class));
    }
    @Test void assignmentsInConditionsCannotLeakIntoTheNextRow() {
        target.execute("CREATE TABLE samples(id BIGINT PRIMARY KEY,result VARCHAR(20))");
        MetaTypeVO conditional = condition("#scratch == null and (#scratch = 'temporary') != null", literal("isolated"), literal("leaked"));
        service.generateData(Collections.singletonList(table("samples",1201,
                column("id","java.lang.Long",sequence()),column("result","java.lang.String",conditional))),monitor,executor);
        assertEquals(1201,target.queryForObject("SELECT COUNT(*) FROM samples WHERE result='isolated'",Integer.class));
    }
    @Test void zeroSizeTreeReportsProgressAgainstItsActualNodeCount() {
        TableVO nodes = treeTable("progress_nodes",0,4);
        service.generateData(Collections.singletonList(nodes),monitor,executor);
        assertEquals(1111,target.queryForObject("SELECT COUNT(*) FROM progress_nodes",Integer.class));
        assertProgress(1000D/1111*100,100D);
    }
    @Test void mixedTablesAndMultipleTreesShareAnAccurateProgressDenominator() {
        target.execute("CREATE TABLE ordinary(id BIGINT PRIMARY KEY)");
        TableVO first = treeTable("first_tree",0,4);
        TableVO second = treeTable("second_tree",9999,3);
        service.generateData(Arrays.asList(table("ordinary",1001,column("id","java.lang.Long",sequence())),first,second),monitor,executor);
        assertEquals(1111,target.queryForObject("SELECT COUNT(*) FROM first_tree",Integer.class));
        assertEquals(111,target.queryForObject("SELECT COUNT(*) FROM second_tree",Integer.class));
        assertProgress(1000D/2223*100,1001D/2223*100,2001D/2223*100,2112D/2223*100,100D);
    }
    private TableVO treeTable(String name,int requestedSize,int depth) {
        target.execute("CREATE TABLE "+name+"(id BIGINT PRIMARY KEY,parent_id BIGINT)");
        TableVO nodes=table(name,requestedSize,column("id","java.lang.Long",sequence()),
                column("parent_id","java.lang.Long",rule("STRUCT.TREE_PARENT")));
        TreeConfig config=new TreeConfig();config.setNodeField("id");config.setParentRefField("parent_id");
        config.setMinRoots(1);config.setMaxRoots(1);config.setMaxDepth(depth);
        config.setMinChildrenPerNode(10);config.setMaxChildrenPerNode(10);nodes.setTreeConfig(config);
        return nodes;
    }
    private void assertProgress(double... expected) {
        ArgumentCaptor<Double> captured=ArgumentCaptor.forClass(Double.class);
        verify(monitor,times(expected.length)).progressAndMessage(same(executor),captured.capture(),isNull());
        List<Double> actual=captured.getAllValues();
        for(int i=0;i<expected.length;i++)assertEquals(expected[i],actual.get(i),0.000001,"batch "+i);
    }
    @Test void concurrentTasksKeepRowValuesAndCountersIsolated() throws Exception {
        target.execute("CREATE TABLE samples_a(ID BIGINT PRIMARY KEY,Label VARCHAR(20),Copied VARCHAR(20),Result VARCHAR(20))");
        target.execute("CREATE TABLE samples_b(ID BIGINT PRIMARY KEY,Label VARCHAR(20),Copied VARCHAR(20),Result VARCHAR(20))");
        ExecutorService workers=Executors.newFixedThreadPool(2);
        String otherTask=UUID.randomUUID().toString();
        try {
            Future<?> first=workers.submit(()->runConcurrentTable("samples_a","Alpha",taskId));
            Future<?> second=workers.submit(()->runConcurrentTable("samples_b","Beta",otherTask));
            first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
            assertEquals(1201,target.queryForObject("SELECT COUNT(*) FROM samples_a WHERE Label='Alpha' AND Copied='A' AND Result='yes'",Integer.class));
            assertEquals(1201,target.queryForObject("SELECT COUNT(*) FROM samples_b WHERE Label='Beta' AND Copied='B' AND Result='no'",Integer.class));
            assertEquals(1201L,target.queryForObject("SELECT MAX(ID) FROM samples_b",Long.class));
        } finally {workers.shutdownNow();cache.removeTask(otherTask);IncrementNumRegMetaType.clearTask(otherTask);}
    }
    private void runConcurrentTable(String name,String label,String id) {
        AsyncTaskExecutor task=mock(AsyncTaskExecutor.class);when(task.getTaskId()).thenReturn(id);
        MetaTypeVO self=rule("REF.SELF_COLUMN");self.setRefSelfColumn("label");self.setRefSelfTransform("FIRST_CHAR");
        TableVO table=table(name,1201,column("Result","java.lang.String",condition("contains(#Label,'Alpha')",literal("yes"),literal("no"))),
                column("Copied","java.lang.String",self),column("Label","java.lang.String",literal(label)),column("ID","java.lang.Long",sequence()));
        service.generateData(Collections.singletonList(table),monitor,task);
    }
    static TableVO table(String name, int rows, ColumnInfo... columns) {
        TableVO t = new TableVO(); t.setName(name); t.setSize(rows); t.setColumns(Arrays.asList(columns)); return t;
    }
    static ColumnInfo column(String name, String type, MetaTypeVO... rules) {
        ColumnInfo c = new ColumnInfo(); c.setName(name); c.setJavaType(type); c.setNullable(1); c.setRegs(Arrays.asList(rules)); return c;
    }
    static MetaTypeVO rule(String code) { MetaTypeVO r = new MetaTypeVO(); r.setCode(code); return r; }
    static MetaTypeVO sequence() { MetaTypeVO r = rule("REG.INCREMENT_NUM"); r.setMinSize(1); r.setStep(1); return r; }
    static MetaTypeVO literal(String value) {
        MetaTypeVO r = rule("RES.INPUTAREA"); MetaTypeVO.InputItem item = new MetaTypeVO.InputItem(); item.setValue(value); item.setWeight(1);
        r.setInputs(Collections.singletonList(item)); return r;
    }
    static MetaTypeVO reference(String table, String column) {
        MetaTypeVO r = rule("REF.OTHER_TABLE"); r.setRefOtherTableColumn(new String[]{table,column}); return r;
    }
    static MetaTypeVO condition(String expression, MetaTypeVO yes, MetaTypeVO no) {
        MetaTypeVO r = rule("COND.EXPR"); r.setCondition(expression);
        r.setOnTrue(yes == null ? Collections.emptyList() : Collections.singletonList(yes));
        r.setOnFalse(no == null ? Collections.emptyList() : Collections.singletonList(no)); return r;
    }
}
