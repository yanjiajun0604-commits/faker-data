package faker.data.domain;

import faker.data.service.generate.MetaTypeVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ColumnInfo {
    private String name;
    private String javaType;
    private String dbType;
    private Integer length;
    private Integer nullable;

    /** 参与普通规则和引用，仅在当前生成任务内使用，不写入目标表。 */
    private boolean virtual;

    /** 多条规则列表，按顺序拼接生成最终值 */
    private List<MetaTypeVO> regs = new ArrayList<>();

    private boolean depended;
}
