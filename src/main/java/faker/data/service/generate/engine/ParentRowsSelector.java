package faker.data.service.generate.engine;

import faker.data.cache.ReferenceCache;
import faker.data.domain.*;
import faker.data.service.generate.*;
import org.springframework.expression.Expression;
import java.util.*;

/** 仅保存选中的父行索引，不为展开后的每个子行复制完整记录。 */
final class ParentRowsSelector {
    private final TableVO parent;
    private final String key;
    private final CondExprMetaType evaluator;
    private final MetaTypeVO rule;
    private final Expression expression;
    private final boolean preview;

    ParentRowsSelector(TableVO parent,ParentRowsConfig config) {
        this(parent,config,false);
    }
    ParentRowsSelector(TableVO parent,ParentRowsConfig config,boolean preview) {
        this(parent,config,preview,false);
    }
    ParentRowsSelector(TableVO parent,ParentRowsConfig config,boolean preview,boolean portable) {
        this.preview=preview;
        this.parent=parent; key=parent.getName().toLowerCase(Locale.ROOT);
        if(config.getCondition()==null || config.getCondition().trim().isEmpty()) {
            evaluator=null;rule=null;expression=null;
        } else {
            rule=new MetaTypeVO();rule.setCode("COND.EXPR");rule.setCondition(config.getCondition());
            evaluator=(CondExprMetaType)MetaTypeFactory.getMetaTypeImpl(rule);
            if(evaluator==null)throw new IllegalArgumentException("Conditional rule implementation is unavailable");
            expression=portable?new org.springframework.expression.spel.standard.SpelExpressionParser().parseExpression(config.getCondition()):preview?PreviewSafety.expression(config.getCondition()):evaluator.prepare(rule);
        }
    }

    List<String> select(String task,ReferenceCache cache,Map<String,List<String>> rowIds) {
        List<String> source=rowIds.getOrDefault(key,Collections.emptyList());
        if(expression==null)return source;
        List<String> selected=new ArrayList<>();
        Map<String,Object> values=new HashMap<>();
        for(String id:source) {
            values.clear();
            for(ColumnInfo column:parent.getColumns())values.put(column.getName(),cache.get(task,key+":"+column.getName().toLowerCase(Locale.ROOT),id));
            if(preview?PreviewSafety.evaluate(expression,values):evaluator.evaluate(expression,rule,values))selected.add(id);
        }
        return selected;
    }
}
