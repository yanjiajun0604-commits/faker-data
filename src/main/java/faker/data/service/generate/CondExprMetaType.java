package faker.data.service.generate;

import faker.data.domain.ColumnInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CondExprMetaType implements IMetaType {

    /**
     * SpEL 函数扩展点列表。
     * 实现 {@link SpelFunctionProvider} 并注册为 Spring Bean 即可为条件表达式添加自定义函数。
     * required=false 确保没有自定义函数时也能正常工作。
     */
    @Autowired(required = false)
    private List<SpelFunctionProvider> functionProviders = Collections.emptyList();

    @Override
    public String getCode() {
        return "COND.EXPR";
    }

    @Override
    public String getTitle() {
        return "条件规则";
    }

    @Override
    public Object parse(String transId, String curTableName,
                        ColumnInfo columnInfo, Map<String, Object> extendArg) {
        MetaTypeVO reg = (MetaTypeVO) extendArg.get("currentReg");
        @SuppressWarnings("unchecked")
        Map<String, Object> rowValues = (Map<String, Object>) extendArg.get("rowValues");
        boolean branch = evaluate(prepare(reg), reg, rowValues);

        return executeBranch(branch, reg, transId, curTableName, columnInfo, extendArg, rowValues);
    }

    /** 表达式在任务准备时解析；不使用会无限增长的全局表达式缓存。 */
    public Expression prepare(MetaTypeVO reg) {
        String condition = reg.getCondition();
        if (condition == null || condition.trim().isEmpty()) {
            throw new IllegalArgumentException("条件表达式不能为空");
        }
        try {
            return new SpelExpressionParser().parseExpression(addFunctionPrefix(condition));
        } catch (Exception ex) {
            throw new IllegalArgumentException("条件表达式求值失败: " + condition, ex);
        }
    }

    /** 每次求值隔离变量，避免条件中的赋值或多任务并发污染其他行。 */
    public boolean evaluate(Expression expression, MetaTypeVO reg, Map<String, Object> rowValues) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        if (rowValues != null) {
            // 注册原始 key 和小写别名，使 SpEL 变量查找大小写不敏感
            // 解决 Oracle/PostgreSQL 返回大写列名但用户写了小写 #字段名 的问题
            rowValues.forEach((key, value) -> {
                ctx.setVariable(key, value);
                String lowerKey = key.toLowerCase(java.util.Locale.ROOT);
                if (!lowerKey.equals(key)) {
                    ctx.setVariable(lowerKey, value);
                }
            });
        }

        // 注册自定义 SpEL 函数（内置 + 用户扩展）
        for (SpelFunctionProvider provider : functionProviders) {
            provider.register(ctx);
        }

        try {
            Boolean result = expression.getValue(ctx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            throw new RuntimeException("条件表达式求值失败: " + reg.getCondition(), e);
        }
    }

    private Object executeBranch(boolean branch, MetaTypeVO reg, String transId, String curTableName,
                                 ColumnInfo columnInfo, Map<String, Object> extendArg, Map<String, Object> rowValues) {
        List<MetaTypeVO> rules = branch ? reg.getOnTrue() : reg.getOnFalse();
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (MetaTypeVO rule : rules) {
            extendArg.put("currentReg", rule);

            // 为嵌套的 REF.SELF_COLUMN 解析引用值
            // genColumnValue 只处理顶层规则的 refSelfColumn，嵌套规则需要在这里补上
            if (rule.getRefSelfColumn() != null) {
                String refCol = rule.getRefSelfColumn().toLowerCase();
                Object refVal = rowValues != null ? rowValues.get(refCol) : null;
                if (refVal == null && rowValues != null) {
                    // 兼容大小写：前端传入的可能与 JDBC 返回的大小写不同
                    refVal = rowValues.get(rule.getRefSelfColumn());
                }
                extendArg.put("refSelfValue", refVal);
            }

            IMetaType impl = MetaTypeFactory.getMetaTypeImpl(rule);
            Object val = impl.parse(transId, curTableName, columnInfo, extendArg);
            if (val != null) {
                sb.append(val);
            }
        }
        return sb.toString();
    }

    /**
     * 自动为条件表达式中的函数名补全 # 前缀。
     * 用户可省略 # 直接写 contains(#name,'俊')，自动转为 #contains(#name,'俊')。
     * 已带 # 前缀的函数不受影响。
     */
    private String addFunctionPrefix(String condition) {
        for (SpelFunctionProvider provider : functionProviders) {
            for (String funcName : provider.getFunctionNames()) {
                // 匹配函数名后紧跟 ( 的位置，且前面不是 #（即用户没写 # 前缀）
                // 使用负向先行断言确保不会重复添加 ##
                String pattern = "(?<!#)\\b" + java.util.regex.Pattern.quote(funcName) + "\\(";
                condition = condition.replaceAll(pattern, "#" + funcName + "(");
            }
        }
        return condition;
    }
}
