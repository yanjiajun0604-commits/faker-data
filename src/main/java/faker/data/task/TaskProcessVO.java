package faker.data.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * 任务进度查询结果 VO
 * 包含进度、状态、错误信息，供前端判断展示成功或失败。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskProcessVO {
    /** 执行进度（0-100） */
    private Double progress;
    /** 任务状态 */
    private TaskState state;
    /** 错误消息（仅 ERROR 状态时有值） */
    private String errorMsg;
    /** 任务结果描述（如"执行完成"、"执行异常"） */
    private String result;
}
