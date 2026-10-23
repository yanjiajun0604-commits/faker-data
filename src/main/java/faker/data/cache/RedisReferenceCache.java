package faker.data.cache;
import com.alibaba.fastjson.JSON;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;
/** 每个任务一个带 TTL 的哈希；清理只删除本任务的键。 */
public class RedisReferenceCache implements ReferenceCache {
    private final StringRedisTemplate redis;
    public RedisReferenceCache(StringRedisTemplate redis){this.redis=redis;}
    private String key(String id){return "faker:reference:"+id;}
    private String field(String column,String row){return column.length()+":"+column+row;}
    public void put(String task,String column,String row,Object value){
        String key=key(task);
        redis.opsForHash().put(key,field(column,row),JSON.toJSONString(value));
        redis.expire(key,18,TimeUnit.HOURS);
    }
    public Object get(String task,String column,String row){
        Object value=redis.opsForHash().get(key(task),field(column,row));
        return value==null?null:JSON.parse(value.toString());
    }
    public void removeTask(String id){redis.delete(key(id));}
}
