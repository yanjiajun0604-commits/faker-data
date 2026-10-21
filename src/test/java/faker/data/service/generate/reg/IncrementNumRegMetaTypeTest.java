package faker.data.service.generate.reg;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.MetaTypeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 REG.INCREMENT_NUM（递增数值）规则的多规则隔离性。
 *
 * <p>核心场景：同一字段配置两条 INCREMENT_NUM 规则，各有独立的起始值和步长，
 * 生成数据时应互不干扰，各自从自己的起始值开始按自己的步长递增。
 */
class IncrementNumRegMetaTypeTest {

    private final IncrementNumRegMetaType metaType = new IncrementNumRegMetaType();

    @BeforeEach
    void resetCounters() throws Exception {
        Field field = IncrementNumRegMetaType.class.getDeclaredField("counters");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, AtomicLong> counters =
                (ConcurrentHashMap<String, AtomicLong>) field.get(null);
        counters.clear();
    }

    @Test
    @DisplayName("同一字段两条 REG.INCREMENT_NUM 规则使用不同起始值，应各自独立递增")
    void differentStartValues_shouldBeIndependent() {
        // 规则1：minSize=100, step=1
        MetaTypeVO reg1 = new MetaTypeVO();
        reg1.setMinSize(100);
        reg1.setStep(1);

        // 规则2：minSize=5000, step=10
        MetaTypeVO reg2 = new MetaTypeVO();
        reg2.setMinSize(5000);
        reg2.setStep(10);

        ColumnInfo column = new ColumnInfo();
        column.setName("order_no");
        String transId = "test-same-column";
        String tableName = "t_order";

        // 模拟生成 3 行数据，每行依次处理两条规则
        for (int row = 0; row < 3; row++) {
            long val1 = (long) metaType.doParse(reg1, transId, tableName, column);
            long val2 = (long) metaType.doParse(reg2, transId, tableName, column);

            // 规则1 序列: 100, 101, 102
            assertEquals(100L + row, val1,
                    "规则1 第 " + (row + 1) + " 次应返回 " + (100 + row));
            // 规则2 序列: 5000, 5010, 5020
            assertEquals(5000L + row * 10L, val2,
                    "规则2 第 " + (row + 1) + " 次应返回 " + (5000 + row * 10));
        }
    }

    @Test
    @DisplayName("同一字段多条 REG.INCREMENT_NUM 规则起始值相同时也应独立计数")
    void sameStartValues_shouldStillBeIndependent() {
        // 两条规则起始值相同，但步长不同
        MetaTypeVO reg1 = new MetaTypeVO();
        reg1.setMinSize(1);
        reg1.setStep(2);

        MetaTypeVO reg2 = new MetaTypeVO();
        reg2.setMinSize(1);
        reg2.setStep(3);

        ColumnInfo column = new ColumnInfo();
        column.setName("item_no");
        String transId = "test-same-start";
        String tableName = "t_item";

        // 验证 3 行
        for (int row = 0; row < 3; row++) {
            long val1 = (long) metaType.doParse(reg1, transId, tableName, column);
            long val2 = (long) metaType.doParse(reg2, transId, tableName, column);

            assertEquals(1L + row * 2L, val1,
                    "规则1 第 " + (row + 1) + " 次应返回 " + (1 + row * 2));
            assertEquals(1L + row * 3L, val2,
                    "规则2 第 " + (row + 1) + " 次应返回 " + (1 + row * 3));
        }
    }

    @Test
    @DisplayName("不同字段的 INCREMENT_NUM 规则互不干扰（回归验证原有隔离性）")
    void differentColumns_areIsolated() {
        MetaTypeVO reg = new MetaTypeVO();
        reg.setMinSize(10);
        reg.setStep(5);

        ColumnInfo colA = new ColumnInfo();
        colA.setName("col_a");
        ColumnInfo colB = new ColumnInfo();
        colB.setName("col_b");

        String transId = "test";
        String tableName = "t_user";

        long a1 = (long) metaType.doParse(reg, transId, tableName, colA);
        long b1 = (long) metaType.doParse(reg, transId, tableName, colB);

        assertEquals(10L, a1, "col_a 第一次应返回 10");
        assertEquals(10L, b1, "col_b 第一次应返回 10，与 col_a 互不干扰");

        long a2 = (long) metaType.doParse(reg, transId, tableName, colA);
        long b2 = (long) metaType.doParse(reg, transId, tableName, colB);

        assertEquals(15L, a2, "col_a 第二次应返回 15（10+5）");
        assertEquals(15L, b2, "col_b 第二次应返回 15（10+5）");
    }

    @Test
    @DisplayName("不同 transId 的任务间计数器互不干扰")
    void differentTransId_areIsolated() {
        MetaTypeVO reg = new MetaTypeVO();
        reg.setMinSize(1);
        reg.setStep(1);

        ColumnInfo column = new ColumnInfo();
        column.setName("id");

        // 两个任务
        long t1v1 = (long) metaType.doParse(reg, "trans-1", "t", column);
        long t2v1 = (long) metaType.doParse(reg, "trans-2", "t", column);

        assertEquals(1L, t1v1, "任务1 第一次应返回 1");
        assertEquals(1L, t2v1, "任务2 第一次也返回 1（不同 transId 隔离）");
    }

    @Test
    @DisplayName("默认参数验证：minSize=null 时从 1 开始，step=null 时步长为 1")
    void defaultParameterValues() {
        MetaTypeVO reg = new MetaTypeVO(); // minSize=null, step=null

        ColumnInfo column = new ColumnInfo();
        column.setName("seq");

        long v1 = (long) metaType.doParse(reg, "default", "t", column);
        assertEquals(1L, v1, "默认起始值应为 1");

        long v2 = (long) metaType.doParse(reg, "default", "t", column);
        assertEquals(2L, v2, "默认步长为 1，第二次返回 2");
    }
}
