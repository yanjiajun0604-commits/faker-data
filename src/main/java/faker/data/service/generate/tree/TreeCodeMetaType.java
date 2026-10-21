package faker.data.service.generate.tree;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.IMetaType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 树形编码规则：输出当前节点的结构性编码（如 "010102"）。
 * 编码由 TreeTopologyEngine 预计算，通过 extendArg.treeContext 传入。
 * <p>
 * 适用场景：
 * <ul>
 *   <li>结构性编码列（treeCodeField）：固定使用</li>
 *   <li>编码即 ID 场景（nodeField = id 列）：编码作为主键值</li>
 * </ul>
 */
@Component
public class TreeCodeMetaType implements IMetaType {

    @Override
    public String getCode() {
        return "STRUCT.TREE_CODE";
    }

    @Override
    public String getTitle() {
        return "树形编码";
    }

    @Override
    public Object parse(String transId, String curTableName,
                        ColumnInfo columnInfo, Map<String, Object> extendArg) {
        TreeContext ctx = (TreeContext) extendArg.get("treeContext");
        if (ctx == null) {
            return null;
        }
        return ctx.getCode();
    }
}
