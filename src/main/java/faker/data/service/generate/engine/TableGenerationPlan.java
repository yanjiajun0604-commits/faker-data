package faker.data.service.generate.engine;

import faker.data.cache.ReferenceCache;
import faker.data.domain.*;
import faker.data.service.generate.*;
import faker.data.service.generate.tree.*;
import org.springframework.expression.Expression;
import java.util.*;

/** 一个任务中的表执行计划；规则解析、列索引及关联键只准备一次。 */
final class TableGenerationPlan {
    final TableVO table;
    final JdbcBatchWriter writer;
    private final ColumnPlan[] columns;
    private final Map<String,Integer> indices = new HashMap<>();
    private final String lowerTable;

    TableGenerationPlan(TableVO table) {
        this.table=table;
        lowerTable=table.getName().toLowerCase(Locale.ROOT);
        columns=new ColumnPlan[table.getColumns().size()];
        for(int i=0;i<columns.length;i++) {
            ColumnInfo c=table.getColumns().get(i);
            indices.put(c.getName().toLowerCase(Locale.ROOT),i);
        }
        for(int i=0;i<columns.length;i++)columns[i]=new ColumnPlan(table.getColumns().get(i));
        writer=new JdbcBatchWriter(table.getName(),table.getColumns());
    }

    RowCursor cursor(String task, ReferenceCache cache, Map<String,List<String>> rowIds) {
        return new RowCursor(task,cache,rowIds);
    }

    private final class ColumnPlan {
        final ColumnInfo source;
        final String name,lowerName,cacheKey;
        final PreparedRule[] rules;
        ColumnPlan(ColumnInfo source) {
            this.source=source;name=source.getName();lowerName=name.toLowerCase(Locale.ROOT);
            cacheKey=lowerTable+":"+lowerName;
            rules=prepareRules(source.getRegs());
        }
    }

    private PreparedRule[] prepareRules(List<MetaTypeVO> rules) {
        if(rules==null)return new PreparedRule[0];
        PreparedRule[] prepared=new PreparedRule[rules.size()];
        for(int i=0;i<prepared.length;i++)prepared[i]=new PreparedRule(rules.get(i));
        return prepared;
    }

    private final class PreparedRule {
        final MetaTypeVO source;
        final IMetaType strategy;
        final Integer selfIndex;
        final String referenceTable;
        final CondExprMetaType condition;
        final Expression expression;
        final PreparedRule[] yes,no;
        PreparedRule(MetaTypeVO source) {
            this.source=source;
            strategy=MetaTypeFactory.getMetaTypeImpl(source);
            if(strategy==null)throw new IllegalArgumentException("不支持的生成规则："+source.getCode());
            selfIndex=source.getRefSelfColumn()==null?null:indices.get(source.getRefSelfColumn().toLowerCase(Locale.ROOT));
            String[] ref=source.getRefOtherTableColumn();
            referenceTable=ref==null || ref.length==0?null:ref[0].toLowerCase(Locale.ROOT);
            condition=strategy instanceof CondExprMetaType?(CondExprMetaType)strategy:null;
            expression=condition==null?null:condition.prepare(source);
            yes=condition==null?null:prepareRules(source.getOnTrue());
            no=condition==null?null:prepareRules(source.getOnFalse());
        }
        Object evaluate(RowCursor row, ColumnPlan column) {
            Object self=selfIndex==null?null:row.generateColumn(selfIndex);
            // 递归同表引用会复用上下文，必须在递归返回后恢复当前规则参数。
            row.args.put("currentReg",source);
            if(source.getRefSelfColumn()!=null)row.args.put("refSelfValue",self);else row.args.remove("refSelfValue");
            if(referenceTable!=null) {
                List<String> ids=row.rowIds.get(referenceTable);
                if(ids==null || ids.isEmpty())throw new IllegalArgumentException("被引用表没有可用数据："+referenceTable);
                row.args.put("refRowId",ids.get(row.index%ids.size()));
            } else row.args.remove("refRowId");
            if(condition!=null) {
                PreparedRule[] branch=condition.evaluate(expression,source,row.values)?yes:no;
                if(branch.length==0)return null;
                return concatenate(branch,row,column,false);
            }
            return strategy.parse(row.task,table.getName(),column.source,row.args);
        }
    }

    /** 保留既有字符串拼接语义；单规则不再分配 StringBuilder。 */
    private String concatenate(PreparedRule[] rules, RowCursor row, ColumnPlan column, boolean emptyAsNull) {
        if(rules.length==0)return emptyAsNull?null:"";
        if(rules.length==1) {
            Object value=rules[0].evaluate(row,column);
            String text=value==null?"":value.toString();
            return emptyAsNull && text.isEmpty()?null:text;
        }
        StringBuilder text=new StringBuilder();
        for(PreparedRule rule:rules) {
            Object value=rule.evaluate(row,column);
            if(value!=null)text.append(value);
        }
        return emptyAsNull && text.length()==0?null:text.toString();
    }

    /** 每张表、每个任务独立使用；同步消费一行后即可复用容器。 */
    final class RowCursor {
        final String task;
        final ReferenceCache cache;
        final Map<String,List<String>> rowIds;
        final Map<String,Object> values=new HashMap<>(Math.max(16,columns.length*3));
        final Map<String,Object> args=new HashMap<>(16);
        final Object[] output=new Object[columns.length];
        final byte[] states=new byte[columns.length];
        int index;
        String rowId;
        RowCursor(String task, ReferenceCache cache, Map<String,List<String>> rowIds) {
            this.task=task;this.cache=cache;this.rowIds=rowIds;
        }
        Object[] next(int index, TreeContext tree, TreeState treeState) {
            this.index=index;
            Arrays.fill(states,(byte)0);Arrays.fill(output,null);
            values.clear();args.clear();rowId=null;
            if(!table.getRefOtherTables().isEmpty()) {
                rowId=UUID.randomUUID().toString();
                rowIds.computeIfAbsent(lowerTable,k->new ArrayList<>()).add(rowId);
            }
            args.put("rowId",rowId);args.put("rowValues",values);
            if(tree!=null) {
                args.put("treeContext",tree);args.put("treeState",treeState);
                args.put("parentValue",tree.getParentIndex()<0?null:treeState.get(tree.getParentIndex()));
            }
            for(int i=0;i<columns.length;i++)generateColumn(i);
            return output;
        }
        Object value(String name) { return values.get(name); }
        private Object generateColumn(int position) {
            if(states[position]==2)return output[position];
            if(states[position]==1)throw new IllegalArgumentException("同表字段存在循环引用："+columns[position].name);
            states[position]=1;
            ColumnPlan column=columns[position];
            String value=concatenate(column.rules,this,column,true);
            output[position]=value;states[position]=2;
            values.put(column.name,value);
            if(!column.lowerName.equals(column.name))values.put(column.lowerName,value);
            if(column.source.isDepended())cache.put(task,column.cacheKey,rowId,value);
            return value;
        }
    }
}
