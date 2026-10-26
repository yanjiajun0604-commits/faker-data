package faker.data;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenerationPreflightTest {
    private static final String URL="jdbc:h2:mem:preflight;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final Path DIR=directory();
    private final JdbcTemplate fixture=new JdbcTemplate(new DriverManagerDataSource(URL,"sa","fixture"));
    @Autowired TestRestTemplate http;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("faker.data-dir",DIR::toString);}
    static Path directory(){try{return Files.createTempDirectory("faker-preflight-");}catch(Exception e){throw new RuntimeException(e);}}
    @BeforeEach void setup(){
        fixture.execute("DROP ALL OBJECTS");
        fixture.execute("CREATE TABLE products(id BIGINT PRIMARY KEY, name VARCHAR(5) NOT NULL, price DECIMAL(5,2), note VARCHAR(20) DEFAULT 'default')");
        fixture.execute("CREATE UNIQUE INDEX uq_name ON products(name)");
        fixture.execute("INSERT INTO products(id,name,price) VALUES(7,'taken',1.20)");
        fixture.execute("CREATE TABLE auto_products(id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(5) NOT NULL, doubled BIGINT AS(id*2))");
        fixture.execute("CREATE TABLE defaults_test(id BIGINT PRIMARY KEY, password VARCHAR(20) NOT NULL DEFAULT 'private-default')");
    }
    static Map<String,Object> m(Object...pairs){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
    static Map<String,Object> col(String name,String type,String constant){return m("name",name,"javaType",type,"nullable",1,"regs",Arrays.asList(m("code","RES.INPUTAREA","inputs",Arrays.asList(m("value",constant,"weight",1)))));}
    static Map<String,Object> id(){return col("id","java.lang.Long","12");}
    static Map<String,Object> name(){return col("name","java.lang.String","new");}
    @SuppressWarnings("unchecked") Map<String,Object> check(String table,int size,List<Map<String,Object>> columns){
        Map<String,Object> body=m("dataSource",m("type","MYSQL","url",URL,"username","sa","password","fixture"),"tables",Arrays.asList(m("name",table,"size",size,"columns",columns)));
        Map<String,Object> response=http.postForObject("/generation/preflight",body,Map.class);
        assertEquals(0,response.get("code"),response.toString());assertNotNull(response.get("data"));return (Map<String,Object>)response.get("data");
    }
    @SuppressWarnings("unchecked") void has(Map<String,Object> result,String code,String column){
        assertEquals(false,result.get("valid"),result.toString());
        assertTrue(((List<Map<String,Object>>)result.get("errors")).stream().anyMatch(e->code.equals(e.get("code")) && Objects.equals(column,e.get("column"))),result.toString());
    }
    @Test void detectsMissingTargetsAndRequiredColumns(){
        has(check("missing",1,Arrays.asList(id())),"TABLE_NOT_FOUND",null);
        has(check("products",1,Arrays.asList(id(),name(),col("absent","java.lang.String","x"))),"COLUMN_NOT_FOUND","absent");
        has(check("products",1,Arrays.asList(id())),"REQUIRED_COLUMN_MISSING","name");
    }
    @Test void checksConstantsAgainstActualLengthNullAndDecimalConstraints(){
        has(check("products",1,Arrays.asList(id(),col("name","java.lang.String","123456"))),"TEXT_TOO_LONG","name");
        Map<String,Object> empty=m("name","name","javaType","java.lang.String","nullable",1,"regs",Collections.emptyList());
        has(check("products",1,Arrays.asList(id(),empty)),"NULL_NOT_ALLOWED","name");
        has(check("products",1,Arrays.asList(id(),name(),col("price","java.math.BigDecimal","1234.56"))),"DECIMAL_PRECISION","price");
        has(check("products",1,Arrays.asList(id(),name(),col("price","java.math.BigDecimal","1.234"))),"DECIMAL_SCALE","price");
        has(check("products",1,Arrays.asList(col("id","java.lang.Long","not-number"),name())),"INVALID_STATIC_VALUE","id");
    }
    @Test void detectsDeterministicUniqueConflictsWithoutInserting(){
        has(check("products",2,Arrays.asList(id(),name())),"REPEATED_UNIQUE_VALUE",null);
        has(check("products",1,Arrays.asList(id(),col("name","java.lang.String","taken"))),"EXISTING_UNIQUE_VALUE",null);
        assertEquals(1,fixture.queryForObject("SELECT COUNT(*) FROM products",Integer.class));
    }
    @Test void permitsDatabaseDefaultsAndReportsAutoAndGeneratedColumns(){
        Map<String,Object> good=check("products",1,Arrays.asList(id(),name()));
        assertEquals(true,good.get("valid"));assertEquals("DATABASE_METADATA_AND_STATIC_VALUES",good.get("scope"));
        assertFalse(((List<?>)good.get("notChecked")).isEmpty());
        assertEquals(true,check("auto_products",1,Arrays.asList(name())).get("valid"));
        assertEquals(true,check("defaults_test",1,Arrays.asList(id())).get("valid"));
        has(check("auto_products",1,Arrays.asList(name(),col("doubled","java.lang.Long","1"))),"GENERATED_COLUMN_WRITE","doubled");
    }
    @Test void leavesDynamicValuesForRuntimeAndReportsTheBoundary(){
        Map<String,Object> random=m("name","id","javaType","java.lang.Long","regs",Arrays.asList(m("code","REG.RANGE_NUM","minSize",1,"maxSize",100)));
        Map<String,Object> result=check("products",3,Arrays.asList(random,name()));
        // name is constant and unique, so it still catches a proven conflict without executing random rules.
        has(result,"REPEATED_UNIQUE_VALUE",null);
        assertEquals(1,fixture.queryForObject("SELECT COUNT(*) FROM products",Integer.class));
    }
}
