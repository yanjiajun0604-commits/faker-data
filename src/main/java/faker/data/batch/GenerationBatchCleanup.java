package faker.data.batch;

import faker.data.domain.*;
import faker.data.service.db.*;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Semaphore;

/** 预览绑定账本，执行重新锁定核查；目标提交不明时不重复删除。 */
@Service
public class GenerationBatchCleanup {
    private final CleanupPlanStore plans;private final BatchCleanupChecks checks;
    private final Semaphore operation=new Semaphore(1);
    public GenerationBatchCleanup(GenerationBatchStore batches,CleanupPlanStore plans) {
        this.plans=plans;checks=new BatchCleanupChecks(batches);
    }
    public Map<String,Object> preview(String batch,BatchCleanupRequest request) {
        acquire();
        try(Connection connection=open(request)) {
            try{connection.setReadOnly(true);}catch(SQLException ignored){}
            connection.setAutoCommit(false);
            BatchCleanupChecks.Check result;
            try{result=checks.inspect(connection,batch,false);}finally{connection.rollback();}
            return plans.save(batch,result.report,result.manifest,result.foreignHash);
        }catch(SQLException e){throw DatabaseConnectionException.from(e,request.getDataSource());}
        finally{operation.release();}
    }
    public Map<String,Object> get(String batch,String plan){return plans.receipt(batch,plan);}
    public Map<String,Object> execute(String batch,BatchCleanupRequest request) {
        if(!request.isConfirmDelete())throw new IllegalArgumentException("CLEANUP_CONFIRMATION_REQUIRED");
        acquire();Connection target=null;boolean claimed=false,commitAttempted=false;
        CleanupPlanStore.Plan plan=null;
        try {
            plan=plans.get(batch,request.getPlanId());
            if(!Objects.equals(plan.hash,request.getPlanHash()))throw new IllegalArgumentException("CLEANUP_PLAN_HASH_MISMATCH");
            if("DONE".equals(plan.state) || "UNKNOWN".equals(plan.state))return plans.receipt(batch,plan.id);
            if(!"READY".equals(plan.state))throw new IllegalArgumentException("CLEANUP_PLAN_NOT_READY");
            if(System.currentTimeMillis()>plan.expires)throw new IllegalArgumentException("CLEANUP_PLAN_EXPIRED");
            plans.claim(plan);claimed=true;
            target=open(request);target.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);target.setAutoCommit(false);
            BatchCleanupChecks.Check current=checks.inspect(target,batch,true);
            if(!Boolean.TRUE.equals(current.report.get("eligible")) || !Objects.equals(plan.manifest,current.manifest) || !Objects.equals(plan.foreignHash,current.foreignHash))throw new IllegalStateException("CLEANUP_PLAN_STALE: "+current.blockers);
            if(System.currentTimeMillis()>plan.expires)throw new IllegalStateException("CLEANUP_PLAN_EXPIRED");
            delete(target,current);
            requireAbsent(target,current);
            plans.prepared(plan);commitAttempted=true;target.commit();target.close();target=null;
            // 必须使用独立连接确认，不拿当前事务内的结果冒充提交后复核。
            try(Connection verification=open(request)) {
                try{verification.setReadOnly(true);}catch(SQLException ignored){}
                verification.setAutoCommit(false);
                try {
                    Map<?,?> expected=(Map<?,?>)current.report.get("databaseIdentity");
                    if(!Objects.equals(expected.get("fingerprint"),GenerationBatchStore.identity(verification).get("fingerprint")))throw new IllegalStateException("CLEANUP_POSTCHECK_IDENTITY_CHANGED");
                    requireAbsent(verification,current);
                }finally{verification.rollback();}
            }
            Map<String,Object> result=new LinkedHashMap<>();result.put("deletedRows",current.rowCount);result.put("verifiedAbsent",true);result.put("deleteOrder",current.order);result.put("notes","Only recorded original keys were deleted. Source/original rows, sequence counters and normal audit logs were not targeted.");plans.done(plan,result);return plans.receipt(batch,plan.id);
        } catch(Exception failure) {
            if(claimed) {
                boolean rollbackConfirmed=!commitAttempted && target==null;
                if(target!=null)try{target.rollback();rollbackConfirmed=!commitAttempted;}catch(SQLException ignored){rollbackConfirmed=false;}
                try{plans.failed(plan,rollbackConfirmed,commitAttempted?"CLEANUP_OUTCOME_UNKNOWN: inspect the same plan, do not repeat deletion":"CLEANUP_ABORTED_AND_ROLLED_BACK");}catch(RuntimeException journalFailure){failure.addSuppressed(journalFailure);}
            }
            if(failure instanceof SQLException)throw DatabaseConnectionException.from((SQLException)failure,request.getDataSource());
            if(failure instanceof RuntimeException)throw (RuntimeException)failure;
            throw new IllegalStateException("CLEANUP_FAILED",failure);
        } finally {
            if(target!=null)try{target.close();}catch(SQLException ignored){}
            operation.release();
        }
    }
    protected Connection open(BatchCleanupRequest request) throws SQLException{return JdbcServiceFactory.getJdbcService(request.getDataSource()).getDrivierDatasource().getConnection();}
    private void acquire(){if(!operation.tryAcquire())throw new IllegalStateException("CLEANUP_BUSY: one preview/execution is already active; inspect existing receipts and retry later");}
    private static void delete(Connection connection,BatchCleanupChecks.Check current) throws SQLException {
        for(String name:current.order) {
            BatchCleanupChecks.Owned owned=current.owned.get(name);TargetRowSnapshot table=owned.table;StringJoiner where=new StringJoiner(" AND ");for(String key:table.primaryKeys)where.add(table.quoted(key)+"=?");
            try(PreparedStatement statement=connection.prepareStatement("DELETE FROM "+table.qualified()+" WHERE "+where)) {
                statement.setQueryTimeout(10);int buffered=0;
                for(Map<String,String> key:owned.keys) {
                    int parameter=1;for(String field:table.primaryKeys)statement.setString(parameter++,key.get(field));statement.addBatch();buffered++;
                    if(buffered==100){counts(statement.executeBatch(),buffered);statement.clearBatch();buffered=0;}
                }
                if(buffered>0)counts(statement.executeBatch(),buffered);
            }
        }
    }
    private static void counts(int[] values,int expected) throws SQLException {if(values.length!=expected)throw new SQLException("CLEANUP_DELETE_COUNT_MISMATCH");for(int value:values)if(value!=1 && value!=Statement.SUCCESS_NO_INFO)throw new SQLException("CLEANUP_DELETE_COUNT_MISMATCH");}
    private static void requireAbsent(Connection connection,BatchCleanupChecks.Check check) throws SQLException {for(BatchCleanupChecks.Owned owned:check.owned.values())if(!owned.table.read(connection,owned.keys).isEmpty())throw new IllegalStateException("CLEANUP_POSTCHECK_ROWS_REMAIN");}
}
