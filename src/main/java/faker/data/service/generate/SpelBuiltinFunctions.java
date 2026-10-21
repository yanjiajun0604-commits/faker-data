package faker.data.service.generate;

/**
 * SpEL 条件表达式可用的内置函数集合。
 * 函数均为 static 方法，由 {@link SpelBuiltinFunctionProvider} 注册到 SpEL 上下文。
 */
public final class SpelBuiltinFunctions {

    private SpelBuiltinFunctions() {
    }

    // ---- 字符串函数 ----

    public static int length(String str) {
        return str == null ? 0 : str.length();
    }

    public static boolean contains(String str, String sub) {
        if (str == null || sub == null) return false;
        return str.contains(sub);
    }

    public static boolean startsWith(String str, String prefix) {
        if (str == null || prefix == null) return false;
        return str.startsWith(prefix);
    }

    public static boolean endsWith(String str, String suffix) {
        if (str == null || suffix == null) return false;
        return str.endsWith(suffix);
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    // ---- 数值函数 ----

    public static boolean between(Number value, Number min, Number max) {
        if (value == null || min == null || max == null) return false;
        double dv = value.doubleValue();
        return dv >= min.doubleValue() && dv <= max.doubleValue();
    }

    // ---- 类型转换 ----

    public static String toString(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
