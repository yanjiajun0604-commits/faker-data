package faker.data.batch;

import faker.data.domain.TableVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;

/** 从目标事务中读取真实行；只保存主键和摘要，不保存完整业务字段。 */
public final class TargetRowSnapshot {
    private static final ObjectMapper JSON=new ObjectMapper();
    public String table,catalog,schema,name,quote,schemaHash,unsupportedReason;
    public List<Column> columns=new ArrayList<>();
    public List<String> primaryKeys=new ArrayList<>();
    public int[] keyPositions;
    public static final class Column {
        public String name,typeName;public int type,size,scale;
    }
    public static final class Row {
        public Map<String,String> primaryKey;public String fingerprint;
        public Row(){}
        Row(Map<String,String> primaryKey,String fingerprint){this.primaryKey=primaryKey;this.fingerprint=fingerprint;}
    }

    public static TargetRowSnapshot inspect(Connection connection,TableVO configured) throws SQLException {
        DatabaseMetaData meta=connection.getMetaData();TargetRowSnapshot out=new TargetRowSnapshot();out.table=configured.getName();
        boolean mysql=meta.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
        out.catalog=connection.getCatalog();out.schema=mysql?null:connection.getSchema();
        String[] parts=configured.getName().split("\\.");String requested=parts[parts.length-1];
        if(parts.length>2){out.unsupportedReason="QUALIFIED_NAME_NOT_TRACKED";return out;}
        if(parts.length==2){if(mysql)out.catalog=parts[0];else out.schema=parts[0];}
        String q=meta.getIdentifierQuoteString();out.quote=q==null || q.trim().isEmpty()?"\"":q.trim();
        LinkedHashSet<String> variants=new LinkedHashSet<>();variants.add(requested);
        if(meta.storesUpperCaseIdentifiers())variants.add(requested.toUpperCase(Locale.ROOT));
        if(meta.storesLowerCaseIdentifiers())variants.add(requested.toLowerCase(Locale.ROOT));
        for(String variant:variants)try(ResultSet rows=meta.getTables(out.catalog,pattern(meta,out.schema),pattern(meta,variant),new String[]{"TABLE"})) {
            while(rows.next())if(variant.equals(rows.getString("TABLE_NAME"))){out.name=variant;out.catalog=rows.getString("TABLE_CAT");out.schema=rows.getString("TABLE_SCHEM");break;}
            if(out.name!=null)break;
        }
        if(out.name==null)throw new IllegalArgumentException("BATCH_TARGET_NOT_FOUND: "+configured.getName());
        try(ResultSet rows=meta.getColumns(out.catalog,pattern(meta,out.schema),pattern(meta,out.name),"%")) {
            while(rows.next()) {
                if(out.columns.size()>=500)throw new IllegalArgumentException("BATCH_COLUMN_LIMIT");
                Column col=new Column();col.name=rows.getString("COLUMN_NAME");col.type=rows.getInt("DATA_TYPE");col.typeName=rows.getString("TYPE_NAME");col.size=rows.getInt("COLUMN_SIZE");col.scale=rows.getInt("DECIMAL_DIGITS");out.columns.add(col);
            }
        }
        TreeMap<Integer,String> keys=new TreeMap<>();
        try(ResultSet rows=meta.getPrimaryKeys(out.catalog,out.schema,out.name)){while(rows.next())keys.put(rows.getInt("KEY_SEQ"),rows.getString("COLUMN_NAME"));}
        out.primaryKeys.addAll(keys.values());
        for(Column column:out.columns)if(Arrays.asList(Types.JAVA_OBJECT,Types.OTHER,Types.ARRAY,Types.STRUCT,Types.REF,Types.SQLXML).contains(column.type))out.unsupportedReason="UNSUPPORTED_FINGERPRINT_TYPE";
        if(keys.isEmpty())out.unsupportedReason="NO_PRIMARY_KEY";
        if(keys.size()>32)out.unsupportedReason="PRIMARY_KEY_TOO_WIDE";
        out.keyPositions=new int[keys.size()];
        for(int i=0;i<out.primaryKeys.size();i++) {
            String key=out.primaryKeys.get(i);int position=-1;
            for(int c=0;c<configured.getColumns().size();c++)if(configured.getColumns().get(c).getName().equalsIgnoreCase(key)){position=c;break;}
            out.keyPositions[i]=position;
            if(position<0)out.unsupportedReason="PRIMARY_KEY_NOT_SUPPLIED";
            Column col=out.column(key);
            if(!scalarKey(col.type))out.unsupportedReason="UNSUPPORTED_PRIMARY_KEY_TYPE";
            if(key.toLowerCase(Locale.ROOT).matches("(?:.*_)?(?:password|passwd|pwd|secret|api_key|access_token|refresh_token)"))out.unsupportedReason="SENSITIVE_PRIMARY_KEY";
        }
        out.schemaHash=sha(json(out.columns)+json(out.primaryKeys));return out;
    }

    public Map<String,String> inputKey(Object[] values) {
        Map<String,String> key=new LinkedHashMap<>();
        for(int i=0;i<primaryKeys.size();i++) {
            Object value=values[keyPositions[i]];
            if(value==null)throw new IllegalArgumentException("BATCH_EXPLICIT_PRIMARY_KEY_NULL: "+table+"."+primaryKeys.get(i));
            key.put(primaryKeys.get(i),canonicalKey(value,column(primaryKeys.get(i)).type));
        }
        return key;
    }

    public List<Row> read(Connection connection,List<Map<String,String>> keys) throws SQLException {
        return read(connection,keys,false);
    }
    public List<Row> read(Connection connection,List<Map<String,String>> keys,boolean lock) throws SQLException {
        List<Row> result=new ArrayList<>();if(keys.isEmpty())return result;
        int chunk=Math.max(1,Math.min(100,500/primaryKeys.size()));
        StringJoiner projection=new StringJoiner(",");for(Column col:columns)projection.add(quoted(col.name));
        for(int offset=0;offset<keys.size();offset+=chunk) {
            List<Map<String,String>> group=keys.subList(offset,Math.min(keys.size(),offset+chunk));
            StringJoiner alternatives=new StringJoiner(" OR ");
            for(Map<String,String> ignored:group){StringJoiner terms=new StringJoiner(" AND ");for(String key:primaryKeys)terms.add(quoted(key)+"=?");alternatives.add("("+terms+")");}
            try(PreparedStatement statement=connection.prepareStatement("SELECT "+projection+" FROM "+qualified()+" WHERE "+alternatives+(lock?" FOR UPDATE":""))) {
                int parameter=1;for(Map<String,String> key:group)for(String name:primaryKeys)statement.setString(parameter++,key.get(name));
                statement.setQueryTimeout(10);statement.setFetchSize(Math.min(group.size(),50));
                try(ResultSet rows=statement.executeQuery()) {
                    while(rows.next()) {
                        Map<String,String> key=new LinkedHashMap<>();
                        for(String name:primaryKeys)key.put(name,canonicalKey(rows.getObject(name),column(name).type));
                        result.add(new Row(key,fingerprint(rows)));
                    }
                }
            }
        }
        return result;
    }

    public String qualified(){String prefix=schema!=null?schema:catalog;return (prefix==null || prefix.isEmpty()?"":quoted(prefix)+".")+quoted(name);}
    public String physicalName(){return json(Arrays.asList(catalog,schema,name));}
    public Map<String,String> key(ResultSet row) throws SQLException {Map<String,String> key=new LinkedHashMap<>();for(String name:primaryKeys)key.put(name,canonicalKey(row.getObject(name),column(name).type));return key;}
    public String quoted(String value){return quote+value.replace(quote,quote+quote)+quote;}
    private Column column(String name){return columns.stream().filter(c->c.name.equals(name)).findFirst().orElseThrow(()->new IllegalArgumentException("BATCH_KEY_COLUMN_NOT_FOUND"));}
    private String fingerprint(ResultSet rows) throws SQLException {
        MessageDigest row=digest();long[] bytes={0};
        for(int i=0;i<columns.size();i++) {
            Column col=columns.get(i);MessageDigest cell=digest();
            try {
                if(binary(col.type)) {
                    try(InputStream input=rows.getBinaryStream(i+1)){if(input==null)cell.update((byte)0);else {cell.update((byte)1);byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))!=-1;){limit(bytes,n);cell.update(buffer,0,n);}}}
                } else if(text(col.type)) {
                    try(Reader input=rows.getCharacterStream(i+1)){if(input==null)cell.update((byte)0);else {cell.update((byte)1);char[] buffer=new char[4096];for(int n;(n=input.read(buffer))!=-1;){limit(bytes,n*2L);for(int c=0;c<n;c++){cell.update((byte)(buffer[c]>>>8));cell.update((byte)buffer[c]);}}}}
                } else {
                    Object value=rows.getObject(i+1);
                    if(value==null)cell.update((byte)0);
                    else {cell.update((byte)1);String canonical=value instanceof BigDecimal?((BigDecimal)value).stripTrailingZeros().toPlainString():value.toString();byte[] data=canonical.getBytes(StandardCharsets.UTF_8);limit(bytes,data.length);cell.update(data);}
                }
            } catch(IOException e){throw new SQLException("BATCH_FINGERPRINT_READ_FAILED",e);}
            row.update(cell.digest());
        }
        return hex(row.digest());
    }
    private static void limit(long[] bytes,long increment){bytes[0]+=increment;if(bytes[0]>67108864)throw new IllegalArgumentException("BATCH_ROW_FINGERPRINT_LIMIT: 64 MiB");}
    private static boolean text(int t){return Arrays.asList(Types.CHAR,Types.VARCHAR,Types.NCHAR,Types.NVARCHAR,Types.CLOB,Types.NCLOB,Types.LONGVARCHAR,Types.LONGNVARCHAR).contains(t);}
    private static boolean binary(int t){return Arrays.asList(Types.BINARY,Types.VARBINARY,Types.LONGVARBINARY,Types.BLOB).contains(t);}
    private static boolean scalarKey(int t){return Arrays.asList(Types.VARCHAR,Types.NVARCHAR,Types.BIGINT,Types.INTEGER,Types.SMALLINT,Types.TINYINT,Types.NUMERIC,Types.DECIMAL).contains(t);}
    private static String canonicalKey(Object value,int type){if(value==null)throw new IllegalArgumentException("BATCH_PRIMARY_KEY_NULL");return Arrays.asList(Types.BIGINT,Types.INTEGER,Types.SMALLINT,Types.TINYINT,Types.NUMERIC,Types.DECIMAL).contains(type)?new BigDecimal(value.toString()).stripTrailingZeros().toPlainString():value.toString();}
    private static String pattern(DatabaseMetaData meta,String value) throws SQLException {if(value==null)return null;String e=meta.getSearchStringEscape();if(e==null || e.isEmpty()){if(value.contains("_") || value.contains("%"))throw new IllegalArgumentException("BATCH_METADATA_ESCAPE_UNSUPPORTED");return value;}return value.replace(e,e+e).replace("_",e+"_").replace("%",e+"%");}
    public static String json(Object value){try{return JSON.writeValueAsString(value);}catch(IOException e){throw new IllegalStateException("BATCH_JSON_ERROR",e);}}
    public static String sha(String value){return hex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static String hex(byte[] data){StringBuilder out=new StringBuilder();for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
}
