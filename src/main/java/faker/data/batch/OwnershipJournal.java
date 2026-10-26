package faker.data.batch;

import faker.data.domain.*;
import faker.data.service.db.JdbcTableTransactions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 保存生成前的键基线；关联本身不等同于本批次所有权。 */
final class OwnershipJournal {
    static final class Relation {
        public String id,parentPhysical,index,mode,watermark;public int ordinal,baselineCount,maxRows;
        public TargetRowSnapshot parent,child;
        public Map<String,String> join=new LinkedHashMap<>();
        public Map<String,Object> where=new LinkedHashMap<>();
    }
    static final class Candidate {
        Map<String,String> key,parent;
        Candidate(Map<String,String> key,Map<String,String> parent){this.key=key;this.parent=parent;}
    }
    private final JdbcTemplate jdbc;private final TransactionTemplate local;private final ObjectMapper json=new ObjectMapper();
    private final Map<String,List<Relation>> active=new ConcurrentHashMap<>();
    OwnershipJournal(JdbcTemplate jdbc) {
        this.jdbc=jdbc;local=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_ownership_relation(batch_id TEXT NOT NULL,relation_id TEXT NOT NULL,ordinal INTEGER NOT NULL,definition_json TEXT NOT NULL,PRIMARY KEY(batch_id,relation_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_ownership_baseline(batch_id TEXT NOT NULL,relation_id TEXT NOT NULL,keys_json TEXT NOT NULL,PRIMARY KEY(batch_id,relation_id,keys_json))");
    }
    void capture(String batch,Connection connection,List<TableVO> tables,Map<String,TargetRowSnapshot> originals) throws SQLException {
        String product=connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if(!product.contains("mysql") && !product.equals("h2"))throw new IllegalArgumentException("OWNERSHIP_DATABASE_NOT_VERIFIED");
        List<Relation> relations=new ArrayList<>();int[] fullCount={0};long fullCharacters=0;
        for(TableVO table:tables)if(table.getOwnership()!=null)compile(connection,originals.get(lower(table.getName())),table.getOwnership().getDerivedRows(),originals,relations);
        for(Relation relation:relations) {
            if("NUMERIC_HIGH_WATERMARK".equals(relation.mode)) {
                String key=relation.child.primaryKeys.get(0);
                try(PreparedStatement statement=connection.prepareStatement("SELECT "+relation.child.quoted(key)+" FROM "+relation.child.qualified()+" ORDER BY "+relation.child.quoted(key)+" DESC LIMIT 1 FOR UPDATE")) {
                    statement.setQueryTimeout(10);try(ResultSet rows=statement.executeQuery()){if(rows.next())relation.watermark=relation.child.canonical(key,rows.getObject(1));}
                }
            } else {
                int limit=relation.baselineCount;relation.baselineCount=0;StringJoiner keys=new StringJoiner(",");for(String key:relation.child.primaryKeys)keys.add(relation.child.quoted(key));
                try(PreparedStatement statement=connection.prepareStatement("SELECT "+keys+" FROM "+relation.child.qualified()+" ORDER BY "+keys+" FOR UPDATE",ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
                    statement.setMaxRows(limit+1);statement.setQueryTimeout(10);statement.setFetchSize(product.contains("mysql")?Integer.MIN_VALUE:Math.min(100,limit+1));
                    List<Object[]> buffer=new ArrayList<>();
                    try(ResultSet rows=statement.executeQuery()) {
                        while(rows.next()) {
                            if(++relation.baselineCount>limit || ++fullCount[0]>100000)throw new IllegalArgumentException("OWNERSHIP_BASELINE_LIMIT");
                            String key=TargetRowSnapshot.json(relation.child.key(rows));fullCharacters+=key.length();if(key.length()>8192 || fullCharacters>16777216)throw new IllegalArgumentException("OWNERSHIP_BASELINE_KEY_LIMIT");
                            buffer.add(new Object[]{batch,relation.id,key});if(buffer.size()==100){baseline(buffer);buffer.clear();}
                        }
                    }
                    if(!buffer.isEmpty())baseline(buffer);
                }
            }
            jdbc.update("INSERT INTO faker_ownership_relation(batch_id,relation_id,ordinal,definition_json) VALUES(?,?,?,?)",batch,relation.id,relation.ordinal,TargetRowSnapshot.json(relation));
        }
        active.put(batch,relations);
    }
    private void baseline(List<Object[]> rows){local.executeWithoutResult(status->jdbc.batchUpdate("INSERT INTO faker_ownership_baseline(batch_id,relation_id,keys_json) VALUES(?,?,?)",rows));}

    private void compile(Connection connection,TargetRowSnapshot parent,List<DerivedRowsConfig> configs,Map<String,TargetRowSnapshot> originals,List<Relation> output) throws SQLException {
        if(configs==null)return;
        for(DerivedRowsConfig config:configs) {
            Relation relation=new Relation();relation.id="relation-"+output.size();relation.ordinal=output.size();relation.parent=parent;relation.parentPhysical=parent.physicalName();
            relation.child=originals.get(lower(config.getTable()));if(relation.child==null)relation.child=TargetRowSnapshot.inspectExisting(connection,config.getTable());
            if(parent.unsupportedReason!=null || relation.child.unsupportedReason!=null)throw new IllegalArgumentException("OWNERSHIP_REQUIRES_TRACKABLE_KEYS");
            if(!Objects.equals(parent.catalog,relation.child.catalog) || !Objects.equals(parent.schema,relation.child.schema))throw new IllegalArgumentException("OWNERSHIP_CROSS_SCOPE_UNSUPPORTED");
            JdbcTableTransactions.require(connection,relation.child.catalog,relation.child.name);
            Set<String> matched=new HashSet<>();
            for(Map.Entry<String,String> pair:config.getJoin().entrySet()) {
                TargetRowSnapshot.Column child=relation.child.columnInfo(pair.getKey()),key=parent.columnInfo(pair.getValue());
                if(!parent.primaryKeys.contains(key.name) || numeric(child.type)!=numeric(key.type) || (!numeric(child.type) && (!string(child.type) || !string(key.type))))throw new IllegalArgumentException("OWNERSHIP_JOIN_REQUIRES_COMPATIBLE_PARENT_PRIMARY_KEY");
                if(sensitive(child.name))throw new IllegalArgumentException("OWNERSHIP_SENSITIVE_JOIN_UNSUPPORTED");
                relation.join.put(child.name,key.name);matched.add(key.name);
            }
            if(!matched.equals(new HashSet<>(parent.primaryKeys)))throw new IllegalArgumentException("OWNERSHIP_JOIN_REQUIRES_COMPLETE_PARENT_KEY");
            if(config.getWhere()!=null)for(Map.Entry<String,Object> item:config.getWhere().entrySet()) {
                TargetRowSnapshot.Column column=relation.child.columnInfo(item.getKey());Object value=item.getValue();
                if(sensitive(column.name))throw new IllegalArgumentException("OWNERSHIP_SENSITIVE_PREDICATE_UNSUPPORTED");
                if(value!=null) {
                    if(string(column.type)){if(!(value instanceof String))throw new IllegalArgumentException("OWNERSHIP_STRING_PREDICATE_REQUIRED");}
                    else if(numeric(column.type)){try{value=new BigDecimal(value instanceof Boolean?((Boolean)value?"1":"0"):value.toString()).toPlainString();}catch(NumberFormatException e){throw new IllegalArgumentException("OWNERSHIP_NUMERIC_PREDICATE_REQUIRED");}}
                    else if(column.type!=Types.BOOLEAN && column.type!=Types.BIT)throw new IllegalArgumentException("OWNERSHIP_PREDICATE_TYPE_UNSUPPORTED");
                }
                if(relation.where.containsKey(column.name))throw new IllegalArgumentException("OWNERSHIP_AMBIGUOUS_PREDICATE");relation.where.put(column.name,value);
            }
            relation.index=index(connection,relation.child,relation.join.keySet());
            String mode=config.getFreshness()==null?"AUTO":config.getFreshness().toUpperCase(Locale.ROOT);boolean number=relation.child.primaryKeys.size()==1 && numeric(relation.child.columnInfo(relation.child.primaryKeys.get(0)).type);
            relation.mode=mode.equals("AUTO")?(number?"NUMERIC_HIGH_WATERMARK":"FULL_KEYS"):mode;
            if(relation.mode.equals("NUMERIC_HIGH_WATERMARK") && !number)throw new IllegalArgumentException("OWNERSHIP_NUMERIC_KEY_REQUIRED");
            relation.baselineCount=config.getBaselineLimit()==null?10000:config.getBaselineLimit();relation.maxRows=config.getMaxRows()==null?10000:config.getMaxRows();
            output.add(relation);compile(connection,relation.child,config.getDerivedRows(),originals,output);
        }
    }
    private static String index(Connection connection,TargetRowSnapshot table,Set<String> columns) throws SQLException {
        Map<String,TreeMap<Integer,String>> indexes=new TreeMap<>();int count=0;
        try(ResultSet rows=connection.getMetaData().getIndexInfo(table.catalog,table.schema,table.name,false,true)) {
            while(rows.next()) {
                if(++count>500)throw new IllegalArgumentException("OWNERSHIP_INDEX_METADATA_LIMIT");
                String name=rows.getString("INDEX_NAME"),column=rows.getString("COLUMN_NAME");
                if(name==null || column==null || rows.getShort("TYPE")==DatabaseMetaData.tableIndexHashed)continue;
                if(indexes.computeIfAbsent(name,k->new TreeMap<>()).put(rows.getInt("ORDINAL_POSITION"),column)!=null)throw new IllegalArgumentException("OWNERSHIP_AMBIGUOUS_INDEX_METADATA");
            }
        }
        for(Map.Entry<String,TreeMap<Integer,String>> index:indexes.entrySet()) {Set<String> prefix=new HashSet<>();for(int ordinal=1;ordinal<=columns.size();ordinal++)prefix.add(index.getValue().get(ordinal));if(prefix.equals(columns))return index.getKey();}
        throw new IllegalArgumentException("OWNERSHIP_JOIN_INDEX_REQUIRED: "+table.table);
    }

    List<Relation> relations(String batch){return jdbc.query("SELECT definition_json FROM faker_ownership_relation WHERE batch_id=? ORDER BY ordinal",(rs,n)->decode(rs.getString(1)),batch);}
    void evict(String batch){active.remove(batch);}
    List<Relation> direct(String batch,TargetRowSnapshot parent){List<Relation> result=new ArrayList<>();for(Relation relation:active.getOrDefault(batch,Collections.emptyList()))if(relation.parentPhysical.equals(parent.physicalName()))result.add(relation);return result;}
    void beforeWrite(String batch,Connection connection,List<Relation> direct,List<Map<String,String>> parentKeys) throws SQLException {
        for(Relation relation:direct)for(Candidate candidate:candidates(connection,relation,parentKeys,true))if(!originallyOwned(batch,relation.child.physicalName(),candidate.key))throw new IllegalArgumentException("OWNERSHIP_PREEXISTING_DERIVED_REFERENCE: "+relation.child.table);
    }
    boolean originallyOwned(String batch,String physical,Map<String,String> key){return !jdbc.queryForList("SELECT 1 FROM faker_generation_row r JOIN faker_generation_table t ON t.batch_id=r.batch_id AND t.table_name=r.table_name WHERE r.batch_id=? AND t.physical_name=? AND r.keys_json=? LIMIT 1",batch,physical,TargetRowSnapshot.json(key)).isEmpty();}
    boolean fresh(String batch,Relation relation,Map<String,String> key) {
        if(relation.mode.equals("NUMERIC_HIGH_WATERMARK"))return relation.watermark==null || new BigDecimal(key.values().iterator().next()).compareTo(new BigDecimal(relation.watermark))>0;
        return jdbc.queryForObject("SELECT COUNT(*) FROM faker_ownership_baseline WHERE batch_id=? AND relation_id=? AND keys_json=?",Integer.class,batch,relation.id,TargetRowSnapshot.json(key))==0;
    }
    List<Map<String,Object>> summary(String batch){List<Map<String,Object>> result=new ArrayList<>();for(Relation r:relations(batch)){Map<String,Object> item=new LinkedHashMap<>();item.put("table",r.child.table);item.put("parent",r.parent.table);item.put("freshness",r.mode);item.put("baselineCount",r.mode.equals("FULL_KEYS")?r.baselineCount:null);item.put("watermark",r.watermark);item.put("maxRows",r.maxRows);result.add(item);}return result;}

    static List<Candidate> candidates(Connection connection,Relation relation,List<Map<String,String>> parentKeys,boolean lock) throws SQLException {
        return candidates(connection,relation,parentKeys,lock,false);
    }
    static List<Candidate> candidates(Connection connection,Relation relation,List<Map<String,String>> parentKeys,boolean lock,boolean includeExcluded) throws SQLException {
        List<Candidate> result=new ArrayList<>();if(parentKeys.isEmpty())return result;
        boolean mysql=connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");TargetRowSnapshot table=relation.child;
        LinkedHashSet<String> fields=new LinkedHashSet<>(table.primaryKeys);fields.addAll(relation.join.keySet());StringJoiner projection=new StringJoiner(",");for(String field:fields)projection.add(table.quoted(field));
        Set<String> seen=new HashSet<>();int chunk=Math.max(1,Math.min(100,(480-relation.where.size())/relation.join.size()));
        for(int offset=0;offset<parentKeys.size();offset+=chunk) {
            List<Map<String,String>> keys=parentKeys.subList(offset,Math.min(parentKeys.size(),offset+chunk));StringJoiner alternatives=new StringJoiner(" OR ");List<Object> parameters=new ArrayList<>();
            for(Map<String,String> key:keys){StringJoiner join=new StringJoiner(" AND ");for(Map.Entry<String,String> column:relation.join.entrySet()){join.add(table.quoted(column.getKey())+"=?");parameters.add(key.get(column.getValue()));}alternatives.add("("+join+")");}
            StringBuilder sql=new StringBuilder("SELECT ").append(projection).append(" FROM ").append(table.qualified());if(mysql)sql.append(" FORCE INDEX (").append(table.quoted(relation.index)).append(")");sql.append(" WHERE (").append(alternatives).append(")");
            if(!includeExcluded)for(Map.Entry<String,Object> predicate:relation.where.entrySet()){sql.append(" AND ").append(table.quoted(predicate.getKey())).append(predicate.getValue()==null?" IS NULL":"=?");if(predicate.getValue()!=null)parameters.add(predicate.getValue());}
            if(lock)sql.append(" FOR UPDATE");
            try(PreparedStatement statement=connection.prepareStatement(sql.toString(),ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
                for(int i=0;i<parameters.size();i++)statement.setObject(i+1,parameters.get(i));statement.setMaxRows(relation.maxRows+1);statement.setQueryTimeout(10);statement.setFetchSize(mysql?Integer.MIN_VALUE:Math.min(100,relation.maxRows+1));
                try(ResultSet rows=statement.executeQuery()) {
                    while(rows.next()) {
                        Map<String,String> key=table.key(rows);String encoded=TargetRowSnapshot.json(key);if(!seen.add(encoded))continue;
                        if(result.size()>=relation.maxRows)throw new IllegalArgumentException("OWNERSHIP_CANDIDATE_LIMIT");
                        Map<String,String> parent=new LinkedHashMap<>();for(String field:relation.parent.primaryKeys)for(Map.Entry<String,String> join:relation.join.entrySet())if(join.getValue().equals(field)){Object value=rows.getObject(join.getKey());if(value==null)throw new IllegalArgumentException("OWNERSHIP_NULL_JOIN_VALUE");parent.put(field,relation.parent.canonical(field,value));}
                        result.add(new Candidate(key,parent));
                    }
                }
            }
        }
        return result;
    }
    private Relation decode(String text){try{return json.readValue(text,Relation.class);}catch(Exception e){throw new IllegalStateException("OWNERSHIP_RECORD_INVALID",e);}}
    static boolean numeric(int type){return Arrays.asList(Types.TINYINT,Types.SMALLINT,Types.INTEGER,Types.BIGINT,Types.NUMERIC,Types.DECIMAL).contains(type);}
    private static boolean string(int type){return type==Types.VARCHAR || type==Types.NVARCHAR;}
    private static boolean sensitive(String name){return name.toLowerCase(Locale.ROOT).matches("(?:.*_)?(?:password|passwd|pwd|secret|api_key|access_token|refresh_token)");}
    private static String lower(String name){return name.toLowerCase(Locale.ROOT);}
}
