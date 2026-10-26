package faker.data.batch;

import faker.data.domain.TableVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.*;

/** SQLite 账本与目标数据库事务分离；未知状态不会被当成已回滚。 */
@Service
public class GenerationBatchStore {
    private final JdbcTemplate jdbc;private final TransactionTemplate local;private final OwnershipJournal ownership;
    private final ObjectMapper json=new ObjectMapper();
    public GenerationBatchStore(JdbcTemplate jdbc) {
        this.jdbc=jdbc;this.local=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_generation_batch(batch_id TEXT PRIMARY KEY,task_id TEXT NOT NULL UNIQUE,state TEXT NOT NULL,identity_json TEXT NOT NULL,recipe_hash TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_generation_table(batch_id TEXT NOT NULL,table_name TEXT NOT NULL,ordinal INTEGER NOT NULL,descriptor_json TEXT NOT NULL,tracking TEXT NOT NULL,reason TEXT,executed_rows INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(batch_id,table_name))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_generation_row(row_id INTEGER PRIMARY KEY AUTOINCREMENT,batch_id TEXT NOT NULL,table_name TEXT NOT NULL,keys_json TEXT NOT NULL,fingerprint TEXT NOT NULL,UNIQUE(batch_id,table_name,keys_json))");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_generation_row_batch ON faker_generation_row(batch_id,row_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_generation_row_key ON faker_generation_row(keys_json,batch_id,table_name)");
        if(!jdbc.queryForList("PRAGMA table_info(faker_generation_row)").stream().anyMatch(c->"ownership_fingerprint".equals(c.get("name"))))jdbc.execute("ALTER TABLE faker_generation_row ADD COLUMN ownership_fingerprint TEXT");
        ownership=new OwnershipJournal(jdbc);
        if(!jdbc.queryForList("PRAGMA table_info(faker_generation_batch)").stream().anyMatch(c->"prepared_at".equals(c.get("name"))))jdbc.execute("ALTER TABLE faker_generation_batch ADD COLUMN prepared_at INTEGER");
        if(!jdbc.queryForList("PRAGMA table_info(faker_generation_batch)").stream().anyMatch(c->"ownership_scope".equals(c.get("name"))))jdbc.execute("ALTER TABLE faker_generation_batch ADD COLUMN ownership_scope TEXT");
        if(!jdbc.queryForList("PRAGMA table_info(faker_generation_table)").stream().anyMatch(c->"physical_name".equals(c.get("name"))))jdbc.execute("ALTER TABLE faker_generation_table ADD COLUMN physical_name TEXT");
        for(Map<String,Object> old:jdbc.queryForList("SELECT batch_id,identity_json FROM faker_generation_batch WHERE ownership_scope IS NULL"))jdbc.update("UPDATE faker_generation_batch SET ownership_scope=? WHERE batch_id=?",ownershipScope(decode((String)old.get("identity_json"))),old.get("batch_id"));
        for(Map<String,Object> old:jdbc.queryForList("SELECT batch_id,table_name FROM faker_generation_table WHERE physical_name IS NULL")) {
            TargetRowSnapshot descriptor=descriptor((String)old.get("batch_id"),(String)old.get("table_name"));
            if(descriptor.name!=null)jdbc.update("UPDATE faker_generation_table SET physical_name=? WHERE batch_id=? AND table_name=?",descriptor.physicalName(),old.get("batch_id"),old.get("table_name"));
        }
        jdbc.update("UPDATE faker_generation_batch SET state='UNKNOWN',updated_at=? WHERE state IN ('WRITING','PREPARED')",System.currentTimeMillis());
    }

    public void begin(String id,Connection target,List<TableVO> tables) throws SQLException {
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("BATCH_TASK_ID_REQUIRED");
        Map<String,Object> identity=identity(target);List<TargetRowSnapshot> descriptors=new ArrayList<>();
        for(TableVO table:tables)if(table.getExistingData()==null) {
            TargetRowSnapshot descriptor=TargetRowSnapshot.inspect(target,table);
            if(descriptor.name!=null)faker.data.service.db.JdbcTableTransactions.require(target,descriptor.catalog,descriptor.name);
            descriptors.add(descriptor);
        }
        String recipeHash=TargetRowSnapshot.sha(TargetRowSnapshot.json(tables));long now=System.currentTimeMillis();
        local.executeWithoutResult(status->{
            jdbc.update("INSERT INTO faker_generation_batch(batch_id,task_id,state,identity_json,recipe_hash,created_at,updated_at,ownership_scope) VALUES(?,?,'WRITING',?,?,?,?,?)",id,id,TargetRowSnapshot.json(identity),recipeHash,now,now,ownershipScope(identity));
            int ordinal=0;
            for(TargetRowSnapshot descriptor:descriptors)
                jdbc.update("INSERT INTO faker_generation_table(batch_id,table_name,ordinal,descriptor_json,tracking,reason,physical_name) VALUES(?,?,?,?,?,?,?)",id,descriptor.table,ordinal++,TargetRowSnapshot.json(descriptor),descriptor.unsupportedReason==null?"EXPLICIT_PRIMARY_KEY":"UNTRACKED",descriptor.unsupportedReason,descriptor.name==null?null:descriptor.physicalName());
        });
    }

    public TargetRowSnapshot descriptor(String id,String table) {
        String text=jdbc.queryForObject("SELECT descriptor_json FROM faker_generation_table WHERE batch_id=? AND table_name=?",String.class,id,table);
        try{return json.readValue(text,TargetRowSnapshot.class);}catch(Exception e){throw new IllegalStateException("BATCH_DESCRIPTOR_INVALID",e);}
    }
    public void captureOwnership(String id,Connection target,List<TableVO> tables) throws SQLException {
        if(!faker.data.service.generate.engine.OwnershipConfiguration.enabled(tables))return;
        Map<String,TargetRowSnapshot> originals=new LinkedHashMap<>();for(TableVO table:tables)if(table.getExistingData()==null)originals.put(table.getName().toLowerCase(Locale.ROOT),descriptor(id,table.getName()));
        ownership.capture(id,target,tables,originals);
    }
    OwnershipJournal ownership(){return ownership;}
    public void record(String id,String table,int executed,List<TargetRowSnapshot.Row> rows) {
        local.executeWithoutResult(status->{
            List<Object[]> parameters=new ArrayList<>();for(TargetRowSnapshot.Row row:rows)parameters.add(new Object[]{id,table,TargetRowSnapshot.json(row.primaryKey),row.fingerprint,row.ownershipFingerprint});
            if(!parameters.isEmpty())jdbc.batchUpdate("INSERT INTO faker_generation_row(batch_id,table_name,keys_json,fingerprint,ownership_fingerprint) VALUES(?,?,?,?,?)",parameters);
            jdbc.update("UPDATE faker_generation_table SET executed_rows=executed_rows+? WHERE batch_id=? AND table_name=?",executed,id,table);
        });
    }
    public void prepare(String id,Connection target) throws SQLException {
        // 后续表的触发器可能改动本批次先前写入的行，提交前统一保存最终快照。
        List<String> tables=jdbc.queryForList("SELECT table_name FROM faker_generation_table WHERE batch_id=? AND tracking='EXPLICIT_PRIMARY_KEY' ORDER BY ordinal",String.class,id);
        for(String name:tables) {
            TargetRowSnapshot descriptor=descriptor(id,name);int offset=0;
            while(true) {
                List<Map<String,String>> keys=jdbc.query("SELECT keys_json FROM faker_generation_row WHERE batch_id=? AND table_name=? ORDER BY row_id LIMIT 200 OFFSET ?",(rs,n)->stringMap(rs.getString(1)),id,name,offset);
                if(keys.isEmpty())break;
                List<TargetRowSnapshot.Row> observed=descriptor.read(target,keys);
                Set<String> expected=new HashSet<>(),actual=new HashSet<>();for(Map<String,String> key:keys)expected.add(TargetRowSnapshot.json(key));for(TargetRowSnapshot.Row row:observed)actual.add(TargetRowSnapshot.json(row.primaryKey));
                if(!expected.equals(actual))throw new IllegalStateException("BATCH_FINAL_KEYS_NOT_CONFIRMED");
                local.executeWithoutResult(status->{List<Object[]> parameters=new ArrayList<>();for(TargetRowSnapshot.Row row:observed)parameters.add(new Object[]{row.fingerprint,row.ownershipFingerprint,id,name,TargetRowSnapshot.json(row.primaryKey)});jdbc.batchUpdate("UPDATE faker_generation_row SET fingerprint=?,ownership_fingerprint=? WHERE batch_id=? AND table_name=? AND keys_json=?",parameters);});
                offset+=keys.size();
            }
        }
        local.executeWithoutResult(status->{
            transition(id,"PREPARED","WRITING");
            jdbc.update("UPDATE faker_generation_batch SET prepared_at=? WHERE batch_id=? AND state='PREPARED'",System.currentTimeMillis(),id);
        });
    }
    public void complete(String id,int completion) {
        String state=completion==org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED?"COMMITTED":completion==org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK?"ROLLED_BACK":"UNKNOWN";
        try{jdbc.update("UPDATE faker_generation_batch SET state=?,updated_at=? WHERE batch_id=? AND state IN ('WRITING','PREPARED')",state,System.currentTimeMillis(),id);}finally{ownership.evict(id);}
    }
    private void transition(String id,String state,String old){if(jdbc.update("UPDATE faker_generation_batch SET state=?,updated_at=? WHERE batch_id=? AND state=?",state,System.currentTimeMillis(),id,old)!=1)throw new IllegalStateException("BATCH_STATE_CONFLICT");}
    public void requireCommitted(String id){if(!"COMMITTED".equals(jdbc.queryForObject("SELECT state FROM faker_generation_batch WHERE batch_id=?",String.class,id)))throw new IllegalStateException("BATCH_COMMIT_RECEIPT_UNKNOWN: inspect batch before retrying");}

    public Map<String,Object> get(String id) {
        List<Map<String,Object>> rows=jdbc.query("SELECT * FROM faker_generation_batch WHERE batch_id=?",(rs,n)->map("batchId",rs.getString("batch_id"),"taskId",rs.getString("task_id"),"state",rs.getString("state"),"databaseIdentity",decode(rs.getString("identity_json")),"recipeHash",rs.getString("recipe_hash"),"createdAt",rs.getLong("created_at"),"updatedAt",rs.getLong("updated_at")),id);
        if(rows.isEmpty())throw new IllegalArgumentException("BATCH_NOT_FOUND");Map<String,Object> out=rows.get(0);
        List<Map<String,Object>> tables=jdbc.query("SELECT * FROM faker_generation_table WHERE batch_id=? ORDER BY ordinal",(rs,n)->map("table",rs.getString("table_name"),"order",rs.getInt("ordinal"),"tracking",rs.getString("tracking"),"reason",rs.getString("reason"),"stagedRows",rs.getInt("executed_rows")),id);
        int verified=0,untracked=0;for(Map<String,Object> table:tables)if("UNTRACKED".equals(table.get("tracking")))untracked+=((Number)table.get("stagedRows")).intValue();else verified+=((Number)table.get("stagedRows")).intValue();
        out.put("preparedAt",jdbc.queryForObject("SELECT prepared_at FROM faker_generation_batch WHERE batch_id=?",Long.class,id));
        boolean committed="COMMITTED".equals(out.get("state"));
        out.put("tables",tables);out.put("rowCount",committed?verified:0);out.put("stagedRowCount",verified);out.put("untrackedRows",committed?untracked:0);out.put("trackable",committed && untracked==0);
        out.put("declaredOwnership",ownership.summary(id));out.put("notes","Original insert snapshots are separate from declared later-derived ownership. Only bounded, verified relations can be adopted during cleanup preview. Reading a record does not delete data.");return out;
    }
    public Map<String,Object> rows(String id,int offset,int limit) {
        get(id);if(offset<0 || offset>1000000 || limit<1 || limit>200)throw new IllegalArgumentException("BATCH_PAGE_LIMIT");
        List<Map<String,Object>> rows=jdbc.query("SELECT * FROM faker_generation_row WHERE batch_id=? ORDER BY row_id LIMIT ? OFFSET ?",(rs,n)->map("table",rs.getString("table_name"),"primaryKey",decode(rs.getString("keys_json")),"fingerprint",rs.getString("fingerprint"),"ownershipFingerprint",rs.getString("ownership_fingerprint")==null?rs.getString("fingerprint"):rs.getString("ownership_fingerprint")),id,limit+1,offset);
        boolean more=rows.size()>limit;if(more)rows.remove(rows.size()-1);
        return map("batchId",id,"rows",rows,"offset",offset,"limit",limit,"truncated",more,"nextOffset",more?offset+rows.size():null,"ownership","Only COMMITTED snapshots are eligible for future ownership validation");
    }
    public void confirmRecoveredCommit(String id) {
        local.executeWithoutResult(status->{
            int updated=jdbc.update("UPDATE faker_generation_batch SET state='COMMITTED',updated_at=? WHERE batch_id=? AND state='UNKNOWN' AND prepared_at IS NOT NULL",System.currentTimeMillis(),id);
            if(updated==0 && !"COMMITTED".equals(get(id).get("state")))throw new IllegalStateException("BATCH_RECOVERY_STATE_CONFLICT");
            if(updated==1)jdbc.update("UPDATE faker_task SET state='FINISHED',progress=100,result='Commit confirmed from prepared row snapshots',error_msg=NULL,updated_at=? WHERE id=? AND state='ERROR'",System.currentTimeMillis(),id);
        });
    }
    public boolean hasOtherOwners(String id,Map<String,Object> identity,String physicalName,List<String> keys) {
        if(keys.size()>200){for(int offset=0;offset<keys.size();offset+=200)if(hasOtherOwners(id,identity,physicalName,keys.subList(offset,Math.min(keys.size(),offset+200))))return true;return false;}
        if(keys.isEmpty())return false;StringJoiner parameters=new StringJoiner(",");List<Object> args=new ArrayList<>();args.add(id);args.add(ownershipScope(identity));args.add(physicalName);
        for(String key:keys){parameters.add("?");args.add(key);}
        if(!jdbc.queryForList("SELECT 1 FROM faker_generation_row r JOIN faker_generation_batch b ON b.batch_id=r.batch_id JOIN faker_generation_table t ON t.batch_id=r.batch_id AND t.table_name=r.table_name WHERE b.batch_id<>? AND b.ownership_scope=? AND t.physical_name=? AND b.state NOT IN ('ROLLED_BACK','CLEANED') AND r.keys_json IN ("+parameters+") LIMIT 1",args.toArray()).isEmpty())return true;
        if(jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name='faker_cleanup_row'").isEmpty())return false;
        args.add(System.currentTimeMillis());
        return !jdbc.queryForList("SELECT 1 FROM faker_cleanup_row r JOIN faker_cleanup_plan p ON p.plan_id=r.plan_id JOIN faker_generation_batch b ON b.batch_id=p.batch_id WHERE b.batch_id<>? AND b.ownership_scope=? AND r.physical_name=? AND b.state NOT IN ('ROLLED_BACK','CLEANED') AND r.keys_json IN ("+parameters+") AND (p.state IN ('EXECUTING','PREPARED','UNKNOWN') OR p.state='READY' AND p.expires_at>=?) LIMIT 1",args.toArray()).isEmpty();
    }
    private static String ownershipScope(Map<String,Object> identity){return identity.get("serverId")==null?String.valueOf(identity.get("fingerprint")):TargetRowSnapshot.sha(String.valueOf(identity.get("product"))+"\n"+identity.get("serverId"));}
    public static Map<String,Object> identity(Connection connection) throws SQLException {
        DatabaseMetaData meta=connection.getMetaData();String product=meta.getDatabaseProductName();String endpoint=meta.getURL();
        endpoint=endpoint==null?"":endpoint.split("[?;]",2)[0];String server=null;
        if(product.toLowerCase(Locale.ROOT).contains("mysql"))try(Statement statement=connection.createStatement()) {statement.setQueryTimeout(5);try(ResultSet rows=statement.executeQuery("SELECT @@server_uuid")){if(rows.next())server=rows.getString(1);}}
        String catalog=connection.getCatalog(),schema=connection.getSchema();
        return map("product",product,"catalog",catalog,"schema",schema,"serverId",server,"strength",server==null?"CONNECTION_SCOPE":"SERVER_AND_CONNECTION_SCOPE","fingerprint",TargetRowSnapshot.sha(product+"\n"+endpoint+"\n"+catalog+"\n"+schema+"\n"+server));
    }
    private Map<String,Object> decode(String text){try{return json.readValue(text,new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("BATCH_RECORD_INVALID",e);}}
    private Map<String,String> stringMap(String text){try{return json.readValue(text,new TypeReference<LinkedHashMap<String,String>>(){});}catch(Exception e){throw new IllegalStateException("BATCH_KEY_INVALID",e);}}
    private static Map<String,Object> map(Object...pairs){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
}
