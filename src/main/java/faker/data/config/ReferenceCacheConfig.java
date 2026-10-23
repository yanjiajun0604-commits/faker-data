package faker.data.config;
import faker.data.cache.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
@Configuration
public class ReferenceCacheConfig {
    @Bean
    @ConditionalOnProperty(name="faker.cache.backend",havingValue="memory",matchIfMissing=true)
    public ReferenceCache memoryCache(@Value("${faker.cache.max-entries:500000}") long entries,@Value("${faker.cache.max-bytes:134217728}") long bytes){return new MemoryReferenceCache(entries,bytes);}
    @Bean(destroyMethod="destroy")
    @ConditionalOnProperty(name="faker.cache.backend",havingValue="redis")
    public LettuceConnectionFactory redisFactory(@Value("${faker.cache.redis-host}") String host,@Value("${faker.cache.redis-port}") int port,
            @Value("${faker.cache.redis-database}") int database,@Value("${faker.cache.redis-password}") String password){
        RedisStandaloneConfiguration config=new RedisStandaloneConfiguration(host,port);config.setDatabase(database);
        if(!password.isEmpty())config.setPassword(RedisPassword.of(password));
        return new LettuceConnectionFactory(config);
    }
    @Bean
    @ConditionalOnProperty(name="faker.cache.backend",havingValue="redis")
    public ReferenceCache redisCache(LettuceConnectionFactory factory){
        StringRedisTemplate redis=new StringRedisTemplate(factory);
        try(RedisConnection connection=factory.getConnection()){connection.ping();}
        return new RedisReferenceCache(redis);
    }
}
