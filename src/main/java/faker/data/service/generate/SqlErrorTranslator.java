package faker.data.service.generate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 JDBC/Spring 抛出的原始异常消息翻译成用户友好的标题与建议。
 *
 * <p>支持达梦（DM）、Oracle、MySQL 三种数据库的常见错误：
 * 唯一约束、外键约束、NOT NULL 约束、列值超长、表/字段不存在、连接失败等。
 */
public final class SqlErrorTranslator {

    private SqlErrorTranslator() {
    }

    /** 翻译结果。title 用于弹窗主标题，suggestion 是给用户的可读描述，raw 保留完整原文。 */
    public static class Translated {
        public final String title;
        public final String suggestion;
        public final String raw;

        public Translated(String title, String suggestion, String raw) {
            this.title = title;
            this.suggestion = suggestion;
            this.raw = raw;
        }
    }

    /** 把异常链压平后做正则匹配，返回最友好的版本。 */
    public static Translated translate(Throwable t) {
        if (t == null) {
            return new Translated("执行失败", "未知异常", "");
        }

        // 拿最深层 cause 的 message；外层往往是 Spring 的包装
        String raw = flattenMessage(t);
        String head = headClass(t);

        // 1. 达梦 / Oracle：违反表[X]唯一性约束条件[Y]
        Matcher m = Pattern.compile("违反表\\[([^\\]]+)\\]唯一性约束条件\\[([^\\]]+)\\]").matcher(raw);
        if (m.find()) {
            return new Translated(
                    "数据重复，违反唯一性约束",
                    String.format("表【%s】生成的数据存在重复值，违反唯一性约束【%s】。"
                            + "请检查对应字段的生成规则是否会产生重复，或减少生成的数据量。",
                            m.group(1), m.group(2)),
                    raw);
        }

        // 2. MySQL：Duplicate entry 'xxx' for key 'yyy.YYY'
        m = Pattern.compile("Duplicate entry '([^']*)' for key '([^']+)'").matcher(raw);
        if (m.find()) {
            return new Translated(
                    "数据重复，违反唯一性约束",
                    String.format("插入数据时违反唯一性约束【%s】。"
                            + "请检查对应字段的生成规则是否会产生重复，或减少生成的数据量。",
                            m.group(2)),
                    raw);
        }

        // 3. 达梦 / Oracle：违反外键约束
        m = Pattern.compile("违反(?:完整)?(?:性)?约束条件?\\s*([\\w$#]+)(?:[-_]?\\s*无效)?").matcher(raw);
        // 上一条未命中时再尝试更宽松的外键匹配
        if (m.find() && raw.contains("外键")) {
            return new Translated(
                    "违反外键约束",
                    "数据无法写入，因为存在外键引用问题。请确认被引用表已先生成数据，或调整生成顺序。",
                    raw);
        }
        if (raw.contains("外键约束") || raw.contains("foreign key") || raw.contains("FOREIGN KEY")) {
            return new Translated(
                    "违反外键约束",
                    "数据无法写入，因为存在外键引用问题。请确认被引用表已先生成数据，或调整生成顺序。",
                    raw);
        }

        // 4. NOT NULL 约束
        if (raw.contains("cannot insert NULL") || raw.contains("不能插入空值") || raw.contains("NULL 值") && raw.contains("NOT NULL")) {
            m = Pattern.compile("[\"']?([A-Za-z_\\u4e00-\\u9fa5][A-Za-z0-9_\\u4e00-\\u9fa5]*)[\"']?").matcher(raw);
            String col = m.find() ? m.group(1) : "未知字段";
            return new Translated(
                    "必填字段为空",
                    String.format("必填字段【%s】的生成值为空，请为该字段配置生成规则。", col),
                    raw);
        }
        if (raw.contains("ORA-01400") || raw.contains("cannot insert NULL into")) {
            return new Translated(
                    "必填字段为空",
                    "必填字段的生成值为空，请检查必填字段是否都配置了生成规则。",
                    raw);
        }

        // 5. 列值超长
        if (raw.contains("value too large") || raw.contains("ORA-12899") || raw.contains("截断") || raw.contains("数据溢出") || raw.contains("Data too long")) {
            return new Translated(
                    "字段值超出长度限制",
                    "某些字段生成的值超出了数据库列的长度限制，请调整生成规则或放宽字段长度。",
                    raw);
        }

        // 6. 表 / 字段不存在
        if (raw.contains("doesn't exist") || raw.contains("不存在") || raw.contains("ORA-00942")
                || raw.contains("Table") && raw.contains("not found")) {
            return new Translated(
                    "表或字段不存在",
                    "数据库结构与当前配置不匹配，请刷新表列表后重试，或检查数据库连接。",
                    raw);
        }

        // 7. 连接失败
        if (raw.contains("连接数据库失败") || raw.contains("Could not create connection")
                || raw.contains("Communications link failure") || raw.contains("Connection refused")) {
            return new Translated(
                    "数据库连接失败",
                    "无法连接到目标数据库，请检查网络、URL、用户名密码是否正确，以及数据库服务是否启动。",
                    raw);
        }

        // 8. SQL 语法错误（不应该发生在数据生成场景，但兜底）
        if (raw.contains("You have an error in your SQL syntax") || raw.contains("ORA-00900")
                || raw.contains("语法分析") || raw.contains("syntax error")) {
            return new Translated(
                    "SQL 执行异常",
                    "SQL 执行出现语法错误，请刷新表结构后重试。",
                    raw);
        }

        // 9. 兜底：截断过长的原始消息，避免前端被超长字符串冲爆
        String shortRaw = raw.length() > 300 ? raw.substring(0, 300) + "..." : raw;
        return new Translated(
                head.isEmpty() ? "数据生成失败" : ("数据生成失败：" + head),
                shortRaw,
                raw);
    }

    private static String flattenMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 6) {
            if (cur.getMessage() != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(cur.getMessage());
            }
            if (cur.getCause() == cur) break;
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    private static String headClass(Throwable t) {
        if (t == null) return "";
        String name = t.getClass().getSimpleName();
        // 只保留可读性较高的几个，Spring 包装类直接丢弃
        if (name.startsWith("DataIntegrityViolation") || name.contains("ConstraintViolation")) {
            return "数据完整性错误";
        }
        if (name.contains("BadSqlGrammar") || name.contains("SyntaxError")) {
            return "SQL 语法错误";
        }
        if (name.contains("CannotCreateTransaction") || name.contains("CannotGetJdbcConnection")) {
            return "数据库连接失败";
        }
        return "";
    }
}