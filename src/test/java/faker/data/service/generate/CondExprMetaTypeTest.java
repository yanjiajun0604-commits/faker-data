package faker.data.service.generate;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.MetaTypeVO.InputItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class CondExprMetaTypeTest {

    private CondExprMetaType condExprMetaType;

    @BeforeEach
    void setUp() throws Exception {
        condExprMetaType = new CondExprMetaType();

        // 注入 SpEL 函数提供者，使条件表达式中的函数调用（如 contains）可用
        Field fpField = CondExprMetaType.class.getDeclaredField("functionProviders");
        fpField.setAccessible(true);
        fpField.set(condExprMetaType, Collections.singletonList(new SpelBuiltinFunctionProvider()));

        // 静态注入 mock MetaType 实现，避免依赖 Spring 上下文
        Field field = MetaTypeFactory.class.getDeclaredField("metaTypeMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, IMetaType> map =
                (ConcurrentHashMap<String, IMetaType>) field.get(null);
        map.clear();

        // COND.EXPR 自身，用于嵌套条件测试
        map.put("COND.EXPR", condExprMetaType);

        // Mock REG.RANGE_NUM: 返回 maxSize（测试中 minSize==maxSize，结果固定）
        map.put("REG.RANGE_NUM", new IMetaType() {
            @Override
            public String getCode() {
                return "REG.RANGE_NUM";
            }

            @Override
            public String getTitle() {
                return "Mock";
            }

            @Override
            public Object parse(String transId, String curTableName,
                                ColumnInfo columnInfo, Map<String, Object> extendArg) {
                MetaTypeVO reg = (MetaTypeVO) extendArg.get("currentReg");
                return reg.getMaxSize();
            }
        });

        // Mock RES.INPUTAREA: 返回第一个 input 的 value
        map.put("RES.INPUTAREA", new IMetaType() {
            @Override
            public String getCode() {
                return "RES.INPUTAREA";
            }

            @Override
            public String getTitle() {
                return "Mock";
            }

            @Override
            public Object parse(String transId, String curTableName,
                                ColumnInfo columnInfo, Map<String, Object> extendArg) {
                MetaTypeVO reg = (MetaTypeVO) extendArg.get("currentReg");
                List<InputItem> inputs = reg.getInputs();
                if (inputs != null && !inputs.isEmpty()) {
                    return inputs.get(0).getValue();
                }
                return null;
            }
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清理静态注入，避免测试间污染
        Field field = MetaTypeFactory.class.getDeclaredField("metaTypeMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, IMetaType> map =
                (ConcurrentHashMap<String, IMetaType>) field.get(null);
        map.clear();
    }

    @Test
    void testConditionTrue_takesOnTrueBranch() {
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("#user_type == 'vip'");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("REG.RANGE_NUM");
        onTrueRule.setMinSize(5);
        onTrueRule.setMaxSize(5);
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("REG.RANGE_NUM");
        onFalseRule.setMinSize(1);
        onFalseRule.setMaxSize(1);
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        Map<String, Object> rowValues = new HashMap<>();
        rowValues.put("user_type", "vip");

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_user",
                new ColumnInfo(), extendArg);

        assertEquals("5", result);
    }

    @Test
    void testConditionFalse_takesOnFalseBranch() {
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("#user_type == 'vip'");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("REG.RANGE_NUM");
        onTrueRule.setMinSize(5);
        onTrueRule.setMaxSize(5);
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("REG.RANGE_NUM");
        onFalseRule.setMinSize(1);
        onFalseRule.setMaxSize(1);
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        Map<String, Object> rowValues = new HashMap<>();
        rowValues.put("user_type", "normal");

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_user",
                new ColumnInfo(), extendArg);

        assertEquals("1", result);
    }

    @Test
    void testConditionNull_takesOnFalseBranch() {
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("#user_type == 'vip'");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("REG.RANGE_NUM");
        onTrueRule.setMinSize(5);
        onTrueRule.setMaxSize(5);
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("REG.RANGE_NUM");
        onFalseRule.setMinSize(1);
        onFalseRule.setMaxSize(1);
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        // user_type 未生成，SpEL 变量为 null
        Map<String, Object> rowValues = new HashMap<>();

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_user",
                new ColumnInfo(), extendArg);

        assertEquals("1", result);
    }

    @Test
    void testNestedCondition_elseIfChain() {
        // 外层: #user_type == 'svip'
        //   onTrue:  REG.RANGE_NUM(5,5)
        //   onFalse: (内层: #user_type == 'vip')
        //              onTrue:  REG.RANGE_NUM(8,8)
        //              onFalse: REG.RANGE_NUM(1,1)

        MetaTypeVO innerReg = new MetaTypeVO();
        innerReg.setCode("COND.EXPR");
        innerReg.setCondition("#user_type == 'vip'");

        MetaTypeVO innerTrue = new MetaTypeVO();
        innerTrue.setCode("REG.RANGE_NUM");
        innerTrue.setMinSize(8);
        innerTrue.setMaxSize(8);
        innerReg.setOnTrue(Collections.singletonList(innerTrue));

        MetaTypeVO innerFalse = new MetaTypeVO();
        innerFalse.setCode("REG.RANGE_NUM");
        innerFalse.setMinSize(1);
        innerFalse.setMaxSize(1);
        innerReg.setOnFalse(Collections.singletonList(innerFalse));

        MetaTypeVO outerReg = new MetaTypeVO();
        outerReg.setCode("COND.EXPR");
        outerReg.setCondition("#user_type == 'svip'");

        MetaTypeVO outerTrue = new MetaTypeVO();
        outerTrue.setCode("REG.RANGE_NUM");
        outerTrue.setMinSize(5);
        outerTrue.setMaxSize(5);
        outerReg.setOnTrue(Collections.singletonList(outerTrue));
        outerReg.setOnFalse(Collections.singletonList(innerReg));

        Map<String, Object> rowValues = new HashMap<>();
        rowValues.put("user_type", "vip");

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", outerReg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_user",
                new ColumnInfo(), extendArg);

        // outer: svip? false → inner: vip? true → 8
        assertEquals("8", result);
    }

    @Test
    void testNumericComparison() {
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("#amount > 100");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("RES.INPUTAREA");
        InputItem highItem = new InputItem();
        highItem.setValue("high");
        onTrueRule.setInputs(Collections.singletonList(highItem));
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("RES.INPUTAREA");
        InputItem lowItem = new InputItem();
        lowItem.setValue("low");
        onFalseRule.setInputs(Collections.singletonList(lowItem));
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        Map<String, Object> rowValues = new HashMap<>();
        rowValues.put("amount", 150);

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_order",
                new ColumnInfo(), extendArg);

        assertEquals("high", result);
    }

    @Test
    void testEmptyCondition_throwsException() {
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition(null);

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", new HashMap<>());

        assertThrows(IllegalArgumentException.class, () -> {
            condExprMetaType.parse("trans1", "t_user", new ColumnInfo(), extendArg);
        });
    }

    @Test
    void testChineseValueInBranches() {
        // SpEL tokenizer 不支持中文字段名变量（报 Cannot handle '折'），
        // 因此条件使用英文变量名，rowValues 键名为英文，但值仍为中文，
        // 以验证中文值在分支逻辑中正常流转。
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("#discount_level == 'high'");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("RES.INPUTAREA");
        InputItem item = new InputItem();
        item.setValue("3折");
        onTrueRule.setInputs(Collections.singletonList(item));
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("RES.INPUTAREA");
        InputItem item2 = new InputItem();
        item2.setValue("原价");
        onFalseRule.setInputs(Collections.singletonList(item2));
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        Map<String, Object> rowValues = new HashMap<>();
        rowValues.put("discount_level", "high");

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_product",
                new ColumnInfo(), extendArg);

        assertEquals("3折", result);
    }

    @Test
    void testCaseInsensitive_fieldName() {
        // Oracle/PostgreSQL 默认返回大写列名，用户写了小写 #字段名 应能匹配
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("#user_type == 'vip'");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("REG.RANGE_NUM");
        onTrueRule.setMinSize(5);
        onTrueRule.setMaxSize(5);
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("REG.RANGE_NUM");
        onFalseRule.setMinSize(1);
        onFalseRule.setMaxSize(1);
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        // JDBC 返回大写 key，用户写了小写条件表达式
        Map<String, Object> rowValues = new HashMap<>();
        rowValues.put("USER_TYPE", "vip");

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_user",
                new ColumnInfo(), extendArg);

        // 大小写不敏感：USER_TYPE 匹配 #user_type，条件为真走 onTrue → 5
        assertEquals("5", result);
    }

    @Test
    void testContainsFunction_autoPrefix_true() {
        // 用户省略 # 前缀写 contains(#name,'俊')，应自动补全为 #contains(#name,'俊')
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("contains(#name,'俊')");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("RES.INPUTAREA");
        InputItem item = new InputItem();
        item.setValue("含俊");
        onTrueRule.setInputs(Collections.singletonList(item));
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("RES.INPUTAREA");
        InputItem item2 = new InputItem();
        item2.setValue("不含");
        onFalseRule.setInputs(Collections.singletonList(item2));
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        Map<String, Object> rowValues = new HashMap<>();
        rowValues.put("name", "张俊杰");

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_user",
                new ColumnInfo(), extendArg);

        assertEquals("含俊", result);
    }

    @Test
    void testContainsFunction_nullVariable_returnsFalse() {
        // name 为 null 时，contains 返回 false，走 onFalse 分支
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("contains(#name,'俊')");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("RES.INPUTAREA");
        InputItem item = new InputItem();
        item.setValue("yes");
        onTrueRule.setInputs(Collections.singletonList(item));
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("RES.INPUTAREA");
        InputItem item2 = new InputItem();
        item2.setValue("no");
        onFalseRule.setInputs(Collections.singletonList(item2));
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        // name 未生成，SpEL 变量为 null
        Map<String, Object> rowValues = new HashMap<>();

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_user",
                new ColumnInfo(), extendArg);

        assertEquals("no", result);
    }

    @Test
    void testContainsFunction_explicitHashPrefix() {
        // 用户已经写了 # 前缀，不要重复添加
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("#contains(#name,'俊')");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("RES.INPUTAREA");
        InputItem item = new InputItem();
        item.setValue("含俊");
        onTrueRule.setInputs(Collections.singletonList(item));
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("RES.INPUTAREA");
        InputItem item2 = new InputItem();
        item2.setValue("不含");
        onFalseRule.setInputs(Collections.singletonList(item2));
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        Map<String, Object> rowValues = new HashMap<>();
        rowValues.put("name", "张俊杰");

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_user",
                new ColumnInfo(), extendArg);

        assertEquals("含俊", result);
    }

    @Test
    void testStartsWithFunction_autoPrefix() {
        MetaTypeVO reg = new MetaTypeVO();
        reg.setCode("COND.EXPR");
        reg.setCondition("startsWith(#name,'张')");

        MetaTypeVO onTrueRule = new MetaTypeVO();
        onTrueRule.setCode("RES.INPUTAREA");
        InputItem item = new InputItem();
        item.setValue("张家");
        onTrueRule.setInputs(Collections.singletonList(item));
        reg.setOnTrue(Collections.singletonList(onTrueRule));

        MetaTypeVO onFalseRule = new MetaTypeVO();
        onFalseRule.setCode("RES.INPUTAREA");
        InputItem item2 = new InputItem();
        item2.setValue("其他");
        onFalseRule.setInputs(Collections.singletonList(item2));
        reg.setOnFalse(Collections.singletonList(onFalseRule));

        Map<String, Object> rowValues = new HashMap<>();
        rowValues.put("name", "张三");

        Map<String, Object> extendArg = new HashMap<>();
        extendArg.put("currentReg", reg);
        extendArg.put("rowValues", rowValues);

        Object result = condExprMetaType.parse("trans1", "t_user",
                new ColumnInfo(), extendArg);

        assertEquals("张家", result);
    }
}
