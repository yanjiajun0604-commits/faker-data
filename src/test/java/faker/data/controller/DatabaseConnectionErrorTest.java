package faker.data.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import faker.data.service.db.DatabaseConnectionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class DatabaseConnectionErrorTest {
    private static final DiagnosticDriver DRIVER = new DiagnosticDriver();
    private final ObjectMapper json = new ObjectMapper();
    private final MockMvc http = MockMvcBuilders.standaloneSetup(new DatabaseController()).build();

    @BeforeAll static void register() throws SQLException { DriverManager.registerDriver(DRIVER); }
    @AfterAll static void deregister() throws SQLException { DriverManager.deregisterDriver(DRIVER); }

    private JsonNode request(String endpoint, String scenario) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "MYSQL");
        body.put("url", "jdbc:connection-diagnostic:" + scenario + "?password=url-secret&token=url-token");
        body.put("username", "fixture-user");
        body.put("password", "fixture-secret!");
        String result = http.perform(post(endpoint).contentType("application/json").content(json.writeValueAsBytes(body)))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(result);
    }

    @Test void authenticationFailureRetainsSafeDiagnostics() throws Exception {
        JsonNode response = request("/db/connect/test", "auth");
        assertEquals(1, response.path("code").asInt());
        assertEquals("DATABASE_CONNECTION", response.path("error").path("type").asText());
        assertEquals("AUTHENTICATION_FAILED", response.path("error").path("reason").asText());
        assertEquals("28000", response.path("error").path("sqlState").asText());
        assertEquals(1045, response.path("error").path("vendorCode").asInt());
        String detail = response.path("error").path("detail").asText();
        assertTrue(detail.contains("Access denied"));
        for (String secret : Arrays.asList("fixture-secret!", "fixture-secret%21", "url-secret", "url-token"))
            assertFalse(response.toString().contains(secret), secret);
    }

    @Test void sameAuthenticationFailureIsAvailableToMetadataRequests() throws Exception {
        for (String endpoint : Arrays.asList("/db/showTables", "/db/showColumns/demo")) {
            JsonNode response = request(endpoint, "auth");
            assertEquals(1, response.path("code").asInt());
            assertEquals("AUTHENTICATION_FAILED", response.path("error").path("reason").asText());
        }
    }

    @Test void metadataFailureCannotReportSuccessfulConnectionAndClosesIt() throws Exception {
        int before = DRIVER.closed.get();
        JsonNode response = request("/db/connect/test", "metadata-timeout");
        assertEquals(1, response.path("code").asInt());
        assertEquals("TIMEOUT", response.path("error").path("reason").asText());
        assertEquals(before + 1, DRIVER.closed.get());
    }

    @Test void databaseAccessAndMissingSchemaHaveDistinctReasons() throws Exception {
        assertEquals("PERMISSION_DENIED", request("/db/connect/test", "permission").path("error").path("reason").asText());
        assertEquals("DATABASE_NOT_FOUND", request("/db/connect/test", "missing").path("error").path("reason").asText());
    }

    @Test void metadataReadPermissionErrorsAreNotReportedAsSuccessfulEmptyLists() throws Exception {
        for (String endpoint : Arrays.asList("/db/showTables", "/db/showColumns/demo")) {
            JsonNode response = request(endpoint, "metadata-denied");
            assertEquals(1, response.path("code").asInt());
            assertEquals("PERMISSION_DENIED", response.path("error").path("reason").asText());
            assertEquals(1142, response.path("error").path("vendorCode").asInt());
        }
    }

    @Test void healthyConnectionStillSucceeds() throws Exception {
        assertEquals(0, request("/db/connect/test", "healthy").path("code").asInt());
    }

    @Test void redactionPreservesDiagnosticPunctuationAndDoesNotRetainRawCause() {
        faker.data.domain.DataSource source = new faker.data.domain.DataSource();
        source.setPassword("fixture-secret!");
        DatabaseConnectionException failure = DatabaseConnectionException.from(
                new SQLException("Access denied (password: fixture-secret!)", "28000", 1045), source);
        assertEquals("Access denied (password:[REDACTED])", failure.diagnostics().get("detail"));
        assertNull(failure.getCause());
    }

    public static final class DiagnosticDriver implements Driver {
        final AtomicInteger closed = new AtomicInteger();
        public boolean acceptsURL(String url) { return url != null && url.startsWith("jdbc:connection-diagnostic:"); }
        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) return null;
            if (url.contains(":auth")) throw new SQLException("Access denied; supplied=" + info.getProperty("password")
                    + "; encoded=fixture-secret%21; URL=" + url, "28000", 1045);
            if (url.contains(":permission")) throw new SQLException("Database access denied", "42000", 1044);
            if (url.contains(":missing")) throw new SQLException("Unknown database", "42000", 1049);
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("getMetaData")) {
                    if (url.contains(":metadata-timeout")) throw new SQLTimeoutException("Metadata timed out", "HYT00");
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class}, (metadata, operation, values) -> {
                        throw new SQLException("SELECT command denied to fixture-user", "42000", 1142);
                    });
                }
                if (method.getName().equals("prepareStatement")) throw new SQLException("SELECT command denied to fixture-user", "42000", 1142);
                if (method.getName().equals("close")) { closed.incrementAndGet(); return null; }
                if (method.getName().equals("isClosed")) return false;
                throw new UnsupportedOperationException(method.getName());
            });
        }
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion() { return 1; }
        public int getMinorVersion() { return 0; }
        public boolean jdbcCompliant() { return false; }
        public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
