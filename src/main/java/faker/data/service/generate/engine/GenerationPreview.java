package faker.data.service.generate.engine;

import faker.data.domain.*;
import faker.data.cache.ReferenceCache;
import faker.data.service.db.*;
import faker.data.service.generate.tree.*;
import faker.data.extension.FieldExtensionMetaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.Semaphore;

/** 独立只读编排：复用规则游标，但没有 INSERT、扩展执行或目标账本路径。 */
@Service
public class GenerationPreview {
    private final ReferenceCache cache;private final ObjectMapper json;private final Semaphore gate=new Semaphore(1);
    public GenerationPreview(ReferenceCache cache,ObjectMapper json){this.cache=cache;this.json=json;}
    public Map<String,Object> preview(GenerationPreviewRequest request){
        if(request.getRowsPerTable()==null || request.getRowsPerTable()<1 || request.getRowsPerTable()>20)throw new IllegalArgumentException("PREVIEW_ROWS_PER_TABLE_LIMIT");
        if(request.getTables()==null || request.getTables().isEmpty() || request.getTables().size()>20)throw new IllegalArgumentException("PREVIEW_TABLE_LIMIT");
        if(!gate.tryAcquire())throw new IllegalStateException("PREVIEW_BUSY");String task="preview-"+UUID.randomUUID();
        try{
            GenerateVO copy=json.convertValue(request,GenerateVO.class);List<TableVO> tables=copy.getTables();
            for(TableVO table:tables)if(table.getName()!=null && table.getName().contains("."))throw new IllegalArgumentException("PREVIEW_SELECTED_SCHEMA_ONLY");
            GenerationValidator.validateForPreview(tables);
            JdbcService service=JdbcServiceFactory.getJdbcService(request.getDataSource());Map<String,Map<String,Object>> metadata=new HashMap<>();
            Map<String,Object> checked=new GenerationPreflight(name->{DatabaseInspectionRequest query=new DatabaseInspectionRequest();query.setTable(name);Map<String,Object> result=service.inspectDatabase(query);metadata.put(name.toLowerCase(Locale.ROOT),result);return result;},service::readRows).check(tables,true);
            if(!Boolean.TRUE.equals(checked.get("valid")))return map("scope","GENERATED_SAMPLE_NO_TARGET_WRITES","sampleValid",false,"preflight",checked,"tables",Collections.emptyList());
            List<TableGenerationPlan> plans=new ArrayList<>();Map<String,TableGenerationPlan> byName=new HashMap<>();
            Map<String,Object> requested=new HashMap<>();
            for(TableVO table:new GenerationPlanner().plan(tables)){
                requested.put(table.getName(),table.getExistingData()!=null || table.getParentRows()!=null || table.getTreeConfig()!=null?null:table.getSize());
                if(table.getExistingData()!=null){table.getExistingData().setMaxRows(Math.min(table.getExistingData().getMaxRows(),Math.min(1000,10000/table.getColumns().size())));sourceBounds(table,metadata.get(table.getName().toLowerCase(Locale.ROOT)));}
                else if(table.getTreeConfig()==null && table.getParentRows()==null)table.setSize(Math.min(table.getSize(),request.getRowsPerTable()));
                TableGenerationPlan plan=new TableGenerationPlan(table,false,true);plans.add(plan);byName.put(table.getName().toLowerCase(Locale.ROOT),plan);
            }
            JdbcTemplate jdbc=new JdbcTemplate(service.getDrivierDatasource());TransactionTemplate read=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));read.setReadOnly(true);
            return read.execute(status->sample(jdbc,task,plans,byName,requested,request.getRowsPerTable(),checked));
        }finally{try{faker.data.service.generate.reg.IncrementNumRegMetaType.clearTask(task);cache.removeTask(task);}finally{gate.release();}}
    }
    @SuppressWarnings("unchecked") private void sourceBounds(TableVO table,Map<String,Object> metadata){
        if(table.getColumns().size()>50 || table.getExistingData().getMaxRows()<1)throw new IllegalArgumentException("PREVIEW_SOURCE_CELL_LIMIT");
        for(Map<String,Object> actual:(List<Map<String,Object>>)metadata.get("columns"))if(table.getColumns().stream().anyMatch(c->c.getName().equalsIgnoreCase((String)actual.get("name")))){
            int type=((Number)actual.get("jdbcType")).intValue();if(!Arrays.asList(java.sql.Types.CHAR,java.sql.Types.VARCHAR,java.sql.Types.NCHAR,java.sql.Types.NVARCHAR,java.sql.Types.TINYINT,java.sql.Types.SMALLINT,java.sql.Types.INTEGER,java.sql.Types.BIGINT,java.sql.Types.NUMERIC,java.sql.Types.DECIMAL,java.sql.Types.BOOLEAN,java.sql.Types.BIT,java.sql.Types.DATE,java.sql.Types.TIME,java.sql.Types.TIMESTAMP,java.sql.Types.FLOAT,java.sql.Types.DOUBLE,java.sql.Types.REAL).contains(type) || ((Number)actual.get("length")).longValue()>1024)throw new IllegalArgumentException("PREVIEW_SOURCE_VALUE_LIMIT");
        }
    }
    private Map<String,Object> sample(JdbcTemplate jdbc,String task,List<TableGenerationPlan> plans,Map<String,TableGenerationPlan> byName,Map<String,Object> requested,int limit,Map<String,Object> preflight){
        Map<String,List<String>> rowIds=new HashMap<>();List<Map<String,Object>> output=new ArrayList<>();long cells=0,characters=0;int generated=0,omitted=0;
        for(TableGenerationPlan plan:plans)if(plan.table.getExistingData()!=null){plan.table.setSize(new ExistingDataLoader(plan.table).load(jdbc,task,cache,rowIds));cells+=Math.multiplyExact((long)plan.table.getSize(),plan.table.getColumns().size());if(cells>50000)throw new IllegalArgumentException("PREVIEW_CELL_LIMIT");}
        for(TableGenerationPlan plan:plans){
            TableVO table=plan.table;if(table.getExistingData()!=null){output.add(map("name",table.getName(),"mode","READ_EXISTING","candidateRows",table.getSize()));continue;}
            List<String> parents=null;List<TreeContext> tree=null;TreeState state=new TreeState();
            if(table.getParentRows()!=null){TableVO parent=byName.get(table.getParentRows().getTable().toLowerCase(Locale.ROOT)).table;parents=new ParentRowsSelector(parent,table.getParentRows(),true).select(task,cache,rowIds);table.setSize(Math.multiplyExact(parents.size(),table.getParentRows().getRowsPerParent()));}
            if(table.getTreeConfig()!=null){long seed=java.util.concurrent.ThreadLocalRandom.current().nextLong();new TreeTopologyEngine(seed).count(table.getTreeConfig(),1000);tree=new TreeTopologyEngine(seed).compute(table.getTreeConfig());table.setSize(Math.min(tree.size(),limit));}
            generated+=table.getSize();cells+=Math.multiplyExact((long)table.getSize(),table.getColumns().size());if(generated>1000 || cells>50000)throw new IllegalArgumentException("PREVIEW_EXPANSION_OR_CELL_LIMIT");
            List<Map<String,Object>> columns=new ArrayList<>();for(ColumnInfo column:table.getColumns())columns.add(map("name",column.getName(),"virtual",column.isVirtual()));List<List<Object>> rows=new ArrayList<>();
            TableGenerationPlan.RowCursor cursor=plan.cursor(task,cache,rowIds);
            for(int index=0;index<table.getSize();index++){
                TreeContext node=tree==null?null:tree.get(index);Object[] values=cursor.next(index,node,node==null?null:state,parents==null?null:parents.get(index/table.getParentRows().getRowsPerParent()));
                if(node!=null){Object value=cursor.value(table.getTreeConfig().getNodeField().toLowerCase(Locale.ROOT));if(value!=null)state.put(index,value);}
                List<Object> visible=new ArrayList<>();
                for(int field=0;field<values.length;field++){Object value=values[field];String name=table.getColumns().get(field).getName();
                    if(value instanceof FieldExtensionMetaType.Request){value=map("omitted","EXTENSION_NOT_EXECUTED");omitted++;}
                    else if(name.toLowerCase(Locale.ROOT).matches("(?:.*_)?(?:password|passwd|pwd|secret|api_key|access_token|refresh_token)"))value=map("omitted","CREDENTIAL_REDACTED");
                    else if(value!=null){String text=value.toString();characters+=text.length();if(characters>2097152)throw new IllegalArgumentException("PREVIEW_OUTPUT_LIMIT");value=text.length()>512?map("text",text.substring(0,512),"truncated",true):text;}
                    visible.add(value);
                }rows.add(visible);
            }
            output.add(map("name",table.getName(),"mode","GENERATED_SAMPLE","requestedRows",requested.get(table.getName()),"sampledRows",table.getSize(),"columns",columns,"rows",rows));
        }
        return map("scope","GENERATED_SAMPLE_NO_TARGET_WRITES","sampleValid",true,"generatedRows",generated,"omittedExtensionFields",omitted,"tables",output,"preflight",preflight,"notChecked",Arrays.asList("Full-task random values and global uniqueness","JDBC conversions, database defaults, constraints, triggers and workflow execution","Extensions, ownership baselines and later business-created data"));
    }
    private static Map<String,Object> map(Object...pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
}
