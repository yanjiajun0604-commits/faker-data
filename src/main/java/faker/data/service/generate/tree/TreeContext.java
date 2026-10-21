package faker.data.service.generate.tree;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * BFS 预计算后的单个节点上下文。
 * 从 TreeTopologyEngine.compute() 输出，按 BFS 顺序排列。
 */
@Data
@AllArgsConstructor
public class TreeContext {

    /** BFS 序号（0-based），用于 TreeState 的索引查找 */
    private int nodeIndex;

    /** 父节点的 BFS 序号，根节点为 -1 */
    private int parentIndex;

    /** 层深（根=0） */
    private int depth;

    /** 在同父兄弟中的序号（1-based） */
    private int siblingIndex;

    /** 结构性编码（如 "010102"）。仅 treeCodeEnabled=true 时有值，否则为 null。 */
    private String code;

    /** 父节点的结构性编码（如 "0101"）。仅 treeCodeEnabled=true 时有值，根节点为 null。 */
    private String parentCode;
}
