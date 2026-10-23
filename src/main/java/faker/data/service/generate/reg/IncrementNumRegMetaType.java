package faker.data.service.generate.reg;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.MetaTypeVO;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 递增数值：每行 +步长，从起始值开始顺序递增。
 *
 * <p>与 REG.RANGE_NUM（随机）的区别：本策略是确定的、连续的，常用于主键、序号、流水号等场景。
 *
 * <p>计数器按 "transId:tableName:columnName" 隔离，
 * 同一任务内不同列互不干扰；不同任务天然隔离（transId 不同）。
 */
@Component
public class IncrementNumRegMetaType extends AbstractRegMetaType {

    public static final String code = "REG.INCREMENT_NUM";

    /**
     * 每个列的独立计数器。
     * 任务结束后由 LocalTaskService 统一释放，不影响其他正在执行的任务。
     */
    private static final ConcurrentHashMap<String, AtomicLong> counters =
            new ConcurrentHashMap<>();

    public static void clearTask(String taskId) {
        if (taskId != null) counters.keySet().removeIf(key -> key.startsWith(taskId + ":"));
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "递增数值";
    }

    /**
     * 需要 transId/curTableName/columnInfo 构造计数器 key，故重写 doParse 而非 parseValue。
     * <p>key 包含 System.identityHashCode(reg) 以区分同一字段上的多条递增规则，
     * 避免多条规则共享同一个计数器导致起始值被覆盖、步长互相叠加的问题。</p>
     */
    @Override
    protected Object doParse(MetaTypeVO reg, String transId, String curTableName, ColumnInfo columnInfo) {
        long start = reg.getMinSize() == null ? 1L : reg.getMinSize().longValue();
        long step = reg.getStep() == null ? 1L : reg.getStep().longValue();

        // 追加规则对象标识，使同一字段上的多条 INCREMENT_NUM 规则各自独立计数
        String key = transId + ":" + curTableName + ":" + columnInfo.getName()
                + ":" + System.identityHashCode(reg);
        AtomicLong counter = counters.computeIfAbsent(key, k -> new AtomicLong(start));
        return counter.getAndAdd(step);
    }

    /**
     * AbstractRegMetaType 抽象方法要求，实际由 doParse 接管，不会走到此方法。
     */
    @Override
    protected Object parseValue(MetaTypeVO reg) {
        return null;
    }
}
