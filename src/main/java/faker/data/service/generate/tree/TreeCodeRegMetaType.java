package faker.data.service.generate.tree;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.IMetaType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 结构性编码规则（REG 类别）：输出当前节点的结构性编码（如 "010102"）。
 * 与 STRUCT.TREE_CODE 行为一致，但在 UI 上可通过「规则变量」tab 配置，
 * 位宽配置可写入 styleReg 字段（仅作信息记录，实际位宽由表级 TreeConfig 决定）。
 */
@Component
public class TreeCodeRegMetaType implements IMetaType {

    @Override
    public String getCode() {
        return "REG.TREE_CODE";
    }

    @Override
    public String getTitle() {
        return "结构性编码";
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