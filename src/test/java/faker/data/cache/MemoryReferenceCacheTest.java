package faker.data.cache;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MemoryReferenceCacheTest {
    @Test void isolatesTasksAndReleasesOnlyTheFinishedTask() {
        MemoryReferenceCache c=new MemoryReferenceCache(10,10000);
        c.put("a","users:id","row", "A"); c.put("b","users:id","row","B");
        assertEquals("A",c.get("a","users:id","row"));
        assertEquals("B",c.get("b","users:id","row"));
        c.removeTask("a"); assertNull(c.get("a","users:id","row"));
        assertEquals("B",c.get("b","users:id","row")); assertEquals(1,c.size());
        c.removeTask("b"); assertEquals(0,c.estimatedBytes());
    }
    @Test void refusesOverflowWithoutEvictingAReferencedValue() {
        MemoryReferenceCache c=new MemoryReferenceCache(1,10000);
        c.put("a","col","1","keep");
        assertThrows(IllegalStateException.class,()->c.put("b","col","2","new"));
        assertEquals("keep",c.get("a","col","1"));assertEquals(1,c.size());
        c.removeTask("a");c.put("b","col","2","new");
        assertEquals("new",c.get("b","col","2"));
    }
    @Test void countsReplacementAndNullValuesCorrectly() {
        MemoryReferenceCache c=new MemoryReferenceCache(1,10000);
        c.put("a","c","r","old"); c.put("a","c","r",null);
        assertNull(c.get("a","c","r"));assertEquals(1,c.size());
        c.removeTask("a");assertEquals(0,c.size());assertEquals(0,c.estimatedBytes());
    }
    @Test void enforcesByteBudgetBeforeAddingValue() {
        MemoryReferenceCache c=new MemoryReferenceCache(10,256);
        assertThrows(IllegalStateException.class,()->c.put("a","c","r",new String(new char[1000])));
        assertEquals(0,c.size());assertEquals(0,c.estimatedBytes());
    }
    @Test void concurrentTasksDoNotLoseValuesOrLeakCounters() throws Exception {
        MemoryReferenceCache c=new MemoryReferenceCache(2000,2000000);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            Future<?>[] jobs=new Future<?>[4];
            for(int t=0;t<4;t++){final String id="task"+t;jobs[t]=pool.submit(()->{
                try{for(int i=0;i<200;i++){c.put(id,"c",""+i,"v"+i);assertEquals("v"+i,c.get(id,"c",""+i));}}
                finally{c.removeTask(id);}
            });}
            for(Future<?> job:jobs)job.get(10,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertEquals(0,c.size());assertEquals(0,c.estimatedBytes());
    }
}
