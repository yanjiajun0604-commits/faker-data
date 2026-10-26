package faker.data.task;

import faker.data.cache.ReferenceCache;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import faker.data.batch.TargetRowSnapshot;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 单进程有界队列；SQLite 仅记录任务状态，不保存带凭据的请求。 */
@Service
public class LocalTaskService {
    private final JdbcTemplate jdbc;
    private final ReferenceCache cache;
    private final Map<String,AsyncTaskExecutor> executors=new HashMap<>();
    private final ThreadPoolExecutor pool;
    private final TransactionTemplate local;
    private final ObjectMapper json=new ObjectMapper();
    public LocalTaskService(JdbcTemplate jdbc,ReferenceCache cache,List<AsyncTaskExecutor> handlers,
            @Value("${faker.tasks.workers:2}") int workers,@Value("${faker.tasks.queue-capacity:100}") int capacity){
        if(workers<1 || capacity<1) throw new IllegalArgumentException("任务线程数和队列容量必须大于 0");
        this.jdbc=jdbc;this.cache=cache;
        this.local=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_generation_request(request_id TEXT PRIMARY KEY,payload_hash TEXT NOT NULL,task_id TEXT NOT NULL UNIQUE,created_at INTEGER NOT NULL)");
        if(!jdbc.queryForList("PRAGMA table_info(faker_task)").stream().anyMatch(c->"error_detail".equals(c.get("name"))))jdbc.execute("ALTER TABLE faker_task ADD COLUMN error_detail TEXT");
        for(AsyncTaskExecutor h:handlers) {
            if(executors.put(h.getTaskPoolType(),h)!=null) throw new IllegalStateException("任务类型重复");
        }
        pool=new ThreadPoolExecutor(workers,workers,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity));
    }
    public String submit(String type,Object args){
        AsyncTaskExecutor executor=executors.get(type);
        if(executor==null) throw new IllegalArgumentException("不支持的任务类型");
        String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        Object rawKey=args instanceof Map?((Map<?,?>)args).get("requestId"):null;
        String request=rawKey==null?null:requestKey(rawKey);
        if(request!=null) {
            String hash=payloadHash(type,args);
            String claimed=local.execute(status->{
                // 首条语句先抢写锁，避免两个读事务同时升级 SQLite WAL 写锁。
                int inserted=jdbc.update("INSERT OR IGNORE INTO faker_generation_request(request_id,payload_hash,task_id,created_at) VALUES(?,?,?,?)",request,hash,id,now);
                if(inserted==0) {
                    Map<String,Object> existing=jdbc.queryForMap("SELECT payload_hash,task_id FROM faker_generation_request WHERE request_id=?",request);
                    if(!hash.equals(existing.get("payload_hash")))throw new IllegalArgumentException("IDEMPOTENCY_CONFLICT: requestId already belongs to a different payload");
                    return (String)existing.get("task_id");
                }
                insertTask(id,type,now);return id;
            });
            if(!id.equals(claimed))return claimed;
        } else insertTask(id,type,now);
        try {pool.execute(new Job(id,executor,args));}
        catch(RejectedExecutionException ex){update(id,TaskState.ERROR,0,"提交失败","任务队列已满或服务正在关闭");throw new IllegalStateException("任务队列已满或服务正在关闭，请稍后重试");}
        return id;
    }
    private void insertTask(String id,String type,long now){jdbc.update("INSERT INTO faker_task(id,type,state,progress,created_at,updated_at) VALUES(?,?,'WAITING',0,?,?)",id,type,now,now);}
    public Map<String,Object> findRequest(String key) {
        requestKey(key);List<String> ids=jdbc.queryForList("SELECT task_id FROM faker_generation_request WHERE request_id=?",String.class,key);
        Map<String,Object> result=new LinkedHashMap<>();result.put("requestId",key);result.put("found",!ids.isEmpty());
        if(!ids.isEmpty()){result.put("taskId",ids.get(0));result.put("state",query(ids.get(0)).getState());}return result;
    }
    private static String requestKey(Object raw){if(!(raw instanceof String) || !((String)raw).matches("[A-Za-z0-9][A-Za-z0-9_-]{7,99}"))throw new IllegalArgumentException("requestId must contain 8-100 ASCII letters, digits, underscores or hyphens");return (String)raw;}
    @SuppressWarnings("unchecked") private static String payloadHash(String type,Object args) {
        try {
            ObjectMapper mapper=new ObjectMapper();Map<String,Object> value=mapper.readValue(mapper.writeValueAsString(args),new TypeReference<Map<String,Object>>(){});value.remove("requestId");
            Object source=value.get("dataSource");
            if(source instanceof Map) {
                Map<String,Object> connection=(Map<String,Object>)source;connection.remove("password");
                Object url=connection.get("url");if(url instanceof String)connection.put("url",((String)url).replaceAll("(?i)(password|passwd|pwd|token|secret|api_key)=([^&;]+)","$1=REDACTED"));
            }
            return TargetRowSnapshot.sha(type+"\n"+mapper.writeValueAsString(canonical(value)));
        }catch(java.io.IOException e){throw new IllegalArgumentException("Invalid idempotent generation request");}
    }
    private static Object canonical(Object value) {
        if(value instanceof Map){Map<String,Object> sorted=new TreeMap<>();((Map<?,?>)value).forEach((k,v)->sorted.put(String.valueOf(k),canonical(v)));return sorted;}
        if(value instanceof List){List<Object> ordered=new ArrayList<>();for(Object item:(List<?>)value)ordered.add(canonical(item));return ordered;}return value;
    }
    public TaskProcessVO query(String id){
        List<TaskProcessVO> rows=jdbc.query("SELECT * FROM faker_task WHERE id=?",(rs,n)->{
            TaskProcessVO result=new TaskProcessVO(rs.getDouble("progress"),TaskState.valueOf(rs.getString("state")),rs.getString("error_msg"),rs.getString("result"));
            String detail=rs.getString("error_detail");if(result.getState()==TaskState.ERROR && detail!=null)try{result.setDiagnostic(json.readValue(detail,new TypeReference<Map<String,Object>>(){}));}catch(java.io.IOException invalid){throw new IllegalStateException("TASK_DIAGNOSTIC_INVALID",invalid);}return result;
        },id);
        return rows.isEmpty()?new TaskProcessVO(0D,TaskState.NONE,null,"任务不存在"):rows.get(0);
    }
    public List<Map<String,Object>> types(){
        List<Map<String,Object>> result=new ArrayList<>();
        for(AsyncTaskExecutor e:executors.values()){
            Map<String,Object> row=new LinkedHashMap<>();row.put("type",e.getTaskPoolType());row.put("title",e.getTitle());row.put("priority",0);result.add(row);
        }return result;
    }
    private void update(String id,TaskState state,double progress,String result,String error){
        jdbc.update("UPDATE faker_task SET state=?,progress=?,result=?,error_msg=?,error_detail=NULL,updated_at=? WHERE id=?",state.name(),progress,result,error,System.currentTimeMillis(),id);
    }
    private void failed(String id,double progress,String result,Throwable error){Map<String,Object> detail=faker.data.service.generate.engine.GenerationProblem.find(error);jdbc.update("UPDATE faker_task SET state='ERROR',progress=?,result=?,error_msg=?,error_detail=?,updated_at=? WHERE id=?",progress,result,safeError(error),detail==null?null:TargetRowSnapshot.json(detail),System.currentTimeMillis(),id);}
    private class Job implements Runnable {
        final String id;final AsyncTaskExecutor executor;final Object args;
        Job(String id,AsyncTaskExecutor executor,Object args){this.id=id;this.executor=executor;this.args=args;}
        @Override public void run(){
            Monitor monitor=new Monitor(id);
            try {
                executor.setTaskId(id);update(id,TaskState.PROCESSING,0,"执行中",null);
                executor.execute(args,monitor);
                if(!monitor.terminal) update(id,TaskState.FINISHED,100,"执行完成",null);
            }catch(Throwable ex){
                failed(id,monitor.progress,"执行失败",ex);
                if(ex instanceof VirtualMachineError) throw (VirtualMachineError)ex;
            }finally{
                try{
                    faker.data.service.generate.reg.IncrementNumRegMetaType.clearTask(id);
                    cache.removeTask(id);
                }finally{AsyncTaskExecutor.taskId.remove();}
            }
        }
    }
    private static String safeError(Throwable ex){
        String text=ex.getMessage();if(text==null) text=ex.getClass().getSimpleName();
        // 驱动错误可能包含带密码的 URL；不把这些内容写入任务数据库。
        text=text.replaceAll("(?i)(password|pwd|user|username)=([^;&\\s]+)","$1=***");
        return text.length()>1000?text.substring(0,1000):text;
    }
    private class Monitor implements AsyncTaskMonitor {
        final String id;double progress;boolean terminal;
        Monitor(String id){this.id=id;}
        public String progressAndMessage(AsyncTaskExecutor e,double value,String message){
            if(!terminal){progress=Math.max(progress,Math.min(99,Double.isFinite(value)?value:0));update(id,TaskState.PROCESSING,progress,message,null);}
            return String.format(java.util.Locale.ROOT,"%.1f",progress);
        }
        public boolean isCancel(){return Thread.currentThread().isInterrupted();}
        // 完成状态统一在 execute 正常返回后写入，避免事务提交前出现 100%。
        public void finish(String result,Object detail){}
        public void canceling(String result,Object detail){canceled(result,detail);}
        public void canceled(String result,Object detail){terminal=true;update(id,TaskState.CANCELED,progress,result,null);}
        public void error(String result,Throwable ex){terminal=true;failed(id,progress,result,ex);}
        public boolean isFinish(){return terminal;}
        public TaskProcessVO queryProcess(String taskId){return query(taskId);}
    }
    @PreDestroy public void close(){
        pool.shutdown();
        try{if(pool.awaitTermination(30,TimeUnit.SECONDS)) return;}
        catch(InterruptedException ex){Thread.currentThread().interrupt();}
        for(Runnable r:pool.shutdownNow()) {
            Job job=(Job)r;update(job.id,TaskState.ERROR,0,"服务关闭","排队任务未执行，请重新提交");
        }
    }
}
