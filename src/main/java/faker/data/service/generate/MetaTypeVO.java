package faker.data.service.generate;

import lombok.Data;

import java.util.List;

@Data
public class MetaTypeVO {

    //标识
    private String code;

    //固定范围大小数字/长度字符串的下界
    private Integer minSize;

    //固定范围大小数字/长度字符串的上界
    private Integer maxSize;

    //正则表达式
    private String styleReg;

    //数据引用其他表字段
    private String[] refOtherTableColumn;

    /** 来源字段 -> {field: 本行字段} 或 {value: 常量}；省略时保留轮转引用。 */
    private java.util.Map<String, java.util.Map<String,Object>> refMatch;
    private String refOnMissing;
    private String refOnMultiple;

    //同表字段引用：被引用的本表列名（如 姓氏 引用 name）
    private String refSelfColumn;

    //同表字段引用转换方式：FIRST_CHAR=取首字；为空则直接取原值
    private String refSelfTransform;

    //步长（用于递增数值等需要步进的场景）
    private Integer step;

    //时间范围
    private Long[] dateRange;

    //手动输入候选项列表（每项含值 + 权重，替代原 textarea 多行文本）
    private List<InputItem> inputs;

    /** EXT.FIELD：仅为本字段选择已安装的可信本地扩展。 */
    private String extension;
    private java.util.Map<String,Object> extensionParameters;

    // ---- 条件规则新增字段 ----
    // SpEL 条件表达式（如 "#user_type == 'vip'"），null = 非条件规则
    private String condition;

    // 条件成立时执行的规则列表
    private List<MetaTypeVO> onTrue;

    // 条件不成立时执行的规则列表
    private List<MetaTypeVO> onFalse;

    @Data
    public static class InputItem {
        private String value;   // 候选项值
        private Integer weight; // 权重（null/0 运行时当 1 处理）
    }

}
