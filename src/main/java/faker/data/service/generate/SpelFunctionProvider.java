package faker.data.service.generate;

import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * SpEL 函数扩展点。
 * 实现此接口并注册为 Spring Bean，即可在条件表达式（COND.EXPR）中使用自定义函数。
 *
 * <pre>
 * 示例：假设注册了函数签名 startsWith(str, prefix)
 * 条件表达式: {@code #startsWith(#name, '张')}
 * 当 name 以 "张" 开头时条件为真。
 * </pre>
 *
 * 注册方式：调用 {@code ctx.registerFunction("函数名", Method)}
 * 在条件表达式中使用: {@code #函数名(参数...)}
 */
public interface SpelFunctionProvider {

    /**
     * 注册函数到 SpEL 上下文。
     * 每次条件求值前调用，可将自定义函数注入 EvaluationContext。
     *
     * @param ctx SpEL 求值上下文
     */
    void register(StandardEvaluationContext ctx);

    /**
     * 返回已注册的函数名集合，用于条件表达式自动补全 # 前缀。
     * 用户写 {@code contains(#name,'俊')} 时自动转换为 {@code #contains(#name,'俊')}。
     */
    default java.util.Set<String> getFunctionNames() {
        return java.util.Collections.emptySet();
    }
}
