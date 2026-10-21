package faker.data.domain;

import lombok.Data;

import java.util.List;

/**
 * 树形结构表级配置。
 * 用户在数据列配置区启用「树形结构生成」时，前端自动注入到 TableVO.treeConfig。
 */
@Data
public class TreeConfig {

    // === 字段绑定 ===
    /** 当前节点字段名，如 "ID"。该列的值必须在同表内唯一。 */
    private String nodeField;

    /** 父节点引用字段名，如 "PARENT_ID"。自动使用 STRUCT.TREE_PARENT 规则。 */
    private String parentRefField;

    // === 基本参数 ===
    /** 树的最大深度（根节点 depth=0），>= 1 */
    private int maxDepth;

    /** 最小根节点数 */
    private int minRoots;

    /** 最大根节点数（随机取 [min,max]） */
    private int maxRoots;

    /** 每节点最小子节点数 */
    private int minChildrenPerNode;

    /** 每节点最大子节点数（随机取 [min,max]） */
    private int maxChildrenPerNode;

    // === 高级：结构性编码（可选） ===
    /** 是否启用结构性编码。false 时下方字段均不生效。 */
    private boolean treeCodeEnabled;

    /** 结构性编码写入的列名，如 "TREE_CODE"（treeCodeEnabled=true 时必填） */
    private String treeCodeField;

    /** 每层编码位宽，如 [2,3,2] 表示 L1=2位, L2=3位, L3=2位。超出数组长度时用最后一个值。 */
    private List<Integer> layerBitWidths;

    /** 预留根编码列表。非 null + 非空时使用这些编码作为根节点，不再自动生成。 */
    private List<String> reservedRootCodes;
}
