package faker.data.service.db;

import faker.data.domain.DataSource;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Carries useful JDBC diagnostics without retaining credentials or the raw exception chain. */
public final class DatabaseConnectionException extends RuntimeException {
    private static final Pattern URL_SECRET = Pattern.compile("(?i)(?:[?&;]|^)(?:password|passwd|pwd|token|access_token|secret|api_key)=([^&;\\s]+)");
    private static final Pattern NAMED_SECRET = Pattern.compile("(?i)(password|passwd|pwd|token|access_token|secret|api_key)\\s*([=:])\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;&)]+)");
    private final String reason;
    private final String sqlState;
    private final Integer vendorCode;
    private final String detail;

    private DatabaseConnectionException(String reason, String sqlState, Integer vendorCode, String detail) {
        super(summary(reason));
        this.reason = reason;
        this.sqlState = sqlState;
        this.vendorCode = vendorCode;
        this.detail = detail;
    }

    public static DatabaseConnectionException from(Exception failure, DataSource source) {
        if (failure instanceof DatabaseConnectionException) return (DatabaseConnectionException) failure;
        SQLException sql = failure instanceof SQLException ? (SQLException) failure : null;
        String state = sql == null ? null : sql.getSQLState();
        int code = sql == null ? 0 : sql.getErrorCode();
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        String reason;
        if (failure instanceof ClassNotFoundException) reason = "DRIVER_NOT_FOUND";
        else if (failure instanceof SQLTimeoutException || "HYT00".equals(state) || "HYT01".equals(state)) reason = "TIMEOUT";
        else if (code == 1044 || code == 1142 || code == 1143 || "42501".equals(state)) reason = "PERMISSION_DENIED";
        else if (code == 1049 || "3D000".equals(state)) reason = "DATABASE_NOT_FOUND";
        else if (code == 1045 || (state != null && state.startsWith("28"))) reason = "AUTHENTICATION_FAILED";
        else if (message.contains("Public Key Retrieval")) reason = "AUTH_CONFIGURATION";
        else if (state != null && state.startsWith("08")) reason = "CONNECTION_FAILED";
        else reason = "DATABASE_ERROR";
        String safeState = state != null && state.matches("[A-Za-z0-9]{5}") ? state : null;
        return new DatabaseConnectionException(reason, safeState, sql == null ? null : code, redact(message, source));
    }

    private static String redact(String message, DataSource source) {
        String safe = message;
        if (source != null) {
            String url = source.getUrl();
            if (url != null && !url.isEmpty()) {
                safe = safe.replace(url, "[connection URL redacted]");
                Matcher secrets = URL_SECRET.matcher(url);
                while (secrets.find()) {
                    String value = secrets.group(1);
                    safe = safe.replace(value, "[REDACTED]");
                    try { safe = safe.replace(URLDecoder.decode(value, "UTF-8"), "[REDACTED]"); }
                    catch (Exception ignored) { /* The literal value has already been removed. */ }
                }
            }
            String password = source.getPassword();
            if (password != null && !password.isEmpty()) {
                safe = safe.replace(password, "[REDACTED]");
                try { safe = safe.replace(URLEncoder.encode(password, "UTF-8"), "[REDACTED]"); }
                catch (Exception ignored) { /* UTF-8 is always available. */ }
            }
        }
        safe = NAMED_SECRET.matcher(safe).replaceAll("$1$2[REDACTED]");
        safe = safe.replaceAll("[\\p{Cntrl}]", " ");
        return safe.length() > 512 ? safe.substring(0, 512) + "..." : safe;
    }

    private static String summary(String reason) {
        switch (reason) {
            case "AUTHENTICATION_FAILED": return "数据库登录认证失败，请核对账号、密码及来源主机授权。";
            case "PERMISSION_DENIED": return "数据库访问被拒绝，请核对账号的数据库访问权限。";
            case "DATABASE_NOT_FOUND": return "目标数据库不存在，请核对 JDBC URL 中的数据库名称。";
            case "TIMEOUT": return "数据库连接或元数据读取超时，请检查地址、端口和网络。";
            case "DRIVER_NOT_FOUND": return "数据库驱动未找到，请检查程序依赖。";
            case "AUTH_CONFIGURATION": return "数据库认证配置不兼容，请检查驱动、TLS 与服务端认证配置。";
            case "CONNECTION_FAILED": return "无法连接数据库，请检查地址、端口和数据库服务。";
            default: return "数据库连接或元数据访问失败，请查看下方诊断信息。";
        }
    }

    public Map<String, Object> diagnostics() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "DATABASE_CONNECTION");
        error.put("reason", reason);
        error.put("sqlState", sqlState);
        error.put("vendorCode", vendorCode);
        error.put("detail", detail);
        return error;
    }
}
