package faker.data.service.generate;

import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册内置 SpEL 函数，供条件表达式使用。
 * 所有注册函数见 {@link SpelBuiltinFunctions}。
 */
@Component
public class SpelBuiltinFunctionProvider implements SpelFunctionProvider {

    private static final Map<String, Method> METHODS = new LinkedHashMap<>();

    static {
        try {
            METHODS.put("length",    m("length",    String.class));
            METHODS.put("contains",  m("contains",  String.class, String.class));
            METHODS.put("startsWith", m("startsWith", String.class, String.class));
            METHODS.put("endsWith",  m("endsWith",  String.class, String.class));
            METHODS.put("isEmpty",   m("isEmpty",   String.class));
            METHODS.put("isNotEmpty", m("isNotEmpty", String.class));
            METHODS.put("between",   m("between",   Number.class, Number.class, Number.class));
            METHODS.put("toString",  m("toString",  Object.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("内置 SpEL 函数初始化失败", e);
        }
    }

    private static Method m(String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return SpelBuiltinFunctions.class.getMethod(name, paramTypes);
    }

    @Override
    public void register(StandardEvaluationContext ctx) {
        METHODS.forEach((name, method) -> {
            try {
                ctx.registerFunction(name, method);
            } catch (Exception ignored) {
                // 重名函数跳过
            }
        });
    }

    @Override
    public java.util.Set<String> getFunctionNames() {
        return METHODS.keySet();
    }
}
