package faker.data.domain;

import lombok.Data;

/** 对符合条件的父记录逐行生成固定数量的子记录。 */
@Data
public class ParentRowsConfig {
    private String table;
    private String condition;
    private Integer rowsPerParent = 1;
}
