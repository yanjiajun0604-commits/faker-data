package faker.data.service.generate.engine;

import faker.data.cache.ReferenceCache;
import faker.data.domain.TableVO;
import faker.data.service.generate.tree.*;
import faker.data.task.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 编排生成、引用与批写入，整次任务仍由一个事务保护。 */
@Slf4j
@Component
public class GenerationEngine {
    private final ReferenceCache cache;
    public GenerationEngine(ReferenceCache cache) { this.cache=cache; }

    public void generate(DataSource target, List<TableVO> tables, AsyncTaskMonitor monitor, AsyncTaskExecutor executor) {
        List<TableGenerationPlan> plans=new ArrayList<>();
        for(TableVO table:new GenerationPlanner().plan(tables))plans.add(new TableGenerationPlan(table));
        String task=executor.getTaskId();
        // 只预存种子和实际行数；完整拓扑仍在执行到该表时才创建。
        Map<TableGenerationPlan,Long> treeSeeds=new IdentityHashMap<>();
        for(TableGenerationPlan plan:plans) {
            if(plan.table.getTreeConfig()!=null) {
                long seed=ThreadLocalRandom.current().nextLong();
                treeSeeds.put(plan,seed);
                plan.table.setSize(new TreeTopologyEngine(seed).count(plan.table.getTreeConfig()));
            }
        }
        long total=plans.stream().mapToLong(plan->plan.table.getSize()).sum();
        JdbcTemplate jdbc=new JdbcTemplate(target);
        TransactionTemplate transaction=new TransactionTemplate(new DataSourceTransactionManager(target));
        Map<String,List<String>> rowIds=new HashMap<>();
        transaction.executeWithoutResult(status -> {
            long completed=0;
            for(TableGenerationPlan plan:plans) {
                TableGenerationPlan.RowCursor row=plan.cursor(task,cache,rowIds);
                final long before=completed;
                if(plan.table.getTreeConfig()==null) {
                    plan.writer.write(jdbc,plan.table.getSize(),index->row.next(index,null,null),
                            count->progress(monitor,executor,before+count,total));
                } else {
                    List<TreeContext> topology=new TreeTopologyEngine(treeSeeds.get(plan)).compute(plan.table.getTreeConfig());
                    TreeState treeState=new TreeState();
                    String nodeField=plan.table.getTreeConfig().getNodeField().toLowerCase(Locale.ROOT);
                    plan.writer.write(jdbc,topology.size(),index->{
                        TreeContext node=topology.get(index);
                        Object[] values=row.next(index,node,treeState);
                        Object nodeValue=row.value(nodeField);
                        if(nodeValue!=null)treeState.put(index,nodeValue);
                        return values;
                    },count->progress(monitor,executor,before+count,total));
                }
                completed+=plan.table.getSize();
                log.info("{} 表数据生成完毕, 共 {} 条",plan.table.getName(),plan.table.getSize());
            }
            status.flush();
        });
    }
    private void progress(AsyncTaskMonitor monitor, AsyncTaskExecutor executor,long completed,long total) {
        String value=monitor.progressAndMessage(executor,total==0?0:completed*100D/total,null);
        log.info("执行进度: {}% , 已完成: {}条, 共计: {}条",value,completed,total);
    }
}
