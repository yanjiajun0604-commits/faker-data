package faker.data.service.generate.engine;

import faker.data.cache.ReferenceCache;
import faker.data.domain.TableVO;
import faker.data.service.generate.tree.*;
import faker.data.task.*;
import faker.data.extension.FieldExtensionService;
import faker.data.batch.GenerationBatchStore;
import faker.data.batch.GenerationBatchRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.core.ConnectionCallback;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 编排生成、引用与批写入，整次任务仍由一个事务保护。 */
@Slf4j
@Component
public class GenerationEngine {
    private final ReferenceCache cache;
    private final FieldExtensionService fields;
    private final GenerationBatchStore batches;
    public GenerationEngine(ReferenceCache cache) { this(cache,null,null); }
    public GenerationEngine(ReferenceCache cache,FieldExtensionService fields) { this(cache,fields,null); }
    @Autowired public GenerationEngine(ReferenceCache cache,FieldExtensionService fields,GenerationBatchStore batches) { this.cache=cache;this.fields=fields;this.batches=batches; }

    public void generate(DataSource target, List<TableVO> tables, AsyncTaskMonitor monitor, AsyncTaskExecutor executor) {
        GenerationValidator.structure(tables);
        List<TableGenerationPlan> plans=new ArrayList<>();
        for(TableVO table:new GenerationPlanner().plan(tables))plans.add(new TableGenerationPlan(table,fields!=null && fields.isEnabled()));
        Map<String,TableGenerationPlan> byName=new HashMap<>();
        for(TableGenerationPlan plan:plans)byName.put(plan.table.getName().toLowerCase(Locale.ROOT),plan);
        String task=executor.getTaskId();
        final boolean buffered=plans.stream().anyMatch(p->p.hasFieldExtensions);
        if(buffered && fields==null)throw new IllegalStateException("FIELD_EXTENSIONS_UNAVAILABLE");
        // 只预存种子和实际行数；完整拓扑仍在执行到该表时才创建。
        Map<TableGenerationPlan,Long> treeSeeds=new IdentityHashMap<>();
        for(TableGenerationPlan plan:plans) {
            if(plan.table.getTreeConfig()!=null) {
                long seed=ThreadLocalRandom.current().nextLong();
                treeSeeds.put(plan,seed);
                plan.table.setSize(new TreeTopologyEngine(seed).count(plan.table.getTreeConfig()));
            }
        }
        JdbcTemplate jdbc=new JdbcTemplate(target);
        TransactionTemplate transaction=new TransactionTemplate(new DataSourceTransactionManager(target));
        Map<String,List<String>> rowIds=new HashMap<>();
        Map<TableGenerationPlan,ExistingDataLoader> existing=new IdentityHashMap<>();
        Map<TableGenerationPlan,ParentRowsSelector> selectors=new IdentityHashMap<>();
        for(TableGenerationPlan plan:plans)if(plan.table.getExistingData()!=null)existing.put(plan,new ExistingDataLoader(plan.table));
        for(TableGenerationPlan plan:plans)if(plan.table.getParentRows()!=null) {
            TableVO parent=byName.get(plan.table.getParentRows().getTable().toLowerCase(Locale.ROOT)).table;
            selectors.put(plan,new ParentRowsSelector(parent,plan.table.getParentRows()));
        }
        transaction.executeWithoutResult(status -> {
            if(batches!=null) {
                List<TableVO> orderedTables=new ArrayList<>();for(TableGenerationPlan plan:plans)orderedTables.add(plan.table);
                jdbc.execute((ConnectionCallback<Void>)connection->{batches.begin(task,connection,orderedTables);return null;});
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                    @Override public void afterCompletion(int completion){batches.complete(task,completion);}
                });
            }
            Map<String,List<Map<String,Object>>> generated=new LinkedHashMap<>();
            long cells=0;
            long bufferedBytes=0;
            // 先固定全部候选数据，再生成目标行；读取失败时不会留下半成品。
            for(TableGenerationPlan plan:plans)if(existing.containsKey(plan))
                plan.table.setSize(existing.get(plan).load(jdbc,task,cache,rowIds));
            long total=0;
            for(TableGenerationPlan plan:plans)if(!existing.containsKey(plan)) {
                if(selectors.containsKey(plan)) {
                    TableVO parent=byName.get(plan.table.getParentRows().getTable().toLowerCase(Locale.ROOT)).table;
                    plan.table.setSize(Math.multiplyExact(parent.getSize(),plan.table.getParentRows().getRowsPerParent()));
                }
                total+=plan.table.getSize();
            }
            long completed=0;
            for(TableGenerationPlan plan:plans) {
                if(existing.containsKey(plan))continue;
                final List<String> parents=selectors.containsKey(plan)?selectors.get(plan).select(task,cache,rowIds):null;
                if(parents!=null) {
                    int actual=Math.multiplyExact(parents.size(),plan.table.getParentRows().getRowsPerParent());
                    total-=plan.table.getSize()-actual;
                    plan.table.setSize(actual);
                }
                TableGenerationPlan.RowCursor row=plan.cursor(task,cache,rowIds);
                final long before=completed;
                final long denominator=total;
                java.util.function.IntFunction<Object[]> source;
                if(plan.table.getTreeConfig()==null) {
                    source=index->row.next(index,null,null,parents==null?null:parents.get(index/plan.table.getParentRows().getRowsPerParent()));
                } else {
                    List<TreeContext> topology=new TreeTopologyEngine(treeSeeds.get(plan)).compute(plan.table.getTreeConfig());
                    TreeState treeState=new TreeState();
                    String nodeField=plan.table.getTreeConfig().getNodeField().toLowerCase(Locale.ROOT);
                    source=index->{
                        TreeContext node=topology.get(index);
                        Object[] values=row.next(index,node,treeState);
                        Object nodeValue=row.value(nodeField);
                        if(nodeValue!=null)treeState.put(index,nodeValue);
                        return values;
                    };
                }
                if(buffered) {
                    cells+=Math.multiplyExact((long)plan.table.getSize(),plan.table.getColumns().size());
                    if(total>100000 || cells>1000000)throw new IllegalArgumentException("FIELD_EXTENSION_BUFFER_LIMIT");
                    List<Map<String,Object>> records=new ArrayList<>();
                    for(int index=0;index<plan.table.getSize();index++) {
                        Object[] values=source.apply(index);Map<String,Object> record=new LinkedHashMap<>();
                        for(int c=0;c<values.length;c++) {
                            if(values[c] instanceof String)bufferedBytes+=((String)values[c]).length()*2L;
                            if(bufferedBytes>67108864)throw new IllegalArgumentException("FIELD_EXTENSION_BUFFER_LIMIT");
                            record.put(plan.table.getColumns().get(c).getName(),values[c]);
                        }
                        records.add(record);
                    }
                    generated.put(plan.table.getName(),records);
                } else {
                    plan.writer.write(jdbc,plan.table.getSize(),source,count->progress(monitor,executor,before+count,denominator),batches==null?null:new GenerationBatchRecorder(batches,task,plan.table.getName()));
                }
                completed+=plan.table.getSize();
                log.info("{} 表数据生成完毕, 共 {} 条",plan.table.getName(),plan.table.getSize());
            }
            if(buffered) {
                fields.resolve(task,generated);
                for(TableGenerationPlan plan:plans)if(!existing.containsKey(plan)) {
                    List<Map<String,Object>> records=generated.get(plan.table.getName());
                    plan.writer.write(jdbc,records.size(),index->{
                        Object[] values=new Object[plan.table.getColumns().size()];
                        for(int c=0;c<values.length;c++)values[c]=records.get(index).get(plan.table.getColumns().get(c).getName());
                        return values;
                    },count->{},batches==null?null:new GenerationBatchRecorder(batches,task,plan.table.getName()));
                }
            }
            status.flush();
            if(batches!=null)jdbc.execute((ConnectionCallback<Void>)connection->{batches.prepare(task,connection);return null;});
        });
        if(batches!=null)batches.requireCommitted(task);
        if(fields!=null) {
            List<Map<String,Object>> audit=new ArrayList<>();
            for(TableGenerationPlan plan:plans)if(!existing.containsKey(plan)) {
                Map<String,Object> item=new LinkedHashMap<>();item.put("table",plan.table.getName());item.put("rows",plan.table.getSize());item.put("ruleExecutions",plan.ruleUses);audit.add(item);
            }
            fields.audit(task,audit);
        }
    }
    private void progress(AsyncTaskMonitor monitor, AsyncTaskExecutor executor,long completed,long total) {
        String value=monitor.progressAndMessage(executor,total==0?0:completed*100D/total,null);
        log.info("执行进度: {}% , 已完成: {}条, 共计: {}条",value,completed,total);
    }
}
