package faker.data.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 任务隔离的引用缓存。容量不足时拒绝写入，不能淘汰仍被引用的数据。 */
public class MemoryReferenceCache implements ReferenceCache {
    private final long maxEntries,maxBytes;
    private long entries,bytes;
    private final Map<String,Map<String,Entry>> tasks=new ConcurrentHashMap<>();
    private static final class Entry {
        final Object value;final long bytes;
        Entry(Object value,long bytes){this.value=value;this.bytes=bytes;}
    }
    public MemoryReferenceCache(long maxEntries,long maxBytes){
        if(maxEntries<1 || maxBytes<1)throw new IllegalArgumentException("缓存容量必须大于 0");
        this.maxEntries=maxEntries;this.maxBytes=maxBytes;
    }
    private String field(String column,String row){return column.length()+":"+column+row;}
    public synchronized void put(String task,String column,String row,Object value){
        if(task==null || column==null || row==null)throw new IllegalArgumentException("引用键不能为空");
        String key=field(column,row);Map<String,Entry> values=tasks.get(task);
        Entry old=values==null?null:values.get(key);
        // 估算对象、哈希节点及 UTF-16 字符串占用，不等同于 JVM 堆的硬上限。
        long cost=192L+2L*(task.length()+key.length()+(value==null?0:value.toString().length()));
        long nextEntries=entries+(old==null?1:0),nextBytes=bytes-(old==null?0:old.bytes)+cost;
        if(nextEntries>maxEntries || nextBytes>maxBytes)throw new IllegalStateException("引用缓存达到容量上限，请减少生成量、提高缓存上限或启用 Redis 模式");
        if(values==null){values=new ConcurrentHashMap<>();tasks.put(task,values);}
        values.put(key,new Entry(value,cost));entries=nextEntries;bytes=nextBytes;
    }
    public Object get(String task,String column,String row){
        Map<String,Entry> values=tasks.get(task);Entry entry=values==null?null:values.get(field(column,row));
        return entry==null?null:entry.value;
    }
    public synchronized void removeTask(String task){
        Map<String,Entry> removed=tasks.remove(task);
        if(removed!=null){entries-=removed.size();for(Entry e:removed.values())bytes-=e.bytes;}
    }
    public synchronized long size(){return entries;}
    public synchronized long estimatedBytes(){return bytes;}
}
