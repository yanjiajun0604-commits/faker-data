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

    /** 树形结构配置。非 null 时表示该表为树形结构表，使用 BFS 拓扑引擎生成。 */
    private TreeConfig treeConfig;
}
