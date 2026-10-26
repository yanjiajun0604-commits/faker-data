package faker.data.batch;

import faker.data.service.generate.engine.JdbcBatchWriter;
import java.sql.*;
import java.util.*;

/** 每个 JDBC 批次只保留主键，成功写入后在同一事务内核对真实行。 */
public final class GenerationBatchRecorder implements JdbcBatchWriter.Observer {
    private final GenerationBatchStore store;private final String batch;
    private final TargetRowSnapshot table;
    private final List<Map<String,String>> keys=new ArrayList<>();private int count;
    public GenerationBatchRecorder(GenerationBatchStore store,String batch,String table){this.store=store;this.batch=batch;this.table=store.descriptor(batch,table);}
    @Override public void row(Object[] values){count++;if(table.unsupportedReason==null)keys.add(table.inputKey(values));}
    @Override public void written(Connection connection) throws SQLException {
        List<TargetRowSnapshot.Row> observed=table.unsupportedReason==null?table.read(connection,keys):Collections.emptyList();
        if(table.unsupportedReason==null) {
            Set<String> expected=new HashSet<>(),actual=new HashSet<>();for(Map<String,String> key:keys)expected.add(TargetRowSnapshot.json(key));for(TargetRowSnapshot.Row row:observed)actual.add(TargetRowSnapshot.json(row.primaryKey));
            if(expected.size()!=count || !expected.equals(actual))throw new IllegalStateException("BATCH_INSERTED_KEYS_NOT_CONFIRMED: "+table.table);
        }
        store.record(batch,table.table,count,observed);keys.clear();count=0;
    }
}
