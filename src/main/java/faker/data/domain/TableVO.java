package faker.data.domain;

import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class TableVO {
    private String name;
    private Integer size;
    private List<ColumnInfo> columns;
    private Set<String> refOtherTables = new HashSet<>();

    /** 非 null 时只读取已有行作为引用数据，不向此表插入。 */
    private ExistingDataConfig existingData;

    private ParentRowsConfig parentRows;

    /** 可选：事先声明允许变化的列及后续业务派生记录，供按批次核查清理。 */
    private OwnershipConfig ownership;

    /** 树形结构配置。非 null 时表示该表为树形结构表，使用 BFS 拓扑引擎生成。 */
    private TreeConfig treeConfig;
}
