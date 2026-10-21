package faker.data.service.generate;

import faker.data.domain.ColumnInfo;
import faker.data.domain.DataSource;
import faker.data.service.db.AbstractJdbcService;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 条件规则集成测试：
 * - 验证列按条件依赖排序后，条件引用列在条件列之前
 * - 验证排序/循环依赖检测不依赖 Spring 上下文（纯算法测试）
 */
class ConditionRuleIntegrationTest {

    /**
     * 创建一个不触发数据库连接、能通过 checkDataSource 校验的 AbstractJdbcService 实例。
     * 两个 private 算法方法不依赖任何实例字段，因此使用空的匿名子类即可。
     */
    private static final AbstractJdbcService SERVICE = new AbstractJdbcService(createValidDataSource()) {
        @Override
        protected String loadDriverClass() {
            return "com.mysql.cj.jdbc.Driver";
        }

        @Override
        protected String schemaPattern() {
            return null;
        }
    };

    private static DataSource createValidDataSource() {
        DataSource ds = new DataSource();
        ds.setUrl("jdbc:mysql://localhost:3306/test");
        ds.setUsername("root");
        ds.setPassword("root");
        return ds;
    }

    @Test
    void testSortByConditionDeps_simpleDependency() {
        // user_type 被 discount 的条件规则引用 → user_type 应在 discount 前
        List<ColumnInfo> columns = new ArrayList<>();

        ColumnInfo discount = new ColumnInfo();
        discount.setName("discount");
        MetaTypeVO condReg = new MetaTypeVO();
        condReg.setCode("COND.EXPR");
        condReg.setCondition("#user_type == 'vip'");
        MetaTypeVO trueRule = new MetaTypeVO();
        trueRule.setCode("REG.RANGE_NUM");
        trueRule.setMinSize(1);
        trueRule.setMaxSize(1);
        condReg.setOnTrue(Collections.singletonList(trueRule));
        condReg.setOnFalse(Collections.singletonList(trueRule));
        discount.setRegs(Collections.singletonList(condReg));

        ColumnInfo userType = new ColumnInfo();
        userType.setName("user_type");
        MetaTypeVO enumReg = new MetaTypeVO();
        enumReg.setCode("RES.INPUTAREA");
        userType.setRegs(Collections.singletonList(enumReg));

        // 原始顺序: discount 在 user_type 之前（不应该）
        columns.add(discount);
        columns.add(userType);

        // 通过反射调用 sortColumnsByConditionDeps（绕过 Spring 上下文）
        List<ColumnInfo> sorted = invokeSort(columns);

        // user_type 应在 discount 之前
        assertEquals("user_type", sorted.get(0).getName());
        assertEquals("discount", sorted.get(1).getName());
    }

    @Test
    void testSortByConditionDeps_noDependency_preservesOrder() {
        List<ColumnInfo> columns = new ArrayList<>();

        ColumnInfo colA = new ColumnInfo();
        colA.setName("name");
        colA.setRegs(new ArrayList<>());

        ColumnInfo colB = new ColumnInfo();
        colB.setName("age");
        colB.setRegs(new ArrayList<>());

        columns.add(colA);
        columns.add(colB);

        List<ColumnInfo> sorted = invokeSort(columns);

        assertEquals("name", sorted.get(0).getName());
        assertEquals("age", sorted.get(1).getName());
    }

    @Test
    void testSortByConditionDeps_circularDependency_throwsException() {
        // A 条件引用 B，B 条件引用 A → 应抛异常
        List<ColumnInfo> columns = new ArrayList<>();

        ColumnInfo colA = new ColumnInfo();
        colA.setName("A");
        MetaTypeVO regA = new MetaTypeVO();
        regA.setCode("COND.EXPR");
        regA.setCondition("#B > 0");
        regA.setOnTrue(new ArrayList<>());
        regA.setOnFalse(new ArrayList<>());
        colA.setRegs(Collections.singletonList(regA));

        ColumnInfo colB = new ColumnInfo();
        colB.setName("B");
        MetaTypeVO regB = new MetaTypeVO();
        regB.setCode("COND.EXPR");
        regB.setCondition("#A > 0");
        regB.setOnTrue(new ArrayList<>());
        regB.setOnFalse(new ArrayList<>());
        colB.setRegs(Collections.singletonList(regB));

        columns.add(colA);
        columns.add(colB);

        assertThrows(RuntimeException.class, () -> invokeSort(columns));
    }

    @Test
    void testExtractConditionRefs_nestedConditions() {
        ColumnInfo column = new ColumnInfo();
        column.setName("result");

        MetaTypeVO outer = new MetaTypeVO();
        outer.setCode("COND.EXPR");
        outer.setCondition("#a == 'x'");

        MetaTypeVO inner = new MetaTypeVO();
        inner.setCode("COND.EXPR");
        inner.setCondition("#b == 'y'");
        inner.setOnTrue(new ArrayList<>());
        inner.setOnFalse(new ArrayList<>());

        outer.setOnTrue(new ArrayList<>());
        outer.setOnFalse(Collections.singletonList(inner));
        column.setRegs(Collections.singletonList(outer));

        Set<String> refs = invokeExtractRefs(column);
        assertTrue(refs.contains("a"));
        assertTrue(refs.contains("b"));
        assertEquals(2, refs.size());
    }

    // ---- 反射辅助方法 ----

    @SuppressWarnings("unchecked")
    private List<ColumnInfo> invokeSort(List<ColumnInfo> columns) {
        try {
            Method method = AbstractJdbcService.class.getDeclaredMethod(
                    "sortColumnsByConditionDeps", List.class);
            method.setAccessible(true);
            return (List<ColumnInfo>) method.invoke(SERVICE, columns);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> invokeExtractRefs(ColumnInfo columnInfo) {
        try {
            Method method = AbstractJdbcService.class.getDeclaredMethod(
                    "extractConditionRefs", ColumnInfo.class);
            method.setAccessible(true);
            return (Set<String>) method.invoke(SERVICE, columnInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
