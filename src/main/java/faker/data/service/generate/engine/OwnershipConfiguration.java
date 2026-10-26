package faker.data.service.generate.engine;

import faker.data.domain.*;
import java.util.*;

/** 归属声明只描述关系与边界，不执行字段规则或目标数据库查询。 */
public final class OwnershipConfiguration {
    private OwnershipConfiguration(){}
    public static boolean enabled(List<TableVO> tables){return tables.stream().anyMatch(t->t.getOwnership()!=null && ((t.getOwnership().getMutableColumns()!=null && !t.getOwnership().getMutableColumns().isEmpty()) || (t.getOwnership().getDerivedRows()!=null && !t.getOwnership().getDerivedRows().isEmpty())));}
    public static void validate(List<TableVO> tables){
        Map<String,TableVO> roots=new HashMap<>();for(TableVO t:tables)roots.put(lower(t.getName()),t);
        Set<String> declared=new HashSet<>();Map<String,Set<String>> graph=new LinkedHashMap<>();int[] count={0};
        for(TableVO table:tables)if(table.getOwnership()!=null){
            identifier(table.getName());if(table.getExistingData()!=null)throw new IllegalArgumentException("OWNERSHIP_READONLY_SOURCE_UNSUPPORTED");
            OwnershipConfig policy=table.getOwnership();Set<String> fields=new HashSet<>();
            if(policy.getMutableColumns()!=null){if(policy.getMutableColumns().size()>50)throw new IllegalArgumentException("OWNERSHIP_MUTABLE_COLUMN_LIMIT");for(String field:policy.getMutableColumns()){identifier(field);if(!fields.add(lower(field)))throw new IllegalArgumentException("OWNERSHIP_DUPLICATE_MUTABLE_COLUMN");}}
            visit(table.getName(),policy.getDerivedRows(),1,roots,declared,graph,count);
        }
        Set<String> done=new HashSet<>(),active=new HashSet<>();for(String name:graph.keySet())acyclic(name,graph,done,active);
    }
    private static void visit(String parent,List<DerivedRowsConfig> rows,int depth,Map<String,TableVO> roots,Set<String> declared,Map<String,Set<String>> graph,int[] count){
        if(rows==null)return;
        for(DerivedRowsConfig row:rows){
            if(row==null || depth>5 || ++count[0]>20)throw new IllegalArgumentException("OWNERSHIP_RELATION_LIMIT");identifier(row.getTable());
            if(!declared.add(lower(row.getTable())))throw new IllegalArgumentException("OWNERSHIP_AMBIGUOUS_DERIVED_TABLE");
            TableVO source=roots.get(lower(row.getTable()));if(source!=null && source.getExistingData()!=null)throw new IllegalArgumentException("OWNERSHIP_READONLY_SOURCE_UNSUPPORTED");
            if(row.getJoin()==null || row.getJoin().isEmpty() || row.getJoin().size()>32)throw new IllegalArgumentException("OWNERSHIP_JOIN_REQUIRED");
            Set<String> child=new HashSet<>(),parents=new HashSet<>();for(Map.Entry<String,String> join:row.getJoin().entrySet()){identifier(join.getKey());identifier(join.getValue());if(!child.add(lower(join.getKey())) || !parents.add(lower(join.getValue())))throw new IllegalArgumentException("OWNERSHIP_AMBIGUOUS_JOIN");}
            if(row.getWhere()!=null){if(row.getWhere().size()>20)throw new IllegalArgumentException("OWNERSHIP_PREDICATE_LIMIT");for(Map.Entry<String,Object> predicate:row.getWhere().entrySet()){identifier(predicate.getKey());Object value=predicate.getValue();if(value!=null && (!(value instanceof String || value instanceof Number || value instanceof Boolean) || value.toString().length()>2048 || value instanceof Double && !Double.isFinite((Double)value) || value instanceof Float && !Float.isFinite((Float)value)))throw new IllegalArgumentException("OWNERSHIP_SCALAR_PREDICATE_REQUIRED");}}
            String mode=row.getFreshness()==null?"AUTO":row.getFreshness().toUpperCase(Locale.ROOT);if(!Arrays.asList("AUTO","FULL_KEYS","NUMERIC_HIGH_WATERMARK").contains(mode))throw new IllegalArgumentException("OWNERSHIP_FRESHNESS_UNSUPPORTED");
            if(row.getMaxRows()!=null && (row.getMaxRows()<1 || row.getMaxRows()>100000) || row.getBaselineLimit()!=null && (row.getBaselineLimit()<1 || row.getBaselineLimit()>100000))throw new IllegalArgumentException("OWNERSHIP_ROW_LIMIT");
            graph.computeIfAbsent(lower(parent),k->new LinkedHashSet<>()).add(lower(row.getTable()));visit(row.getTable(),row.getDerivedRows(),depth+1,roots,declared,graph,count);
        }
    }
    private static void acyclic(String name,Map<String,Set<String>> graph,Set<String> done,Set<String> active){if(done.contains(name))return;if(!active.add(name))throw new IllegalArgumentException("OWNERSHIP_CYCLIC_GRAPH");for(String child:graph.getOrDefault(name,Collections.emptySet()))acyclic(child,graph,done,active);active.remove(name);done.add(name);}
    private static void identifier(String value){if(value==null || value.length()>128 || !value.matches("[\\p{L}_][\\p{L}\\p{N}_$#]*"))throw new IllegalArgumentException("OWNERSHIP_UNQUALIFIED_IDENTIFIER_REQUIRED");}
    private static String lower(String text){return text.toLowerCase(Locale.ROOT);}
}
