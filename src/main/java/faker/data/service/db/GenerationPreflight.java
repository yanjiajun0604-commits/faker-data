package faker.data.service.db;

import faker.data.domain.*;
import faker.data.service.generate.MetaTypeVO;
import faker.data.service.generate.engine.GenerationValidator;
import java.math.*;
import java.sql.Types;
import java.util.*;
import java.util.function.Function;

/** 只检查可由元数据和固定候选证明的错误，不执行生成规则或扩展。 */
public final class GenerationPreflight {
    private final Function<String,Map<String,Object>> describe;
    private final Function<DatabaseInspectionRequest,Map<String,Object>> read;
    public GenerationPreflight(Function<String,Map<String,Object>> describe,Function<DatabaseInspectionRequest,Map<String,Object>> read) {
        this.describe=describe;this.read=read;
    }

    public Map<String,Object> check(List<TableVO> tables) {
        return check(tables,false);
    }
    public Map<String,Object> check(List<TableVO> tables,boolean preview) {
        if(preview)GenerationValidator.validateForPreview(tables);else GenerationValidator.validate(tables);
        List<Map<String,Object>> errors=new ArrayList<>(),warnings=new ArrayList<>(),summary=new ArrayList<>();
        for(TableVO table:tables)checkTable(table,errors,warnings,summary);
        return map("valid",errors.isEmpty(),"scope","DATABASE_METADATA_AND_STATIC_VALUES","errors",errors,"warnings",warnings,"tables",summary,
            "notChecked",Arrays.asList("Dynamic/random/conditional/reference/extension values are not executed or exhaustively validated",
                "CHECK constraints, triggers, all candidate availability and application workflow semantics",
                "Concurrent changes after preflight; the write transaction and database constraints remain authoritative"));
    }

    @SuppressWarnings("unchecked") private void checkTable(TableVO table,List<Map<String,Object>> errors,List<Map<String,Object>> warnings,List<Map<String,Object>> summary) {
        int previousErrors=errors.size();
        if(table.getName().contains(".")) {
            issue(warnings,"QUALIFIED_TARGET_NOT_CHECKED",table.getName(),null,"This preflight inspects the selected database/schema only; qualified generation targets require separate validation");return;
        }
        Map<String,Object> metadata;
        try {metadata=describe.apply(table.getName());}
        catch(IllegalArgumentException e) {
            issue(errors,e.getMessage()!=null && e.getMessage().startsWith("Table not found")?"TABLE_NOT_FOUND":"UNSUPPORTED_TARGET_METADATA",table.getName(),null,"Target table could not be resolved in the selected database/schema");return;
        }
        List<Map<String,Object>> cols=(List<Map<String,Object>>)metadata.get("columns");
        Map<String,Map<String,Object>> actual=new LinkedHashMap<>();
        for(Map<String,Object> col:cols)actual.put(lower(col.get("name")),col);
        Map<String,ColumnInfo> supplied=new LinkedHashMap<>();
        for(ColumnInfo col:table.getColumns()) {
            if(col.isVirtual()){if(actual.containsKey(lower(col.getName())))issue(errors,"VIRTUAL_SHADOWS_DATABASE_COLUMN",table.getName(),col.getName(),"A virtual field must use a distinct logical name, not hide a real target column");continue;}
            supplied.put(lower(col.getName()),col);
            if(!actual.containsKey(lower(col.getName())))issue(errors,"COLUMN_NOT_FOUND",table.getName(),col.getName(),"Configured column does not exist in the target table");
        }
        boolean existing=table.getExistingData()!=null;
        List<String> virtual=new ArrayList<>();for(ColumnInfo col:table.getColumns())if(col.isVirtual())virtual.add(col.getName());
        summary.add(map("table",table.getName(),"mode",existing?"READ_EXISTING":"GENERATE","databaseColumns",cols.size(),"virtualColumns",virtual,"primaryKeys",metadata.get("primaryKeys"),"indexes",metadata.get("indexes")));
        if(existing)return;
        boolean writes=table.getSize()==null || table.getSize()>0 || table.getParentRows()!=null || table.getTreeConfig()!=null;
        if(!writes)return;
        if(Boolean.FALSE.equals(metadata.get("transactionalWritesSupported")))issue(errors,"NON_TRANSACTIONAL_TARGET",table.getName(),null,"Target writes require transactional tables; this version supports MySQL InnoDB writes only");
        Map<String,StaticValue> constants=new HashMap<>();
        for(Map<String,Object> col:cols) {
            String name=(String)col.get("name");ColumnInfo configured=supplied.get(lower(name));
            boolean automatic=yes(col.get("autoIncrement")),generated=yes(col.get("generated"));
            if(configured==null) {
                if(number(col,"nullable")==0 && !Boolean.TRUE.equals(col.get("hasDefault")) && !automatic && !generated)
                    issue(errors,"REQUIRED_COLUMN_MISSING",table.getName(),name,"Required target column is omitted and has no reported default");
                continue;
            }
            if(generated){issue(errors,"GENERATED_COLUMN_WRITE",table.getName(),name,"Omit a database-generated column from the insert configuration");continue;}
            if(automatic)issue(warnings,"AUTO_INCREMENT_COLUMN_SUPPLIED",table.getName(),name,"Explicit auto-increment values have database-specific semantics; omit the column to use its default");
            List<StaticValue> candidates=candidates(configured);
            if(candidates==null)continue;
            if(candidates.size()==1)constants.put(lower(name),candidates.get(0));
            for(StaticValue value:candidates)checkValue(table.getName(),configured,col,value,errors,warnings,automatic);
        }
        if(errors.size()==previousErrors)checkUnique(table,metadata,actual,constants,errors,warnings);
    }

    private void checkValue(String table,ColumnInfo configured,Map<String,Object> col,StaticValue value,List<Map<String,Object>> errors,List<Map<String,Object>> warnings,boolean automatic) {
        String name=configured.getName();String raw=value.text;
        if(raw==null && "java.lang.String".equals(configured.getJavaType()) && Integer.valueOf(0).equals(configured.getNullable())) {
            issue(warnings,"EMPTY_STRING_COERCION",table,name,"The current required-String binder converts a null generated value to an empty string");raw="";
        }
        if(raw==null) {
            if(number(col,"nullable")==0 && !automatic)issue(errors,"NULL_NOT_ALLOWED",table,name,"A static candidate generates NULL for a required target column");
            return;
        }
        int type=number(col,"jdbcType");
        if(Arrays.asList(Types.CHAR,Types.VARCHAR,Types.NCHAR,Types.NVARCHAR).contains(type) && raw.codePointCount(0,raw.length())>number(col,"length"))
            issue(errors,"TEXT_TOO_LONG",table,name,"A static candidate exceeds the target character length");
        try {
            if(Arrays.asList(Types.TINYINT,Types.SMALLINT,Types.INTEGER,Types.BIGINT).contains(type)) {
                BigInteger n=new BigDecimal(raw).toBigIntegerExact();
                int bits=type==Types.TINYINT?8:type==Types.SMALLINT?16:type==Types.INTEGER?32:64;
                // Unsigned metadata varies by driver; only claim the signed check when the DB type is signed.
                boolean unsigned=String.valueOf(col.get("dbType")).toUpperCase(Locale.ROOT).contains("UNSIGNED");
                BigInteger min=unsigned?BigInteger.ZERO:BigInteger.ONE.shiftLeft(bits-1).negate();
                BigInteger max=BigInteger.ONE.shiftLeft(unsigned?bits:bits-1).subtract(BigInteger.ONE);
                if(n.compareTo(min)<0 || n.compareTo(max)>0)issue(errors,"INTEGER_RANGE",table,name,"A static candidate exceeds the target integer range");
            }
            if(type==Types.DECIMAL || type==Types.NUMERIC) {
                BigDecimal n=new BigDecimal(raw);int scale=number(col,"scale"),precision=number(col,"length");
                if(n.stripTrailingZeros().scale()>scale)issue(errors,"DECIMAL_SCALE",table,name,"A static candidate would lose decimal places at the target scale");
                if(precision>0 && Math.max(0,n.precision()-n.scale())>precision-scale)issue(errors,"DECIMAL_PRECISION",table,name,"A static candidate exceeds target decimal precision");
            }
        } catch(NumberFormatException|ArithmeticException invalid) {
            issue(errors,"INVALID_STATIC_VALUE",table,name,"A static candidate cannot be represented by the target numeric type");
        }
    }

    @SuppressWarnings("unchecked") private void checkUnique(TableVO table,Map<String,Object> metadata,Map<String,Map<String,Object>> actual,Map<String,StaticValue> constants,List<Map<String,Object>> errors,List<Map<String,Object>> warnings) {
        Map<String,List<Map<String,Object>>> groups=new LinkedHashMap<>();
        for(Map<String,Object> index:(List<Map<String,Object>>)metadata.get("indexes"))if(Boolean.TRUE.equals(index.get("unique")))
            groups.computeIfAbsent(String.valueOf(index.get("name")),k->new ArrayList<>()).add(index);
        for(List<Map<String,Object>> parts:groups.values()) {
            parts.sort(Comparator.comparingInt(p->number(p,"sequence")));
            List<DatabaseInspectionRequest.Filter> filters=new ArrayList<>();boolean complete=true;
            for(Map<String,Object> part:parts) {
                String name=(String)part.get("column");StaticValue value=constants.get(lower(name));
                if(name==null || value==null || value.text==null || value.text.length()>2048 || credential(name) || !actual.containsKey(lower(name))) {complete=false;break;}
                DatabaseInspectionRequest.Filter f=new DatabaseInspectionRequest.Filter();f.setColumn(name);f.setValue(value.text);filters.add(f);
            }
            if(!complete) {issue(warnings,"UNIQUE_VALUES_NOT_PROVEN",table.getName(),null,"Dynamic, omitted, sensitive, nullable or expression-based unique values require runtime enforcement");continue;}
            if(table.getParentRows()==null && table.getTreeConfig()==null && table.getSize()!=null && table.getSize()>1)
                issue(errors,"REPEATED_UNIQUE_VALUE",table.getName(),null,"Multiple rows have the same fully static non-null unique key");
            DatabaseInspectionRequest request=new DatabaseInspectionRequest();request.setTable(table.getName());request.setColumns(Arrays.asList(filters.get(0).getColumn()));request.setWhere(filters);request.setLimit(1);
            if(((Number)read.apply(request).get("rowCount")).intValue()>0)
                issue(errors,"EXISTING_UNIQUE_VALUE",table.getName(),null,"A fully static non-null unique key already exists in the target table");
        }
    }

    private static final class StaticValue {final String text;StaticValue(String text){this.text=text;}}
    private static List<StaticValue> candidates(ColumnInfo column) {
        List<MetaTypeVO> rules=column.getRegs();
        if(rules==null || rules.isEmpty())return Arrays.asList(new StaticValue(null));
        if(rules.size()!=1 || !"RES.INPUTAREA".equals(rules.get(0).getCode()))return null;
        Set<String> values=new LinkedHashSet<>();List<MetaTypeVO.InputItem> inputs=rules.get(0).getInputs();
        if(inputs!=null)for(MetaTypeVO.InputItem item:inputs)if(item!=null && item.getValue()!=null && !item.getValue().trim().isEmpty())values.add(item.getValue().trim());
        List<StaticValue> result=new ArrayList<>();for(String value:values)result.add(new StaticValue(value));
        if(result.isEmpty())result.add(new StaticValue(null));return result;
    }
    private static boolean credential(String name){return lower(name).matches("(?:.*_)?(?:password|passwd|pwd|secret|api_key|access_token|refresh_token)");}
    private static String lower(Object value){return String.valueOf(value).toLowerCase(Locale.ROOT);}
    private static boolean yes(Object value){return "YES".equalsIgnoreCase(String.valueOf(value));}
    private static int number(Map<String,Object> item,String key){Object n=item.get(key);return n instanceof Number?((Number)n).intValue():0;}
    private static void issue(List<Map<String,Object>> list,String code,String table,String column,String message) {
        if(list.size()>=200)throw new IllegalArgumentException("PREFLIGHT_ISSUE_LIMIT: narrow the generation configuration");
        list.add(map("code",code,"table",table,"column",column,"message",message));
    }
    private static Map<String,Object> map(Object...pairs){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
}
