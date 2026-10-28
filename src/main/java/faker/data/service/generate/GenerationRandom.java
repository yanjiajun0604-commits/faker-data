package faker.data.service.generate;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Per-cell random scope. Never seeds shared singleton rule objects or task IDs. */
public final class GenerationRandom {
    private static final ThreadLocal<State> CURRENT=new ThreadLocal<>();
    private GenerationRandom(){}
    private static final class State {
        final Random random;final String locale;final boolean seeded;
        State(Long seed,String table,String column,int row,String locale){this.seeded=seed!=null;this.random=seed==null?ThreadLocalRandom.current():new Random(seed(seed,table,column,row));this.locale=locale;}
    }
    public static final class Scope implements AutoCloseable {
        private final State previous;
        private Scope(State previous){this.previous=previous;}
        @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    public static Scope scope(Long seed,String table,String column,int row,String locale){
        State previous=CURRENT.get();CURRENT.set(new State(seed,table,column,row,locale));return new Scope(previous);
    }
    public static Random current(){State state=CURRENT.get();return state==null?ThreadLocalRandom.current():state.random;}
    public static boolean seeded(){State state=CURRENT.get();return state!=null && state.seeded;}
    public static String locale(){State state=CURRENT.get();return state==null || state.locale==null?"zh_CN":state.locale;}
    public static UUID uuid(){if(!seeded())return UUID.randomUUID();Random random=current();return new UUID((random.nextLong()&0xffffffffffff0fffL)|0x4000L,(random.nextLong()&0x3fffffffffffffffL)|0x8000000000000000L);}
    /** Version1 stable UTF-16 FNV mixing; identifiers are normalized by the caller. */
    public static long seed(long seed,String table,String column,int row){long value=seed^0xcbf29ce484222325L;String key=table+"\u0000"+column;for(int i=0;i<key.length();i++)value=(value^key.charAt(i))*0x100000001b3L;value^=(long)row*0x9e3779b97f4a7c15L;value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31);}
}
