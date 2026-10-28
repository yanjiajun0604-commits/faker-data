package faker.data;

import faker.data.service.generate.GenerationRandom;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenerationRandomTest {
    List<Object> sample(long seed,String field,int row){try(GenerationRandom.Scope ignored=GenerationRandom.scope(seed,"orders",field,row,"en")){return Arrays.asList(GenerationRandom.uuid(),GenerationRandom.current().nextInt(100000),GenerationRandom.locale());}}
    @Test void cellStreamsAreIndependentOfExecutionOrderAndConcurrentTasks() throws Exception {
        List<Object> expected=sample(42,"id",0);sample(42,"other",0);assertEquals(expected,sample(42,"id",0));
        assertNotEquals(expected,sample(42,"id",1));assertNotEquals(expected,sample(43,"id",0));
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try{Future<List<Object>> first=workers.submit(()->sample(42,"id",0)),second=workers.submit(()->sample(43,"id",0));assertEquals(expected,first.get());assertEquals(sample(43,"id",0),second.get());}finally{workers.shutdownNow();}
        assertFalse(GenerationRandom.seeded());assertEquals("zh_CN",GenerationRandom.locale());
    }
    @Test void nestedSelfReferencesRestoreOuterRandomStateAndLocaleAfterFailure(){
        long first,second;
        try(GenerationRandom.Scope outer=GenerationRandom.scope(42L,"rows","outer",0,"en")){
            first=GenerationRandom.current().nextLong();
            assertThrows(IllegalArgumentException.class,()->{try(GenerationRandom.Scope inner=GenerationRandom.scope(43L,"rows","inner",0,"zh_CN")){throw new IllegalArgumentException("fixture");}});
            assertEquals("en",GenerationRandom.locale());second=GenerationRandom.current().nextLong();
        }
        try(GenerationRandom.Scope outer=GenerationRandom.scope(42L,"rows","outer",0,"en")){assertEquals(first,GenerationRandom.current().nextLong());assertEquals(second,GenerationRandom.current().nextLong());}
        assertFalse(GenerationRandom.seeded());
    }
}
