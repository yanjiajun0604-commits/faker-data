package faker.data.cache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 显式指定独立测试 Redis 端口才执行；不清空 Redis 数据库。 */
@EnabledIfSystemProperty(named="faker.redis.test.port",matches="[0-9]+")
class RedisReferenceCacheTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;
    private RedisReferenceCache redis;
    @BeforeEach void connect(){
        factory=new LettuceConnectionFactory("127.0.0.1",Integer.parseInt(System.getProperty("faker.redis.test.port")));
        factory.afterPropertiesSet();template=new StringRedisTemplate(factory);redis=new RedisReferenceCache(template);
    }
    @AfterEach void close(){factory.destroy();}
    @Test void preservesValuesIsolationAndCleanup(){
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
        try {
            redis.put(a,"table:id","r","001");redis.put(b,"table:id","r",42);
            redis.put(a,"table:null","r",null);
            assertEquals("001",redis.get(a,"table:id","r"));assertEquals(42,redis.get(b,"table:id","r"));assertNull(redis.get(a,"table:null","r"));
            assertTrue(template.getExpire("faker:reference:"+a)>0);
            redis.removeTask(a);assertNull(redis.get(a,"table:id","r"));assertEquals(42,redis.get(b,"table:id","r"));
        } finally {redis.removeTask(a);redis.removeTask(b);}
    }
    @Test void reportsReferenceCacheMicrobenchmark(){
        ReferenceCache memory=new MemoryReferenceCache(20000,20000000);
        measure(memory,100);measure(redis,100);
        for(int round=1;round<=3;round++){
            double mem=measure(memory,2000),remote=measure(redis,2000);
            System.out.printf(Locale.ROOT,"REFERENCE_BENCH round=%d rows=2000 memory_ms=%.3f redis_ms=%.3f%n",round,mem,remote);
        }
    }
    private double measure(ReferenceCache cache,int count){
        String task=UUID.randomUUID().toString();long start=System.nanoTime();
        try {
            for(int i=0;i<count;i++)cache.put(task,"parent:id",Integer.toString(i),"value-"+i);
            for(int i=0;i<count;i++)assertEquals("value-"+i,cache.get(task,"parent:id",Integer.toString(i)));
        }finally{cache.removeTask(task);}
        return (System.nanoTime()-start)/1_000_000D;
    }
}
