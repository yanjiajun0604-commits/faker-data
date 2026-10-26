package faker.data.service.generate.engine;

import faker.data.domain.*;
import java.util.*;

/** 只检查配置，不连接目标库、不生成值，也不调用扩展。 */
public final class GenerationValidator {
    private GenerationValidator(){}
    public static void structure(List<TableVO> tables) {
        if(tables==null || tables.isEmpty())throw new IllegalArgumentException("tables must not be empty");
        Set<String> names=new HashSet<>();
        for(TableVO table:tables) {
            if(table==null)throw new IllegalArgumentException("Null table");
            identifier(table.getName(),true);
            if(!names.add(table.getName().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate table: "+table.getName());
            if(table.getExistingData()==null && table.getParentRows()==null && table.getTreeConfig()==null && (table.getSize()==null || table.getSize()<0))
                throw new IllegalArgumentException("Generated table requires a non-negative size: "+table.getName());
            if(table.getColumns()==null || table.getColumns().isEmpty())throw new IllegalArgumentException("Table requires columns: "+table.getName());
            Set<String> columns=new HashSet<>();
            for(ColumnInfo column:table.getColumns()) {
                if(column==null)throw new IllegalArgumentException("Null column");
                identifier(column.getName(),false);
                if(!columns.add(column.getName().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate column: "+column.getName());
                if(column.getJavaType()==null || column.getJavaType().trim().isEmpty())throw new IllegalArgumentException("Column requires javaType: "+column.getName());
            }
        }
        OwnershipConfiguration.validate(tables);
    }
    private static void identifier(String name,boolean qualified) {
        String part="[\\p{L}_][\\p{L}\\p{N}_]*";
        if(name==null || !name.matches(qualified?part+"(?:\\."+part+")*":part))throw new IllegalArgumentException("Invalid SQL identifier");
    }
    public static Map<String,Object> validate(List<TableVO> tables) {
        structure(tables);
        List<TableVO> ordered=new GenerationPlanner().plan(tables);
        List<Map<String,Object>> summary=new ArrayList<>();
        int fields=0;boolean extensions=false;
        for(TableVO table:ordered) {
            TableGenerationPlan plan=new TableGenerationPlan(table);
            if(table.getExistingData()!=null)new ExistingDataLoader(table);
            if(table.getParentRows()!=null) {
                TableVO parent=ordered.stream().filter(t->t.getName().equalsIgnoreCase(table.getParentRows().getTable())).findFirst().get();
                new ParentRowsSelector(parent,table.getParentRows());
            }
            if(table.getTreeConfig()!=null)new faker.data.service.generate.tree.TreeTopologyEngine(0L).count(table.getTreeConfig());
            Map<String,Object> item=new LinkedHashMap<>();item.put("table",table.getName());
            item.put("mode",table.getExistingData()!=null?"READ_EXISTING":"GENERATE");
            item.put("requestedRows",table.getParentRows()!=null || table.getExistingData()!=null || table.getTreeConfig()!=null?null:table.getSize());
            item.put("columns",table.getColumns().size());item.put("fieldExtensions",plan.hasFieldExtensions);summary.add(item);
            fields+=table.getColumns().size();extensions|=plan.hasFieldExtensions;
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("valid",true);result.put("scope","CONFIGURATION_ONLY");
        result.put("tables",summary);result.put("columnCount",fields);result.put("usesFieldExtensions",extensions);
        result.put("notChecked",Arrays.asList("Database connectivity, schema, constraints and existing candidates","Extension installation and execution","Business workflow validity"));
        return result;
    }
}
