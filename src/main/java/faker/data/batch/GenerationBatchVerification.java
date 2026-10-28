package faker.data.batch;

import faker.data.domain.*;
import faker.data.service.db.*;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.math.BigDecimal;

/** 对原始批次做只读观测，不确认未知提交、不修复记录，也不改变账本状态。 */
@Service
public class GenerationBatchVerification {
    private static final int MAX_ROWS=10000;
    private final GenerationBatchStore store;private final Semaphore gate=new Semaphore(1);
    public GenerationBatchVerification(GenerationBatchStore store){this.store=store;}
    private static final class Table {
        TargetRowSnapshot schema;List<Map<String,String>> keys=new ArrayList<>(),present=new ArrayList<>();Set<String> presentKeys=new HashSet<>();
    }
    @SuppressWarnings("unchecked") public Map<String,Object> verify(String id,BatchVerificationRequest request){
        if(request.getChecks()==null || request.getChecks().size()>20)throw new IllegalArgumentException("VERIFICATION_CHECK_LIMIT");
        if(!gate.tryAcquire())throw new IllegalStateException("VERIFICATION_BUSY");
        try{
            Map<String,Object> batch=store.get(id);Map<String,Object> report=map("batchId",id,"state",batch.get("state"),"scope","ORIGINAL_COMMITTED_ROWS_READ_ONLY","valid",false);
            if(!"COMMITTED".equals(batch.get("state"))){report.put("observation","BATCH_NOT_COMMITTED");return report;}
            if(((Number)batch.get("untrackedRows")).intValue()>0){report.put("observation","UNTRACKED_WRITES");return report;}
            if(((Number)batch.get("stagedRowCount")).intValue()>MAX_ROWS)throw new IllegalArgumentException("VERIFICATION_ROW_LIMIT");
            try(Connection connection=JdbcServiceFactory.getJdbcService(request.getDataSource()).getDrivierDatasource().getConnection()){
                try{connection.setReadOnly(true);}catch(SQLException ignored){}
                if(connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ))connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                else if(connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE))connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                else throw new IllegalArgumentException("VERIFICATION_SNAPSHOT_ISOLATION_REQUIRED");
                report.put("isolation",connection.getTransactionIsolation()==Connection.TRANSACTION_REPEATABLE_READ?"REPEATABLE_READ":"SERIALIZABLE");connection.setAutoCommit(false);
                try{return inspect(connection,id,request,batch,report);}finally{connection.rollback();}
            }catch(SQLException error){throw DatabaseConnectionException.from(error,request.getDataSource());}
        }finally{gate.release();}
    }
    @SuppressWarnings("unchecked") private Map<String,Object> inspect(Connection connection,String id,BatchVerificationRequest request,Map<String,Object> batch,Map<String,Object> report) throws SQLException {
        Map<String,Object> identity=(Map<String,Object>)batch.get("databaseIdentity");if(!Objects.equals(identity.get("fingerprint"),GenerationBatchStore.identity(connection).get("fingerprint"))){report.put("observation","DATABASE_IDENTITY_MISMATCH");return report;}
        Map<String,Table> tables=new LinkedHashMap<>();
        for(Map<String,Object> entry:(List<Map<String,Object>>)batch.get("tables")){
            if("UNTRACKED".equals(entry.get("tracking")))continue;String name=(String)entry.get("table");Table table=new Table();table.schema=store.descriptor(id,name);tables.put(name,table);
            if(!Objects.equals(table.schema.schemaHash,TargetRowSnapshot.inspectExisting(connection,name).schemaHash)){report.put("observation","SCHEMA_CHANGED");return report;}
        }
        int offset=0,seen=0,found=0,full=0,immutable=0;long characters=0;
        while(true){
            Map<String,Object> page=store.rows(id,offset,200);List<Map<String,Object>> rows=(List<Map<String,Object>>)page.get("rows");if(rows.isEmpty())break;
            Map<String,List<Map<String,Object>>> grouped=new LinkedHashMap<>();
            for(Map<String,Object> row:rows){String name=(String)row.get("table");Table table=tables.get(name);if(table==null)throw new IllegalStateException("VERIFICATION_JOURNAL_INCONSISTENT");Map<String,String> key=(Map<String,String>)row.get("primaryKey");String encoded=TargetRowSnapshot.json(key);characters+=encoded.length()+128;if(encoded.length()>8192 || characters>4194304)throw new IllegalArgumentException("VERIFICATION_KEY_BUDGET");table.keys.add(key);grouped.computeIfAbsent(name,k->new ArrayList<>()).add(row);}
            for(Map.Entry<String,List<Map<String,Object>>> group:grouped.entrySet()){
                Table table=tables.get(group.getKey());List<Map<String,String>> keys=new ArrayList<>();Map<String,Map<String,Object>> expected=new HashMap<>();
                for(Map<String,Object> row:group.getValue()){Map<String,String> key=(Map<String,String>)row.get("primaryKey");keys.add(key);if(expected.put(TargetRowSnapshot.json(key),row)!=null)throw new IllegalStateException("VERIFICATION_JOURNAL_INCONSISTENT");}
                for(TargetRowSnapshot.Row row:table.schema.read(connection,keys)){String encoded=TargetRowSnapshot.json(row.primaryKey);Map<String,Object> old=expected.get(encoded);if(old==null)throw new IllegalStateException("VERIFICATION_JOURNAL_INCONSISTENT");table.present.add(row.primaryKey);if(!table.presentKeys.add(encoded))throw new IllegalStateException("VERIFICATION_DUPLICATE_KEY");found++;if(Objects.equals(old.get("fingerprint"),row.fingerprint))full++;if(Objects.equals(old.get("ownershipFingerprint"),row.ownershipFingerprint))immutable++;}
            }
            seen+=rows.size();if(seen>MAX_ROWS)throw new IllegalArgumentException("VERIFICATION_ROW_LIMIT");if(!Boolean.TRUE.equals(page.get("truncated")))break;offset=((Number)page.get("nextOffset")).intValue();
        }
        int expected=((Number)batch.get("stagedRowCount")).intValue();if(seen!=expected)throw new IllegalStateException("VERIFICATION_JOURNAL_INCONSISTENT");
        List<Map<String,Object>> checks=new ArrayList<>();for(Map<String,Object> check:request.getChecks())checks.add(check(connection,tables,check));
        boolean valid=found==expected && immutable==expected && checks.stream().allMatch(c->"PASS".equals(c.get("status")));
        report.put("valid",valid);report.put("expectedRows",expected);report.put("foundRows",found);report.put("matchingFullRows",full);report.put("matchingImmutableRows",immutable);report.put("snapshotStatus",found==expected && full==expected?"UNCHANGED":found==expected && immutable==expected?"DECLARED_MUTABLE_CHANGES":"CHANGED_OR_MISSING");report.put("checks",checks);report.put("notes","Only original tracked keys are observed. Later derived rows, application workflows and undeclared invariants are outside this result. No target/local state was changed; concurrent later writes remain possible.");return report;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> check(Connection connection,Map<String,Table> tables,Map<String,Object> specification) throws SQLException {
        if(specification==null)throw new IllegalArgumentException("VERIFICATION_CHECK_REQUIRED");String kind=text(specification.get("kind")),name=text(specification.get("table"));Table table=table(tables,name);
        List<Map<String,String>> failed=new ArrayList<>();int mismatches=0;
        if(kind.equals("ROW_COUNT")){
            int expected;try{expected=new BigDecimal(String.valueOf(specification.get("expected"))).intValueExact();}catch(RuntimeException invalid){throw new IllegalArgumentException("VERIFICATION_EXPECTED_COUNT_REQUIRED");}if(expected<0 || expected>MAX_ROWS)throw new IllegalArgumentException("VERIFICATION_EXPECTED_COUNT_LIMIT");
            return map("kind",kind,"table",table.schema.table,"status",expected==table.present.size()?"PASS":"FAIL","expected",expected,"actual",table.present.size());
        }
        if(kind.equals("VALUE_SET")){
            TargetRowSnapshot.Column column=scalar(table.schema,text(specification.get("column")));Object values=specification.get("values");if(!(values instanceof List) || ((List<?>)values).isEmpty() || ((List<?>)values).size()>100)throw new IllegalArgumentException("VERIFICATION_ALLOWED_VALUES_REQUIRED");Set<String> allowed=new HashSet<>();for(Object value:(List<?>)values){if(value==null){allowed.add(null);continue;}if(!(value instanceof String || value instanceof Number || value instanceof Boolean) || value.toString().length()>2048)throw new IllegalArgumentException("VERIFICATION_SCALAR_VALUE_LIMIT");allowed.add(table.schema.canonical(column.name,value));}
            for(Values row:values(connection,table,Arrays.asList(column.name)))if(!allowed.contains(row.values.get(column.name))){mismatches++;if(failed.size()<10)failed.add(row.key);}
        }else if(kind.equals("REFERENCE")){
            String targetName=text(specification.get("targetTable")),scope=specification.get("targetScope")==null?"BATCH":text(specification.get("targetScope"));if(!Arrays.asList("BATCH","DATABASE").contains(scope) || targetName.contains("."))throw new IllegalArgumentException("VERIFICATION_REFERENCE_SCOPE");
            Table owned=scope.equals("BATCH")?table(tables,targetName):null;TargetRowSnapshot parent=owned==null?TargetRowSnapshot.inspectExisting(connection,targetName):owned.schema;
            if(!Objects.equals(parent.catalog,table.schema.catalog) || !Objects.equals(parent.schema,table.schema.schema))throw new IllegalArgumentException("VERIFICATION_SELECTED_SCHEMA_ONLY");
            Object raw=specification.get("columns");if(!(raw instanceof Map) || ((Map<?,?>)raw).size()!=parent.primaryKeys.size() || parent.primaryKeys.isEmpty() || parent.primaryKeys.size()>32)throw new IllegalArgumentException("VERIFICATION_COMPLETE_PARENT_KEY_REQUIRED");Map<String,String> columns=new LinkedHashMap<>();Set<String> matched=new HashSet<>();
            for(Map.Entry<?,?> pair:((Map<?,?>)raw).entrySet()){TargetRowSnapshot.Column child=scalar(table.schema,text(pair.getKey())),key=scalar(parent,text(pair.getValue()));if(!parent.primaryKeys.contains(key.name) || numeric(child.type)!=numeric(key.type) || (!numeric(key.type) && (!strings(child.type) || !strings(key.type))))throw new IllegalArgumentException("VERIFICATION_COMPATIBLE_PARENT_KEY_REQUIRED");if(columns.put(child.name,key.name)!=null || !matched.add(key.name))throw new IllegalArgumentException("VERIFICATION_AMBIGUOUS_REFERENCE");}if(!matched.equals(new HashSet<>(parent.primaryKeys)))throw new IllegalArgumentException("VERIFICATION_COMPLETE_PARENT_KEY_REQUIRED");
            List<Values> values=values(connection,table,new ArrayList<>(columns.keySet()));Map<String,Map<String,String>> targets=new LinkedHashMap<>();Map<String,String> targetByChild=new HashMap<>();Set<String> nullable=new HashSet<>();
            for(Values row:values){Map<String,String> key=new LinkedHashMap<>();boolean anyNull=false;for(String parentKey:parent.primaryKeys)for(Map.Entry<String,String> column:columns.entrySet())if(column.getValue().equals(parentKey)){String value=row.values.get(column.getKey());if(value==null)anyNull=true;else key.put(parentKey,parent.canonical(parentKey,value));}String child=TargetRowSnapshot.json(row.key);if(anyNull)nullable.add(child);else{String encoded=TargetRowSnapshot.json(key);targets.put(encoded,key);targetByChild.put(child,encoded);}}
            Map<String,String> resolved=existing(connection,parent,new ArrayList<>(targets.values()));boolean allowNull=Boolean.TRUE.equals(specification.get("allowNull"));
            for(Values row:values){String child=TargetRowSnapshot.json(row.key),actual=resolved.get(targetByChild.get(child));if(nullable.contains(child)?!allowNull:actual==null || owned!=null && !owned.presentKeys.contains(actual)){mismatches++;if(failed.size()<10)failed.add(row.key);}}
        }else throw new IllegalArgumentException("VERIFICATION_CHECK_KIND_UNSUPPORTED");
        return map("kind",kind,"table",table.schema.table,"status",mismatches==0?"PASS":"FAIL","checkedRows",table.present.size(),"mismatchedRows",mismatches,"sampleFailureKeys",failed);
    }
    private static final class Values {Map<String,String> key,values;Values(Map<String,String> key,Map<String,String> values){this.key=key;this.values=values;}}
    private static List<Values> values(Connection connection,Table table,List<String> columns) throws SQLException {
        if((long)columns.size()*table.present.size()>50000)throw new IllegalArgumentException("VERIFICATION_CHECK_CELL_LIMIT");List<Values> output=new ArrayList<>();LinkedHashSet<String> fields=new LinkedHashSet<>(table.schema.primaryKeys);fields.addAll(columns);StringJoiner projection=new StringJoiner(",");for(String field:fields)projection.add(table.schema.quoted(field));long characters=0;
        for(int offset=0;offset<table.present.size();offset+=chunk(table.schema)){
            List<Map<String,String>> keys=table.present.subList(offset,Math.min(table.present.size(),offset+chunk(table.schema)));
            try(PreparedStatement statement=query(connection,table.schema,projection.toString(),keys);ResultSet rows=statement.executeQuery()){
                while(rows.next()){Map<String,String> values=new LinkedHashMap<>();for(String column:columns){Object value=rows.getObject(column);String text=value==null?null:table.schema.canonical(column,value);if(text!=null){characters+=text.length();if(text.length()>2048 || characters>4194304)throw new IllegalArgumentException("VERIFICATION_VALUE_BUDGET");}values.put(column,text);}output.add(new Values(table.schema.key(rows),values));}
            }
        }return output;
    }
    private static Map<String,String> existing(Connection connection,TargetRowSnapshot table,List<Map<String,String>> keys) throws SQLException {
        Map<String,String> found=new HashMap<>();StringJoiner projection=new StringJoiner(","),terms=new StringJoiner(" AND ");for(String key:table.primaryKeys){projection.add(table.quoted(key));terms.add(table.quoted(key)+"=?");}
        for(int offset=0;offset<keys.size();offset+=chunk(table)){
            int end=Math.min(keys.size(),offset+chunk(table));StringJoiner unions=new StringJoiner(" UNION ALL ");for(int index=offset;index<end;index++)unions.add("SELECT "+index+","+projection+" FROM "+table.qualified()+" WHERE "+terms);
            try(PreparedStatement statement=connection.prepareStatement(unions.toString(),ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)){
                int parameter=1;for(int index=offset;index<end;index++)for(String key:table.primaryKeys)statement.setString(parameter++,keys.get(index).get(key));statement.setQueryTimeout(10);statement.setMaxRows(end-offset);statement.setFetchSize(connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql")?Integer.MIN_VALUE:Math.min(100,end-offset));
                try(ResultSet rows=statement.executeQuery()){while(rows.next()){int input=rows.getInt(1);Map<String,String> actual=new LinkedHashMap<>();for(int part=0;part<table.primaryKeys.size();part++){String field=table.primaryKeys.get(part);actual.put(field,table.canonical(field,rows.getObject(part+2)));}found.put(TargetRowSnapshot.json(keys.get(input)),TargetRowSnapshot.json(actual));}}
            }
        }return found;
    }
    private static PreparedStatement query(Connection connection,TargetRowSnapshot table,String projection,List<Map<String,String>> keys) throws SQLException {
        StringJoiner alternatives=new StringJoiner(" OR ");for(Map<String,String> ignored:keys){StringJoiner terms=new StringJoiner(" AND ");for(String key:table.primaryKeys)terms.add(table.quoted(key)+"=?");alternatives.add("("+terms+")");}
        PreparedStatement statement=connection.prepareStatement("SELECT "+projection+" FROM "+table.qualified()+" WHERE "+alternatives,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);try{int parameter=1;for(Map<String,String> key:keys)for(String field:table.primaryKeys)statement.setString(parameter++,key.get(field));statement.setQueryTimeout(10);statement.setMaxRows(keys.size());statement.setFetchSize(connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql")?Integer.MIN_VALUE:Math.min(100,keys.size()));return statement;}catch(SQLException|RuntimeException failure){try{statement.close();}catch(SQLException ignored){}throw failure;}
    }
    private static int chunk(TargetRowSnapshot table){return Math.max(1,Math.min(100,500/table.primaryKeys.size()));}
    private static Table table(Map<String,Table> tables,String name){Table found=null;for(Map.Entry<String,Table> table:tables.entrySet())if(table.getKey().equalsIgnoreCase(name)){if(found!=null)throw new IllegalArgumentException("VERIFICATION_AMBIGUOUS_TABLE");found=table.getValue();}if(found==null)throw new IllegalArgumentException("VERIFICATION_TABLE_NOT_IN_BATCH");return found;}
    private static TargetRowSnapshot.Column scalar(TargetRowSnapshot table,String name){TargetRowSnapshot.Column column=table.columnInfo(name);if(column.name.toLowerCase(Locale.ROOT).matches("(?:.*_)?(?:password|passwd|pwd|secret|api_key|access_token|refresh_token)") || !numeric(column.type) && !strings(column.type) && column.type!=Types.BOOLEAN && column.type!=Types.BIT || strings(column.type) && column.size>2048)throw new IllegalArgumentException("VERIFICATION_COLUMN_NOT_ALLOWED");return column;}
    private static boolean numeric(int type){return OwnershipJournal.numeric(type);}
    private static boolean strings(int type){return Arrays.asList(Types.VARCHAR,Types.NVARCHAR,Types.CHAR,Types.NCHAR).contains(type);}
    private static String text(Object value){if(!(value instanceof String) || ((String)value).trim().isEmpty() || ((String)value).length()>128)throw new IllegalArgumentException("VERIFICATION_IDENTIFIER_REQUIRED");return (String)value;}
    private static Map<String,Object> map(Object...pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
}
