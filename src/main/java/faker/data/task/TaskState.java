package faker.data.task;

import java.util.Arrays;
import java.util.Optional;

public enum TaskState {

    /**
     * 排队
     */
    WAITING(0, "排队"),
    /**
     * 进行中
     */
    PROCESSING(1, "进行中"),
    /**
     * 取消
     */
    CANCELED(2, "取消"),
    /**
     * 取消中
     */
    CANCELING(3, "取消中"),
    /**
     * 完成
     */
    FINISHED(4, "完成"),
    /**
     * 异常结束
     */
    ERROR(5, "异常结束"),
    /**
     * 超时
     */
    OVERTIME(6, "超时"),
    /**
     * 查询不到
     */
    NONE(7, "查询不到"),
    /**
     * 超出排队数
     */
    OUTOFQUEUE(8, "超出排队数");

    private int value;
    private String title;

    TaskState(int value, String title) {
        this.value = value;
        this.title = title;
    }

    public int getValue() {
        return value;
    }

    public String getTitle() {
        return title;
    }

    public static TaskState getTaskStateByValue(int value) {
        Optional<TaskState> authority = Arrays.stream(TaskState.values()).
                filter(o -> o.getValue() == value)
                .findFirst();
        return authority.orElse(NONE);
    }
}
