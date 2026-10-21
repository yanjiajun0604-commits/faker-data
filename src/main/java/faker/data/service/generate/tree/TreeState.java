package faker.data.service.generate.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * 树形数据生成时的运行时状态。
 * 按 BFS 序号存储每行的 nodeField 值，子节点通过 parentIndex 反查父节点值。
 * <p>
 * BFS 顺序天然保证父节点在子节点之前生成，因此 ArrayList 按索引直查即可，
 * 无需 HashMap 做编码→UUID 的映射。
 */
public class TreeState {

    private final List<Object> values = new ArrayList<>();

    /**
     * 当前节点生成完毕后调用，记录 BFS 位置的节点值。
     */
    public void put(int nodeIndex, Object value) {
        while (values.size() <= nodeIndex) {
            values.add(null);
        }
        values.set(nodeIndex, value);
    }

    /**
     * 子节点查父节点值时调用。返回父节点的 nodeField 值（可能是 UUID、人名、编码等任意类型）。
     */
    public Object get(int parentIndex) {
        if (parentIndex < 0 || parentIndex >= values.size()) {
            return null;
        }
        return values.get(parentIndex);
    }
}
