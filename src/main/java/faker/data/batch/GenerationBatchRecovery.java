package faker.data.batch;

import faker.data.domain.*;
import faker.data.service.db.*;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.*;

/** 核对未知回执，只在明确确认且快照完整匹配时更新本机账本。 */
@Service
public class GenerationBatchRecovery {
    private final GenerationBatchStore store;
    public GenerationBatchRecovery(GenerationBatchStore store){this.store=store;}
    @SuppressWarnings("unchecked") public Map<String,Object> reconcile(String id,BatchReconcileRequest request) {
        Map<String,Object> batch=store.get(id);Map<String,Object> report=new LinkedHashMap<>();report.put("batchId",id);report.put("recovered",false);report.put("eligible",false);
        if(!"UNKNOWN".equals(batch.get("state"))){report.put("observation","STATE_DOES_NOT_REQUIRE_RECOVERY");report.put("state",batch.get("state"));return report;}
        List<Map<String,Object>> tables=(List<Map<String,Object>>)batch.get("tables");
        if(batch.get("preparedAt")==null || tables.stream().anyMatch(t->"UNTRACKED".equals(t.get("tracking")) && ((Number)t.get("stagedRows")).intValue()>0)) {
            report.put("observation","INCOMPLETE_OR_UNTRACKED_SNAPSHOT");report.put("state","UNKNOWN");return report;
        }
        try(Connection connection=JdbcServiceFactory.getJdbcService(request.getDataSource()).getDrivierDatasource().getConnection()) {
            try{connection.setReadOnly(true);}catch(SQLException ignored){}
            connection.setAutoCommit(false);
            try {
                Map<String,Object> expectedIdentity=(Map<String,Object>)batch.get("databaseIdentity");
                if(!Objects.equals(expectedIdentity.get("fingerprint"),GenerationBatchStore.identity(connection).get("fingerprint")))throw new IllegalArgumentException("BATCH_DATABASE_IDENTITY_MISMATCH");
                Map<String,TargetRowSnapshot> descriptors=new HashMap<>();
                for(Map<String,Object> table:tables) {
                    if(((Number)table.get("stagedRows")).intValue()==0)continue;
                    String name=(String)table.get("table");TargetRowSnapshot expected=store.descriptor(id,name);
                    TableVO configured=new TableVO();configured.setName(name);List<ColumnInfo> cols=new ArrayList<>();for(TargetRowSnapshot.Column actual:expected.columns){ColumnInfo col=new ColumnInfo();col.setName(actual.name);cols.add(col);}configured.setColumns(cols);
                    TargetRowSnapshot current=TargetRowSnapshot.inspect(connection,configured);
                    if(!Objects.equals(expected.schemaHash,current.schemaHash))throw new IllegalArgumentException("BATCH_SCHEMA_CHANGED");descriptors.put(name,expected);
                }
                int seen=0,found=0,matched=0,offset=0;
                while(true) {
                    Map<String,Object> page=store.rows(id,offset,200);List<Map<String,Object>> rows=(List<Map<String,Object>>)page.get("rows");if(rows.isEmpty())break;
                    Map<String,List<Map<String,Object>>> grouped=new LinkedHashMap<>();for(Map<String,Object> row:rows)grouped.computeIfAbsent((String)row.get("table"),k->new ArrayList<>()).add(row);
                    for(Map.Entry<String,List<Map<String,Object>>> group:grouped.entrySet()) {
                        List<Map<String,String>> keys=new ArrayList<>();Map<String,String> fingerprints=new HashMap<>();
                        for(Map<String,Object> row:group.getValue()){Map<String,String> key=(Map<String,String>)row.get("primaryKey");keys.add(key);fingerprints.put(TargetRowSnapshot.json(key),(String)row.get("fingerprint"));}
                        TargetRowSnapshot descriptor=descriptors.get(group.getKey());if(descriptor==null)throw new IllegalStateException("BATCH_JOURNAL_INCONSISTENT");
                        for(TargetRowSnapshot.Row current:descriptor.read(connection,keys)){found++;if(Objects.equals(fingerprints.get(TargetRowSnapshot.json(current.primaryKey)),current.fingerprint))matched++;}
                    }
                    seen+=rows.size();if(!Boolean.TRUE.equals(page.get("truncated")))break;offset=(Integer)page.get("nextOffset");
                }
                int expected=((Number)batch.get("stagedRowCount")).intValue();
                boolean complete=seen==expected && found==seen && matched==seen;
                report.put("expectedRows",expected);report.put("foundRows",found);report.put("matchingRows",matched);report.put("eligible",complete);
                report.put("observation",complete?"ALL_MATCHING":found==0?"MISSING_ALL":"CHANGED_OR_MISSING");
            } finally {connection.rollback();}
        } catch(SQLException error){throw DatabaseConnectionException.from(error,request.getDataSource());}
        if(request.isConfirmCommit() && Boolean.TRUE.equals(report.get("eligible"))){store.confirmRecoveredCommit(id);report.put("recovered",true);}
        report.put("state",store.get(id).get("state"));report.put("notes","No target data was changed or replayed. Missing rows alone do not prove rollback. Explicit commit confirmation assumes matching rows were not identically recreated by another writer.");return report;
    }
}
