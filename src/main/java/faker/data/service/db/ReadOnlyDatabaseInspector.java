package faker.data.service.db;

import faker.data.domain.DatabaseInspectionRequest;
import faker.data.domain.DatabaseInspectionRequest.Filter;
import faker.data.domain.DatabaseInspectionRequest.Order;
import faker.data.domain.DataSource;
import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** 使用驱动元数据解析真实标识符；数据读取只生成参数化 SELECT。 */
public final class ReadOnlyDatabaseInspector {
    private static final int METADATA_LIMIT=500;
    private static final Pattern IDENTIFIER=Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_$#]{0,127}");
    private final Supplier<Connection> open;
    private final String selectedCatalog,selectedSchema;
    private final DataSource source;

    public ReadOnlyDatabaseInspector(Supplier<Connection> open,String catalog,String schema,DataSource source) {
        this.open=open;this.selectedCatalog=catalog;this.selectedSchema=schema;this.source=source;
    }

    public Map<String,Object> inspect(DatabaseInspectionRequest request) {
        if(request.getTable()==null) {
            range(request.getLimit(),100,200,"limit");range(request.getOffset(),0,100000,"offset");
            String text=request.getNameContains();
            if(text!=null && (text.length()>128 || text.chars().anyMatch(Character::isISOControl)))
                throw invalid("nameContains must contain at most 128 printable characters");
        } else identifier(request.getTable());
        return access(scope->request.getTable()==null?list(scope,request):describe(scope,resolve(scope,request.getTable())));
    }

    public Map<String,Object> read(DatabaseInspectionRequest request) {
        identifier(request.getTable());
        range(request.getLimit(),20,100,"limit");
        if(request.getColumns()==null || request.getColumns().isEmpty() || request.getColumns().size()>50)
            throw invalid("Specify 1-50 column names; SELECT * is not supported");
        request.getColumns().forEach(ReadOnlyDatabaseInspector::identifier);
        if(request.getWhere()!=null && request.getWhere().size()>20)throw invalid("At most 20 AND filters are supported");
        if(request.getOrderBy()!=null && request.getOrderBy().size()>10)throw invalid("At most 10 ordering columns are supported");
        return access(scope->select(scope,resolve(scope,request.getTable()),request));
    }

    private Map<String,Object> access(Read work) {
        try(Connection c=open.get()) {
            boolean hint=false;
            try {c.setReadOnly(true);hint=true;}catch(SQLException ignored) { /* 只读提示不是权限边界。 */ }
            c.setAutoCommit(false);
            try {
                Map<String,Object> result=work.run(new Scope(c));
                result.put("readOnly",true);result.put("readOnlyHintApplied",hint);
                return result;
            } finally {
                try{c.rollback();}catch(SQLException ignored) { /* 此路径没有写入语句。 */ }
            }
        } catch(SQLException ex) {throw DatabaseConnectionException.from(ex,source);}
    }

    private final class Scope {
        final Connection c;
        final DatabaseMetaData meta;
        final String catalog,schema,escape,quote;
        final boolean mysql;
        Scope(Connection c) throws SQLException {
            this.c=c;meta=c.getMetaData();
            String product=meta.getDatabaseProductName().toLowerCase(Locale.ROOT);
            mysql=product.contains("mysql") || product.contains("mariadb");
            catalog=selectedCatalog!=null?selectedCatalog:(mysql?c.getCatalog():null);
            String current=selectedSchema;
            if(current==null && !mysql)current=c.getSchema();
            schema=current;
            if(mysql?blank(catalog):blank(schema))throw invalid("Select a database/schema in the connection profile before inspection");
            String q=meta.getIdentifierQuoteString();quote=blank(q)?"\"":q.trim();
            String e=meta.getSearchStringEscape();escape=e==null?"":e;
        }
        String pattern(String value) {
            if(value==null)return null;
            if(escape.isEmpty()) {
                if(value.contains("%") || value.contains("_"))throw invalid("Driver cannot escape metadata search patterns");
                return value;
            }
            return value.replace(escape,escape+escape).replace("%",escape+"%").replace("_",escape+"_");
        }
        String quote(String value){return quote+value.replace(quote,quote+quote)+quote;}
        String qualified(Table table){
            String prefix=mysql?table.catalog:table.schema;
            return (blank(prefix)?"":quote(prefix)+".")+quote(table.name);
        }
    }
    private static final class Table {
        String catalog,schema,name,remarks;
    }
    private Table table(ResultSet rows) throws SQLException {
        Table t=new Table();t.catalog=rows.getString("TABLE_CAT");t.schema=rows.getString("TABLE_SCHEM");
        t.name=rows.getString("TABLE_NAME");t.remarks=bounded(rows.getString("REMARKS"),512);return t;
    }
    private Map<String,Object> tableInfo(Table t){return map("name",t.name,"catalog",t.catalog,"schema",t.schema,"remarks",t.remarks);}

    private Map<String,Object> list(Scope s,DatabaseInspectionRequest r) throws SQLException {
        int limit=range(r.getLimit(),100,200,"limit"),offset=range(r.getOffset(),0,100000,"offset");
        String contains=r.getNameContains()==null?"":r.getNameContains();
        List<Map<String,Object>> tables=new ArrayList<>();boolean more=false;int skipped=0;
        try(ResultSet rows=s.meta.getTables(s.catalog,s.pattern(s.schema),"%"+s.pattern(contains)+"%",new String[]{"TABLE"})) {
            while(rows.next()) {
                if(skipped++<offset)continue;
                if(tables.size()==limit){more=true;break;}
                tables.add(tableInfo(table(rows)));
            }
        }
        return map("tables",tables,"count",tables.size(),"offset",offset,"limit",limit,"truncated",more,"nextOffset",more?offset+tables.size():null);
    }

    private Table resolve(Scope s,String name) throws SQLException {
        // 优先精确名称，仅按驱动规则折叠未加引号的大小写；不扫描其他 schema。
        LinkedHashSet<String> variants=new LinkedHashSet<>();variants.add(name);
        if(s.meta.storesUpperCaseIdentifiers())variants.add(name.toUpperCase(Locale.ROOT));
        if(s.meta.storesLowerCaseIdentifiers())variants.add(name.toLowerCase(Locale.ROOT));
        for(String variant:variants) {
            Table found=null;
            try(ResultSet rows=s.meta.getTables(s.catalog,s.pattern(s.schema),s.pattern(variant),new String[]{"TABLE"})) {
                while(rows.next()) {
                    Table next=table(rows);if(!next.name.equals(variant))continue;
                    if(found!=null)throw invalid("Ambiguous table name in the selected scope");found=next;
                }
            }
            if(found!=null)return found;
        }
        throw invalid("Table not found in the selected database/schema: "+name);
    }

    private List<Map<String,Object>> columns(Scope s,Table t) throws SQLException {
        List<Map<String,Object>> result=new ArrayList<>();
        try(ResultSet rows=s.meta.getColumns(t.catalog,s.pattern(t.schema),s.pattern(t.name),"%")) {
            while(rows.next()) {
                if(result.size()==METADATA_LIMIT)throw invalid("Table exceeds the 500-column inspection limit");
                String name=rows.getString("COLUMN_NAME");
                String defaultValue=rows.getString("COLUMN_DEF");
                result.add(map("name",name,"jdbcType",rows.getInt("DATA_TYPE"),"dbType",rows.getString("TYPE_NAME"),
                    "length",rows.getInt("COLUMN_SIZE"),"scale",rows.getInt("DECIMAL_DIGITS"),
                    "nullable",rows.getInt("NULLABLE"),"ordinal",rows.getInt("ORDINAL_POSITION"),
                    "defaultValue",secret(name)?null:bounded(defaultValue,512),"hasDefault",defaultValue!=null,
                    "remarks",bounded(rows.getString("REMARKS"),512),
                    "autoIncrement",optional(rows,"IS_AUTOINCREMENT"),"generated",optional(rows,"IS_GENERATEDCOLUMN")));
            }
        }
        if(result.isEmpty())throw invalid("Driver returned no columns for the selected table");
        result.sort(Comparator.comparingInt(c->((Number)c.get("ordinal")).intValue()));
        return result;
    }
    private Map<String,Object> describe(Scope s,Table t) throws SQLException {
        List<Map<String,Object>> cols=columns(s,t);
        StringJoiner projection=new StringJoiner(",");for(Map<String,Object> col:cols)projection.add(s.quote((String)col.get("name")));
        try(PreparedStatement statement=s.c.prepareStatement("SELECT "+projection+" FROM "+s.qualified(t)+" WHERE 1=0")) {
            statement.setQueryTimeout(5);
            try(ResultSet rows=statement.executeQuery()) {
                ResultSetMetaData types=rows.getMetaData();
                for(int i=0;i<cols.size();i++) {
                    cols.get(i).put("javaType",types.getColumnClassName(i+1));
                    cols.get(i).put("precision",types.getPrecision(i+1));
                }
            }
        }
        Map<String,Object> result=tableInfo(t);result.put("columns",cols);
        String engine=JdbcTableTransactions.storageEngine(s.c,t.catalog,t.name);
        result.put("storageEngine",engine);result.put("transactionalWritesSupported",JdbcTableTransactions.transactional(s.c,engine));
        List<Map<String,Object>> primary=new ArrayList<>(),foreign=new ArrayList<>(),indexes=new ArrayList<>();
        try(ResultSet rows=s.meta.getPrimaryKeys(t.catalog,t.schema,t.name)) {
            while(rows.next()){boundedMetadata(primary);primary.add(map("name",rows.getString("PK_NAME"),"column",rows.getString("COLUMN_NAME"),"sequence",rows.getInt("KEY_SEQ")));}
        }
        try(ResultSet rows=s.meta.getImportedKeys(t.catalog,t.schema,t.name)) {
            while(rows.next()) {
                boundedMetadata(foreign);
                foreign.add(map("name",rows.getString("FK_NAME"),"column",rows.getString("FKCOLUMN_NAME"),"sequence",rows.getInt("KEY_SEQ"),
                    "referencedCatalog",rows.getString("PKTABLE_CAT"),"referencedSchema",rows.getString("PKTABLE_SCHEM"),
                    "referencedTable",rows.getString("PKTABLE_NAME"),"referencedColumn",rows.getString("PKCOLUMN_NAME"),
                    "updateRule",rows.getInt("UPDATE_RULE"),"deleteRule",rows.getInt("DELETE_RULE")));
            }
        }
        try(ResultSet rows=s.meta.getIndexInfo(t.catalog,t.schema,t.name,false,true)) {
            while(rows.next()) {
                if(rows.getShort("TYPE")==DatabaseMetaData.tableIndexStatistic)continue;
                boundedMetadata(indexes);
                indexes.add(map("name",rows.getString("INDEX_NAME"),"column",rows.getString("COLUMN_NAME"),
                    "sequence",rows.getInt("ORDINAL_POSITION"),"unique",!rows.getBoolean("NON_UNIQUE"),"direction",rows.getString("ASC_OR_DESC")));
            }
        }
        result.put("primaryKeys",primary);result.put("foreignKeys",foreign);result.put("indexes",indexes);
        result.put("weakRelationsInferred",false);
        result.put("scope","DRIVER_METADATA");
        result.put("notes","Driver-reported constraints/comments only. Missing foreign keys do not imply no business relationship. Composite key/index rows use name and sequence.");
        return result;
    }

    private Map<String,Object> select(Scope s,Table t,DatabaseInspectionRequest r) throws SQLException {
        List<Map<String,Object>> metadata=columns(s,t);
        List<Map<String,Object>> selected=new ArrayList<>();Set<String> names=new HashSet<>();
        for(String input:r.getColumns()) {
            Map<String,Object> col=column(metadata,input);String name=(String)col.get("name");
            if(!names.add(name))throw invalid("Duplicate selected column: "+name);selected.add(col);
        }
        // 不让驱动先取回完整 BLOB／长字符串再在 Java 中截断。
        StringJoiner projection=new StringJoiner(",");for(Map<String,Object> col:selected)projection.add(sampleProjection(s,col));
        StringBuilder sql=new StringBuilder("SELECT ").append(projection).append(" FROM ").append(s.qualified(t));
        List<Object> values=new ArrayList<>();List<String> conditions=new ArrayList<>();
        if(r.getWhere()!=null)for(Filter f:r.getWhere()) {
            if(f==null)throw invalid("Filter must not be null");
            String field=s.quote((String)column(metadata,f.getColumn()).get("name"));
            if(secret(f.getColumn()))throw invalid("Credential fields cannot be used in filters");
            String op=f.getOp()==null?"eq":f.getOp().toLowerCase(Locale.ROOT);Object value=f.getValue();
            if(op.equals("is_null") || op.equals("not_null")) {
                if(value!=null)throw invalid("Null predicates do not accept a value");
                conditions.add(field+(op.equals("is_null")?" IS NULL":" IS NOT NULL"));continue;
            }
            if(op.equals("in")) {
                if(!(value instanceof List) || ((List<?>)value).isEmpty() || ((List<?>)value).size()>100)throw invalid("IN requires 1-100 scalar values");
                StringJoiner parameters=new StringJoiner(",");
                for(Object v:(List<?>)value){if(v==null)throw invalid("Use is_null for null values");scalar(v);values.add(v);parameters.add("?");}
                conditions.add(field+" IN ("+parameters+")");continue;
            }
            String operator;
            switch(op){case "eq":operator="=";break;case "ne":operator="<>";break;case "gt":operator=">";break;
                case "gte":operator=">=";break;case "lt":operator="<";break;case "lte":operator="<=";break;
                default:throw invalid("Unsupported filter operator; use eq/ne/gt/gte/lt/lte/in/is_null/not_null");}
            if(value==null) {
                if(!op.equals("eq")&&!op.equals("ne"))throw invalid("Null values require eq/ne/is_null/not_null");
                conditions.add(field+(op.equals("eq")?" IS NULL":" IS NOT NULL"));
            } else {scalar(value);conditions.add(field+operator+"?");values.add(value);}
        }
        if(!conditions.isEmpty())sql.append(" WHERE ").append(String.join(" AND ",conditions));
        StringJoiner ordering=new StringJoiner(",");
        if(r.getOrderBy()!=null)for(Order order:r.getOrderBy()) {
            if(order==null)throw invalid("Ordering must not be null");
            if(order.getColumn()!=null && secret(order.getColumn()))throw invalid("Credential fields cannot be used for ordering");
            String direction=order.getDirection()==null?"ASC":order.getDirection().toUpperCase(Locale.ROOT);
            if(!direction.equals("ASC")&&!direction.equals("DESC"))throw invalid("Ordering direction must be ASC or DESC");
            ordering.add(s.quote((String)column(metadata,order.getColumn()).get("name"))+" "+direction);
        }
        if(ordering.length()>0)sql.append(" ORDER BY ").append(ordering);
        int limit=range(r.getLimit(),20,100,"limit");
        String query;
        if(source.getType()==faker.data.domain.JdbcType.MYSQL)query=sql+" LIMIT ?";
        else query="SELECT * FROM ("+sql+") WHERE ROWNUM <= ?";
        values.add(limit+1);
        List<List<Object>> records=new ArrayList<>();boolean more=false;
        try(PreparedStatement statement=s.c.prepareStatement(query)) {
            statement.setQueryTimeout(5);statement.setMaxRows(limit+1);statement.setFetchSize(Math.min(limit+1,50));
            for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));
            try(ResultSet rows=statement.executeQuery()) {
                while(rows.next()) {
                    if(records.size()==limit){more=true;break;}
                    List<Object> record=new ArrayList<>();
                    for(int i=0;i<selected.size();i++)record.add(cell(rows,i+1,selected.get(i)));
                    records.add(record);
                }
            }
        }
        List<String> outputNames=new ArrayList<>();for(Map<String,Object> col:selected)outputNames.add((String)col.get("name"));
        return map("table",t.name,"columns",outputNames,"rows",records,"rowCount",records.size(),"limit",limit,"truncated",more,
            "notes","Numbers requiring exact precision are strings; text is limited to 512 characters. Binary/large objects are omitted; credential fields are redacted. Use orderBy for stable sampling.");
    }

    private Object cell(ResultSet rows,int index,Map<String,Object> col) throws SQLException {
        if(secret((String)col.get("name")))return map("kind","REDACTED","omitted",true);
        int type=((Number)col.get("jdbcType")).intValue();
        switch(type) {
            case Types.BINARY:case Types.VARBINARY:case Types.LONGVARBINARY:case Types.BLOB:
                return map("kind","BINARY","omitted",true);
            case Types.CLOB:case Types.NCLOB:case Types.LONGVARCHAR:case Types.LONGNVARCHAR:case Types.SQLXML:
                return map("kind","LARGE_OBJECT","omitted",true);
            case Types.JAVA_OBJECT:case Types.OTHER:case Types.ARRAY:case Types.STRUCT:case Types.REF:
                return map("kind","UNSUPPORTED_OBJECT","omitted",true);
            default:
                Object value=rows.getObject(index);if(value==null)return null;
                if(value instanceof BigDecimal)return ((BigDecimal)value).toPlainString();
                if(value instanceof Long || value instanceof BigInteger)return value.toString();
                if(value instanceof Number || value instanceof Boolean)return value;
                String text=value.toString();return text.length()>512?map("value",text.substring(0,512),"truncated",true):text;
        }
    }
    private String sampleProjection(Scope s,Map<String,Object> col) {
        String name=s.quote((String)col.get("name"));
        if(secret((String)col.get("name")))return "NULL AS "+name;
        switch(((Number)col.get("jdbcType")).intValue()) {
            case Types.CHAR:case Types.VARCHAR:case Types.NCHAR:case Types.NVARCHAR:
                return "SUBSTR("+name+",1,513) AS "+name;
            case Types.BINARY:case Types.VARBINARY:case Types.LONGVARBINARY:case Types.BLOB:
            case Types.CLOB:case Types.NCLOB:case Types.LONGVARCHAR:case Types.LONGNVARCHAR:case Types.SQLXML:
            case Types.JAVA_OBJECT:case Types.OTHER:case Types.ARRAY:case Types.STRUCT:case Types.REF:
                return "NULL AS "+name;
            default:return name;
        }
    }
    private static Map<String,Object> column(List<Map<String,Object>> columns,String input) {
        identifier(input);Map<String,Object> folded=null;
        for(Map<String,Object> c:columns) {
            String name=(String)c.get("name");if(name.equals(input))return c;
            if(name.equalsIgnoreCase(input)){if(folded!=null)throw invalid("Ambiguous column name: "+input);folded=c;}
        }
        if(folded==null)throw invalid("Unknown column: "+input);return folded;
    }
    private static void scalar(Object value) {
        if(value instanceof String && ((String)value).length()<=2048)return;
        if(value instanceof Boolean)return;
        if(value instanceof Number && Double.isFinite(((Number)value).doubleValue()))return;
        throw invalid("Filter values must be finite numbers, booleans or strings up to 2048 characters");
    }
    private static boolean secret(String name){return name.toLowerCase(Locale.ROOT).matches("(?:.*_)?(?:password|passwd|pwd|secret|api_key|access_token|refresh_token)");}
    private static String bounded(String text,int limit){return text==null?null:text.substring(0,Math.min(text.length(),limit));}
    private static boolean blank(String value){return value==null || value.trim().isEmpty();}
    private static String optional(ResultSet rows,String field){try{return rows.getString(field);}catch(SQLException ex){return null;}}
    private static void identifier(String value){if(value==null || !IDENTIFIER.matcher(value).matches())throw invalid("Use an unqualified simple table/column identifier, not SQL or expressions");}
    private static int range(Integer value,int fallback,int max,String name){int n=value==null?fallback:value;if(n<(name.equals("offset")?0:1)||n>max)throw invalid(name+" is outside the supported range (max "+max+")");return n;}
    private static void boundedMetadata(List<?> rows){if(rows.size()>=METADATA_LIMIT)throw invalid("Metadata exceeds the 500-entry limit; inspect a smaller table");}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
    private static Map<String,Object> map(Object...kv){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
    private interface Read {Map<String,Object> run(Scope scope) throws SQLException;}
}
