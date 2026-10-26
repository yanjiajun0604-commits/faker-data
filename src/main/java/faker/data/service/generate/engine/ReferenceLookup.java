package faker.data.service.generate.engine;

import faker.data.cache.ReferenceCache;
import faker.data.service.generate.MetaTypeVO;
import java.util.*;
import java.util.function.Function;

/** 对任务内候选快照建立等值索引，避免每个目标行遍历整个来源表。 */
final class ReferenceLookup {
    private final String table;
    private final List<String> sourceColumns=new ArrayList<>();
    private final List<Map<String,Object>> bindings=new ArrayList<>();
    private final String missing,multiple;
    private Map<List<String>,List<String>> index;

    ReferenceLookup(MetaTypeVO rule) {
        table=rule.getRefOtherTableColumn()[0].toLowerCase(Locale.ROOT);
        missing=rule.getRefOnMissing()==null?"ERROR":rule.getRefOnMissing();
        multiple=rule.getRefOnMultiple()==null?"ERROR":rule.getRefOnMultiple();
        if(!Arrays.asList("ERROR","NULL").contains(missing) || !Arrays.asList("ERROR","FIRST").contains(multiple))
            throw new IllegalArgumentException("Invalid refOnMissing/refOnMultiple policy");
        if(rule.getRefMatch()==null || rule.getRefMatch().isEmpty())throw new IllegalArgumentException("refMatch requires at least one predicate");
        rule.getRefMatch().forEach((column,binding)->{
            if(column==null || column.isEmpty() || binding==null || binding.size()!=1 || !(binding.containsKey("field") || binding.containsKey("value")))
                throw new IllegalArgumentException("refMatch requires exactly one field or value per predicate");
            Object value=binding.values().iterator().next();
            if(binding.containsKey("field") && (!(value instanceof String) || ((String)value).trim().isEmpty()))
                throw new IllegalArgumentException("refMatch.field requires a target column name");
            if(binding.containsKey("value") && value!=null && !(value instanceof String || value instanceof Number || value instanceof Boolean))
                throw new IllegalArgumentException("refMatch.value requires a scalar or null");
            sourceColumns.add(column.toLowerCase(Locale.ROOT));bindings.add(binding);
        });
    }

    String select(String task,ReferenceCache cache,List<String> ids,Function<String,Object> current) {
        if(index==null) {
            index=new HashMap<>();
            for(String id:ids) {
                List<String> key=new ArrayList<>(sourceColumns.size());
                for(String column:sourceColumns)key.add(text(cache.get(task,table+":"+column,id)));
                index.computeIfAbsent(key,k->new ArrayList<>()).add(id);
            }
        }
        List<String> key=new ArrayList<>(bindings.size());
        for(Map<String,Object> binding:bindings)
            key.add(text(binding.containsKey("field")?current.apply((String)binding.get("field")):binding.get("value")));
        List<String> matches=index.getOrDefault(key,Collections.emptyList());
        if(matches.isEmpty()) {
            if("NULL".equals(missing))return null;
            throw new IllegalArgumentException("REF_MATCH_NONE: "+table);
        }
        if(matches.size()>1 && "ERROR".equals(multiple))throw new IllegalArgumentException("REF_MATCH_MULTIPLE: "+table);
        return matches.get(0);
    }
    private String text(Object value){return value==null?null:value.toString();}
}
