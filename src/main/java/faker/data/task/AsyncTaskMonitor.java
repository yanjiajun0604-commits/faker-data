package faker.data.task;

public interface AsyncTaskMonitor {

    /**
     * 同时更新进度和信息
     *
     * @param progress
     * @param message
     */
    String progressAndMessage(AsyncTaskExecutor asyncTaskExecutor, double progress, String message);

    /**
     * 是否取消
     *
     * @return
     */
    boolean isCancel();

    /**
     * 结束任务
     *
     * @param result 结果
     * @param detail 细节
     */
    void finish(String result, Object detail);

    /**
     * 结束任务 （不发布FinishTaskEvent事件）
     *
     * @param result 结果
     * @param detail 细节
     */
    default void finished(String result, Object detail) {
    }

    ;

    /**
     * 开始取消任务
     *
     * @param result 结果
     * @param detail 细节
     */
    void canceling(String result, Object detail);

    /**
     * 取消任务成功
     *
     * @param result 结果
     * @param detail 细节
     */
    void canceled(String result, Object detail);

    /**
     * 异常结束，任务状态将记为ERROR
     *
     * @param result 结果
     * @param t      异常原因
     */
    void error(String result, Throwable t);

    /**
     * 异常结束，指定执行器对应任务为ERROR（推荐使用）
     *
     * @param executor 执行器
     * @param result   错误描述
     * @param t        异常原因
     */
    default void error(AsyncTaskExecutor executor, String result, Throwable t) {
        error(result, t);
    }

    /**
     * 业务异常结束，任务状态将记为ERROR
     *
     * @param result 异常原因
     * @param t      异常
     * @param detail 细节
     */
    default void error(String result, Throwable t, String detail) {
        error(result, t);
    }

    /**
     * 是否主动调用了结束
     *
     * @return
     */
    boolean isFinish();


    /**
     * 查询任务进度（包含状态）
     *
     * @param taskId 任务ID
     * @return 包含 progress / state / errorMsg 的结果对象
     */
    TaskProcessVO queryProcess(String taskId);



}
