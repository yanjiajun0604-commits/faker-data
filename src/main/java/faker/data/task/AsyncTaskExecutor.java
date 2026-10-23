package faker.data.task;

import java.util.List;

public interface AsyncTaskExecutor {
    Integer CLEAN_TYPE_NONE = 0;        //清理历史数据方式：无
    Integer CLEAN_TYPE_BYTYPE = 1;        //清理历史数据方式：根据任务类型
    Integer CLEAN_TYPE_BYID = 2;        //清理历史数据方式：根据任务ID

    ThreadLocal<String> taskId = new ThreadLocal<>(); //存储当前线程的任务ID

    void execute(Object args, AsyncTaskMonitor monitor);

    String getTaskPoolType();

    default Integer getCleanDataType() {
        return CLEAN_TYPE_NONE;
    }

    /**
     * 根据任务类型清理历史数据
     */
    default void cleanHistoryData() {

    }

    /**
     * 根据任务ID清理历史数据
     *
     * @param taskIds
     */
    default void cleanHistoryDataByTaskId(List<String> taskIds) {

    }

    String getTitle();

    default void setTaskId(String id) {
        taskId.set(id);
    }

    default String getTaskId() {
        return taskId.get();
    }

}
