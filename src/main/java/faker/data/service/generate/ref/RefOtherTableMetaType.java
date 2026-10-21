package faker.data.service.generate.ref;

import faker.data.service.generate.MetaTypeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;


@Component
public class RefOtherTableMetaType extends AbstractRefMetaType {

    @Autowired
    RedisTemplate redisTemplate;

    @Override
    public String getCode() {
        return "REF.OTHER_TABLE";
    }

    @Override
    public String getTitle() {
        return "引用其他表字段";
    }

    @Override
    protected Object parseValue(String transId, MetaTypeVO reg, Map<String, Object> extendArg) {
        String[] arr = reg.getRefOtherTableColumn();
        // 统一小写构造 Redis key，与写入侧保持一致（AbstractJdbcService.genColumnValue）
        String key = transId + ":" + arr[0].toLowerCase() + ":" + arr[1].toLowerCase();
        String refRowId = extendArg.get("refRowId").toString();
        return redisTemplate.opsForHash().get(key, refRowId);
    }
}
