package faker.data.cache;

/** 跨表引用值按任务隔离，任务退出后统一释放。 */
public interface ReferenceCache {
    void put(String taskId, String columnKey, String rowId, Object value);
    Object get(String taskId, String columnKey, String rowId);
    void removeTask(String taskId);
}
