package faker.data;

import faker.data.domain.*;
import faker.data.service.generate.*;
import faker.data.service.generate.engine.GenerationValidator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import java.nio.file.*;
import java.util.*;
import static faker.data.BulkGenerationIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
class GenerationValidationTest {
    static final Path STATE=directory();
    static Path directory(){try{return Files.createTempDirectory("faker-validate-");}catch(Exception e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("faker.data-dir",STATE::toString);}
    @Autowired List<IMetaType> implementations;
    @BeforeEach void registry()throws Exception{MetaTypeFactory f=new MetaTypeFactory();f.metaTypeList=implementations;f.afterPropertiesSet();}
    @Test void configurationValidationReportsItsLimitedScopeWithoutAConnection(){
        Map<String,Object> result=GenerationValidator.validate(Collections.singletonList(table("orders",3,column("id","java.lang.String",rule("FIX.UUID")))));
        assertEquals(true,result.get("valid"));assertEquals("CONFIGURATION_ONLY",result.get("scope"));
        assertFalse(((List<?>)result.get("notChecked")).isEmpty());
    }
    @Test void rejectsInvalidIdentifiersUnknownStrategiesAndNegativeCounts(){
        assertThrows(IllegalArgumentException.class,()->GenerationValidator.validate(Collections.singletonList(table("orders;drop",1,column("id","java.lang.String",rule("FIX.UUID"))))));
        assertThrows(IllegalArgumentException.class,()->GenerationValidator.validate(Collections.singletonList(table("orders",1,column("id","java.lang.String",rule("UNKNOWN"))))));
        assertThrows(IllegalArgumentException.class,()->GenerationValidator.validate(Collections.singletonList(table("orders",-1,column("id","java.lang.String",rule("FIX.UUID"))))));
    }
    @Test void rejectsDependencyCyclesWithoutRunningGeneration(){
        TableVO a=table("a",1,column("id","java.lang.String",reference("b","id")));
        TableVO b=table("b",1,column("id","java.lang.String",reference("a","id")));
        assertThrows(RuntimeException.class,()->GenerationValidator.validate(Arrays.asList(a,b)));
    }
    @Test void missingRuleParametersAreReportedBeforeSubmission(){
        for(String code:Arrays.asList("REG.DATE","REG.RANGE_NUM","REG.RANGE_STRING","REG.STYLE","REF.SELF_COLUMN","EXT.FIELD"))
            assertThrows(IllegalArgumentException.class,()->GenerationValidator.validate(Collections.singletonList(table("orders",1,column("id","java.lang.String",rule(code))))),code);
    }
    @Test void extensionCannotBeHiddenInsideAConditionalBranch(){
        MetaTypeVO extension=rule("EXT.FIELD");extension.setExtension("demo");
        assertThrows(IllegalArgumentException.class,()->GenerationValidator.validate(Collections.singletonList(table("orders",1,
            column("payload","byte[]",condition("true",extension,null))))));
    }
}
