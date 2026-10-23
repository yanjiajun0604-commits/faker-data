package faker.data.task;

import faker.data.cache.ReferenceCache;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

/** 单进程有界队列；SQLite 仅记录任务状态，不保存带凭据的请求。 */
@Service
public class LocalTaskService {
    private final JdbcTemplate jdbc;
    private final ReferenceCache cache;
    private final Map<String,AsyncTaskExecutor> executors=new HashMap<>();
    private final ThreadPoolExecutor pool;
    public LocalTaskService(JdbcTemplate jdbc,ReferenceCache cache,List<AsyncTaskExecutor> handlers,
            @Value("${faker.tasks.workers:2}") int workers,@Value("${faker.tasks.queue-capacity:100}") int capacity){
        if(workers<1 || capacity<1) throw new IllegalArgumentException("任务线程数和队列容量必须大于 0");
        this.jdbc=jdbc;this.cache=cache;
        for(AsyncTaskExecutor h:handlers) {
            if(executors.put(h.getTaskPoolType(),h)!=null) throw new IllegalStateException("任务类型重复");
        }
        pool=new ThreadPoolExecutor(workers,workers,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity));
    }
    public String submit(String type,Object args){
        AsyncTaskExecutor executor=executors.get(type);
        if(executor==null) throw new IllegalArgumentException("不支持的任务类型");
        String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        jdbc.update("INSERT INTO faker_task(id,type,state,progress,created_at,updated_at) VALUES(?,?,'WAITING',0,?,?)",id,type,now,now);
        try {pool.execute(new Job(id,executor,args));}
        catch(RejectedExecutionException ex){update(id,TaskState.ERROR,0,"提交失败","任务队列已满或服务正在关闭");throw new IllegalStateException("任务队列已满或服务正在关闭，请稍后重试");}
        return id;
    }
    public TaskProcessVO query(String id){
        List<TaskProcessVO> rows=jdbc.query("SELECT * FROM faker_task WHERE id=?",(rs,n)->
            new TaskProcessVO(rs.getDouble("progress"),TaskState.valueOf(rs.getString("state")),rs.getString("error_msg"),rs.getString("result")),id);
        return rows.isEmpty()?new TaskProcessVO(0D,TaskState.NONE,null,"任务不存在"):rows.get(0);
    }
    public List<Map<String,Object>> types(){
        List<Map<String,Object>> result=new ArrayList<>();
        for(AsyncTaskExecutor e:executors.values()){
            Map<String,Object> row=new LinkedHashMap<>();row.put("type",e.getTaskPoolType());row.put("title",e.getTitle());row.put("priority",0);result.add(row);
        }return result;
    }
    private void update(String id,TaskState state,double progress,String result,String error){
        jdbc.update("UPDATE faker_task SET state=?,progress=?,result=?,error_msg=?,updated_at=? WHERE id=?",state.name(),progress,result,error,System.currentTimeMillis(),id);
    }
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
                update(id,TaskState.ERROR,monitor.progress,"执行失败",safeError(ex));
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
        public void error(String result,Throwable ex){terminal=true;update(id,TaskState.ERROR,progress,result,safeError(ex));}
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
