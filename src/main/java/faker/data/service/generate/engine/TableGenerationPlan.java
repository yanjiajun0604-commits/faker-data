package faker.data.service.generate.engine;

import faker.data.cache.ReferenceCache;
import faker.data.domain.*;
import faker.data.service.generate.*;
import faker.data.service.generate.tree.*;
import faker.data.extension.FieldExtensionMetaType;
import org.springframework.expression.Expression;
import java.util.*;

/** 一个任务中的表执行计划；规则解析、列索引及关联键只准备一次。 */
final class TableGenerationPlan {
    final TableVO table;
    final JdbcBatchWriter writer;
    private final ColumnPlan[] columns;
    private final Map<String,Integer> indices = new HashMap<>();
    private final String lowerTable;
    final Map<String,Map<String,Long>> ruleUses=new LinkedHashMap<>();
    boolean hasFieldExtensions;
    private final boolean auditRules;
    private final boolean preview;
    private final boolean portable;

    TableGenerationPlan(TableVO table) {
        this(table,false);
    }
    TableGenerationPlan(TableVO table,boolean auditRules) {
        this(table,auditRules,false);
    }
    TableGenerationPlan(TableVO table,boolean auditRules,boolean preview) {
        this(table,auditRules,preview,false);
    }
    TableGenerationPlan(TableVO table,boolean auditRules,boolean preview,boolean portable) {
        this.portable=portable;
        this.preview=preview;
        this.auditRules=auditRules;
        this.table=table;
        lowerTable=table.getName().toLowerCase(Locale.ROOT);
        columns=new ColumnPlan[table.getColumns().size()];
        for(int i=0;i<columns.length;i++) {
            ColumnInfo c=table.getColumns().get(i);
            indices.put(c.getName().toLowerCase(Locale.ROOT),i);
        }
        for(int i=0;i<columns.length;i++)columns[i]=new ColumnPlan(table.getColumns().get(i));
        writer=table.getExistingData()==null?new JdbcBatchWriter(table.getName(),table.getColumns()):null;
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
            rules=prepareRules(source.getRegs(),name,"regs");
            rejectNestedExtensions(source.getRegs(),false);
            for(MetaTypeVO rule:source.getRegs()==null?Collections.<MetaTypeVO>emptyList():source.getRegs())if("EXT.FIELD".equals(rule.getCode())) {
                if(source.getRegs().size()!=1 || source.isDepended())throw new IllegalArgumentException("FIELD_EXTENSION_MUST_BE_STANDALONE_AND_NOT_REFERENCED");
                hasFieldExtensions=true;
            }
        }
    }

    private void rejectNestedExtensions(List<MetaTypeVO> rules,boolean nested) {
        if(rules==null)return;
        for(MetaTypeVO rule:rules) {
            if(nested && "EXT.FIELD".equals(rule.getCode()))throw new IllegalArgumentException("FIELD_EXTENSION_MUST_BE_STANDALONE_AND_NOT_REFERENCED");
            rejectNestedExtensions(rule.getOnTrue(),true);rejectNestedExtensions(rule.getOnFalse(),true);
        }
    }

    private PreparedRule[] prepareRules(List<MetaTypeVO> rules,String column,String path) {
        if(rules==null)return new PreparedRule[0];
        PreparedRule[] prepared=new PreparedRule[rules.size()];
        for(int i=0;i<prepared.length;i++){MetaTypeVO rule=rules.get(i);String location=path+"["+i+"]";try{prepared[i]=new PreparedRule(rule,column,location);}catch(RuntimeException error){throw GenerationProblem.at(error,table.getName(),column,location,rule==null?null:rule.getCode(),null,"INVALID_RULE_ARGUMENTS");}}
        return prepared;
    }

    private final class PreparedRule {
        final MetaTypeVO source;
        final IMetaType strategy;
        final Integer selfIndex;
        final String referenceTable;
        final ReferenceLookup lookup;
        final CondExprMetaType condition;
        final Expression expression;
        final PreparedRule[] yes,no;
        final String columnName,path;
        PreparedRule(MetaTypeVO source,String columnName,String path) {
            this.columnName=columnName;this.path=path;
            RuleArguments.check(source);
            this.source=source;
            strategy=MetaTypeFactory.getMetaTypeImpl(source);
            if(strategy==null)throw new IllegalArgumentException("不支持的生成规则："+source.getCode());
            if(preview)PreviewSafety.rule(source,strategy);
            selfIndex=source.getRefSelfColumn()==null?null:indices.get(source.getRefSelfColumn().toLowerCase(Locale.ROOT));
            if(source.getRefSelfColumn()!=null && selfIndex==null)throw new IllegalArgumentException("Unknown refSelfColumn: "+source.getRefSelfColumn());
            String[] ref=source.getRefOtherTableColumn();
            referenceTable=ref==null || ref.length==0?null:ref[0].toLowerCase(Locale.ROOT);
            lookup=source.getRefMatch()==null?null:new ReferenceLookup(source);
            condition=strategy instanceof CondExprMetaType?(CondExprMetaType)strategy:null;
            expression=condition==null?null:portable?new org.springframework.expression.spel.standard.SpelExpressionParser().parseExpression(source.getCondition()):preview?PreviewSafety.expression(source.getCondition()):condition.prepare(source);
            yes=condition==null?null:prepareRules(source.getOnTrue(),columnName,path+".onTrue");
            no=condition==null?null:prepareRules(source.getOnFalse(),columnName,path+".onFalse");
        }
        Object evaluate(RowCursor row, ColumnPlan column) {
            try{return evaluateValue(row,column);}catch(RuntimeException error){throw GenerationProblem.at(error,table.getName(),columnName,path,source.getCode(),row.index,"RULE_EXECUTION_FAILED");}
        }
        private Object evaluateValue(RowCursor row, ColumnPlan column) {
            if(auditRules)ruleUses.computeIfAbsent(column.name,k->new LinkedHashMap<>()).merge(source.getCode()==null?"EMPTY":source.getCode(),1L,Long::sum);
            Object self=selfIndex==null?null:row.generateColumn(selfIndex);
            // 递归同表引用会复用上下文，必须在递归返回后恢复当前规则参数。
            row.args.put("currentReg",source);
            if(source.getRefSelfColumn()!=null)row.args.put("refSelfValue",self);else row.args.remove("refSelfValue");
            if(referenceTable!=null) {
                List<String> ids=row.rowIds.get(referenceTable);
                if(ids==null)ids=Collections.emptyList();
                if(lookup==null && ids.isEmpty())throw new IllegalArgumentException("被引用表没有可用数据："+referenceTable);
                boolean boundParent=table.getParentRows()!=null && referenceTable.equals(table.getParentRows().getTable().toLowerCase(Locale.ROOT));
                String selected=lookup==null?(boundParent?row.parentRowId:ids.get(row.index%ids.size())):
                    lookup.select(row.task,row.cache,ids,name->row.generateColumn(indices.get(name.toLowerCase(Locale.ROOT))));
                row.args.put("refRowId",selected);
            } else row.args.remove("refRowId");
            if(condition!=null) {
                PreparedRule[] branch=(preview?PreviewSafety.evaluate(expression,row.values):condition.evaluate(expression,source,row.values))?yes:no;
                if(branch.length==0)return null;
                return concatenate(branch,row,column,false);
            }
            return strategy.parse(row.task,table.getName(),column.source,row.args);
        }
    }

    /** 保留既有字符串拼接语义；单规则不再分配 StringBuilder。 */
    private Object concatenate(PreparedRule[] rules, RowCursor row, ColumnPlan column, boolean emptyAsNull) {
        if(rules.length==0)return emptyAsNull?null:"";
        if(rules.length==1) {
            Object value=rules[0].evaluate(row,column);
            if(value instanceof FieldExtensionMetaType.Request)return value;
            String text=value==null?"":value.toString();
            return emptyAsNull && text.isEmpty()?null:text;
        }
        StringBuilder text=new StringBuilder();
        for(PreparedRule rule:rules) {
            Object value=rule.evaluate(row,column);
            if(value instanceof FieldExtensionMetaType.Request)throw new IllegalArgumentException("FIELD_EXTENSION_CANNOT_BE_CONCATENATED");
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
        String parentRowId;
        RowCursor(String task, ReferenceCache cache, Map<String,List<String>> rowIds) {
            this.task=task;this.cache=cache;this.rowIds=rowIds;
        }
        Object[] next(int index, TreeContext tree, TreeState treeState) {
            return next(index,tree,treeState,null);
        }
        Object[] next(int index, TreeContext tree, TreeState treeState,String parentRowId) {
            this.index=index;
            this.parentRowId=parentRowId;
            Arrays.fill(states,(byte)0);Arrays.fill(output,null);
            values.clear();args.clear();rowId=null;
            values.put("__rowIndex",index);
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
            Object value=concatenate(column.rules,this,column,true);
            output[position]=value;states[position]=2;
            values.put(column.name,value);
            if(!column.lowerName.equals(column.name))values.put(column.lowerName,value);
            if(column.source.isDepended())cache.put(task,column.cacheKey,rowId,value);
            return value;
        }
    }
}
