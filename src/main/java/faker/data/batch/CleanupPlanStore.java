package faker.data.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class CleanupPlanStore {
    public static final class Plan {
        public String id,batch,state,hash,manifest,foreignHash,report,result;public long expires;
    }
    private final JdbcTemplate jdbc;private final TransactionTemplate local;
    private final ObjectMapper json=new ObjectMapper();
    public CleanupPlanStore(JdbcTemplate jdbc,GenerationBatchStore batches) {
        this.jdbc=jdbc;local=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_cleanup_plan(plan_id TEXT PRIMARY KEY,batch_id TEXT NOT NULL,state TEXT NOT NULL,plan_hash TEXT NOT NULL,manifest_hash TEXT NOT NULL,foreign_hash TEXT NOT NULL,report_json TEXT NOT NULL,result_json TEXT,created_at INTEGER NOT NULL,expires_at INTEGER NOT NULL)");
        boolean previousContract=jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name='faker_cleanup_row'").isEmpty();
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_cleanup_row(row_id INTEGER PRIMARY KEY AUTOINCREMENT,plan_id TEXT NOT NULL,table_name TEXT NOT NULL,physical_name TEXT NOT NULL,keys_json TEXT NOT NULL,fingerprint TEXT NOT NULL,origin TEXT NOT NULL,relation_id TEXT,UNIQUE(plan_id,physical_name,keys_json))");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_cleanup_row_plan ON faker_cleanup_row(plan_id,row_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_cleanup_row_owner ON faker_cleanup_row(physical_name,keys_json,plan_id)");
        if(previousContract)jdbc.update("UPDATE faker_cleanup_plan SET state='INVALIDATED' WHERE state='READY'");
        jdbc.update("UPDATE faker_cleanup_plan SET state='UNKNOWN' WHERE state IN ('EXECUTING','PREPARED')");
        jdbc.update("UPDATE faker_generation_batch SET state='CLEANUP_UNKNOWN',updated_at=? WHERE state='CLEANING'",System.currentTimeMillis());
    }
    public Map<String,Object> save(String batch,Map<String,Object> report,String manifest,String foreignHash) {
        return save(batch,report,manifest,foreignHash,Collections.emptyMap());
    }
    Map<String,Object> save(String batch,Map<String,Object> report,String manifest,String foreignHash,Map<String,BatchCleanupChecks.Owned> owned) {
        String id=UUID.randomUUID().toString();long now=System.currentTimeMillis(),expires=now+600000;
        String hash=TargetRowSnapshot.sha(id+"\n"+UUID.randomUUID()+"\n"+batch+"\n"+manifest+"\n"+foreignHash+"\n"+expires);
        Map<String,Object> output=new LinkedHashMap<>(report);output.put("planId",id);output.put("planHash",hash);output.put("batchId",batch);output.put("expiresAt",expires);output.put("state",Boolean.TRUE.equals(report.get("eligible"))?"READY":"BLOCKED");
        local.executeWithoutResult(status->{
            jdbc.update("INSERT INTO faker_cleanup_plan(plan_id,batch_id,state,plan_hash,manifest_hash,foreign_hash,report_json,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?)",id,batch,output.get("state"),hash,manifest,foreignHash,TargetRowSnapshot.json(output),now,expires);
            if(Boolean.TRUE.equals(report.get("eligible")))for(Map.Entry<String,BatchCleanupChecks.Owned> entry:owned.entrySet()){
                BatchCleanupChecks.Owned table=entry.getValue();List<Object[]> buffer=new ArrayList<>();
                for(Map<String,String> key:table.keys){String encoded=TargetRowSnapshot.json(key);buffer.add(new Object[]{id,entry.getKey(),table.table.physicalName(),encoded,table.fingerprints.get(encoded),table.origins.get(encoded),table.relations.get(encoded)});if(buffer.size()==200){saveRows(buffer);buffer.clear();}}
                if(!buffer.isEmpty())saveRows(buffer);
            }
        });return output;
    }
    private void saveRows(List<Object[]> rows){jdbc.batchUpdate("INSERT INTO faker_cleanup_row(plan_id,table_name,physical_name,keys_json,fingerprint,origin,relation_id) VALUES(?,?,?,?,?,?,?)",rows);}
    public Map<String,Object> rows(String batch,String id,int offset,int limit){
        get(batch,id);if(offset<0 || offset>1000000 || limit<1 || limit>200)throw new IllegalArgumentException("CLEANUP_PAGE_LIMIT");
        List<Map<String,Object>> rows=jdbc.query("SELECT * FROM faker_cleanup_row WHERE plan_id=? ORDER BY row_id LIMIT ? OFFSET ?",(rs,n)->{Map<String,Object> row=new LinkedHashMap<>();row.put("table",rs.getString("table_name"));row.put("primaryKey",decode(rs.getString("keys_json")));row.put("fingerprint",rs.getString("fingerprint"));row.put("origin",rs.getString("origin"));row.put("relationId",rs.getString("relation_id"));return row;},id,limit+1,offset);
        boolean more=rows.size()>limit;if(more)rows.remove(rows.size()-1);Map<String,Object> result=new LinkedHashMap<>();result.put("planId",id);result.put("batchId",batch);result.put("rows",rows);result.put("offset",offset);result.put("limit",limit);result.put("truncated",more);result.put("nextOffset",more?offset+rows.size():null);return result;
    }
    public Plan get(String batch,String id) {
        List<Plan> rows=jdbc.query("SELECT * FROM faker_cleanup_plan WHERE batch_id=? AND plan_id=?",(rs,n)->{Plan p=new Plan();p.id=rs.getString("plan_id");p.batch=rs.getString("batch_id");p.state=rs.getString("state");p.hash=rs.getString("plan_hash");p.manifest=rs.getString("manifest_hash");p.foreignHash=rs.getString("foreign_hash");p.report=rs.getString("report_json");p.result=rs.getString("result_json");p.expires=rs.getLong("expires_at");return p;},batch,id);
        if(rows.isEmpty())throw new IllegalArgumentException("CLEANUP_PLAN_NOT_FOUND");return rows.get(0);
    }
    public Map<String,Object> receipt(String batch,String id) {
        Plan plan=get(batch,id);Map<String,Object> result=decode(plan.result==null?plan.report:plan.result);result.put("planId",id);result.put("batchId",batch);result.put("state",plan.state);result.put("eligible","READY".equals(plan.state) && Boolean.TRUE.equals(result.get("eligible")));return result;
    }
    public void claim(Plan plan) {
        local.executeWithoutResult(status->{
            if(jdbc.update("UPDATE faker_cleanup_plan SET state='EXECUTING' WHERE plan_id=? AND state='READY' AND expires_at>=?",plan.id,System.currentTimeMillis())!=1)throw new IllegalStateException("CLEANUP_PLAN_NOT_READY");
            if(jdbc.update("UPDATE faker_generation_batch SET state='CLEANING',updated_at=? WHERE batch_id=? AND state='COMMITTED'",System.currentTimeMillis(),plan.batch)!=1)throw new IllegalStateException("CLEANUP_BATCH_NOT_COMMITTED");
        });
    }
    public void prepared(Plan plan){if(jdbc.update("UPDATE faker_cleanup_plan SET state='PREPARED' WHERE plan_id=? AND state='EXECUTING'",plan.id)!=1)throw new IllegalStateException("CLEANUP_PLAN_STATE_CONFLICT");}
    public void done(Plan plan,Map<String,Object> result) {
        local.executeWithoutResult(status->{
            if(jdbc.update("UPDATE faker_cleanup_plan SET state='DONE',result_json=? WHERE plan_id=? AND state='PREPARED'",TargetRowSnapshot.json(result),plan.id)!=1)throw new IllegalStateException("CLEANUP_RECEIPT_UNKNOWN");
            if(jdbc.update("UPDATE faker_generation_batch SET state='CLEANED',updated_at=? WHERE batch_id=? AND state='CLEANING'",System.currentTimeMillis(),plan.batch)!=1)throw new IllegalStateException("CLEANUP_BATCH_STATE_CONFLICT");
        });
    }
    public void failed(Plan plan,boolean rolledBack,String reason) {
        local.executeWithoutResult(status->{
            jdbc.update("UPDATE faker_cleanup_plan SET state=?,result_json=? WHERE plan_id=? AND state IN ('EXECUTING','PREPARED')",rolledBack?"INVALIDATED":"UNKNOWN",TargetRowSnapshot.json(Collections.singletonMap("reason",reason)),plan.id);
            jdbc.update("UPDATE faker_generation_batch SET state=?,updated_at=? WHERE batch_id=? AND state='CLEANING'",rolledBack?"COMMITTED":"CLEANUP_UNKNOWN",System.currentTimeMillis(),plan.batch);
        });
    }
    private Map<String,Object> decode(String text){try{return json.readValue(text,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("CLEANUP_PLAN_INVALID",e);}}
}
