package faker.data.service.generate.tree;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.IMetaType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 树形父节点引用规则：输出当前节点的父节点值（任何类型）。
 * 父节点值由引擎在 BFS 生成时预查并注入 extendArg.parentValue。
 * <p>
 * 关键：无论 nodeField 列用了什么规则（UUID、人名、编码、身份证...），
 * 父节点的值都通过 BFS 索引从 TreeState 中取出，统一注入此处。
 * <p>
 * 适用范围：树形表的 parentRefField 列（如 PARENT_ID）。
 */
@Component
public class TreeParentMetaType implements IMetaType {

    @Override
    public String getCode() {
        return "STRUCT.TREE_PARENT";
    }

    @Override
    public String getTitle() {
        return "树形父节点引用";
    }

    @Override
    public Object parse(String transId, String curTableName,
                        ColumnInfo columnInfo, Map<String, Object> extendArg) {
        // parentValue 由 AbstractJdbcService.generateTreeData() 预查后注入
        // 根节点时为 null（父节点不存在）
        return extendArg.get("parentValue");
    }
}
