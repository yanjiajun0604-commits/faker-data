package faker.data.service.generate.tree;

import faker.data.domain.TreeConfig;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * 树拓扑预计算引擎。
 * 使用 BFS 遍历生成完整树拓扑，输出按 BFS 顺序排列的节点列表。
 * 父节点始终在子节点之前，因此 TreeState 可按索引直查。
 */
public class TreeTopologyEngine {

    private final Random random;

    public TreeTopologyEngine() { random = new Random(); }

    /** 独立实例使用相同种子时，计数与生成会得到同一棵树。 */
    public TreeTopologyEngine(long seed) { random = new Random(seed); }

    /**
     * 只按 BFS 层级统计节点，不分配拓扑或节点编码。
     * 此方法消耗随机序列，实际生成须使用具有相同初始种子的另一个实例。
     */
    public int count(TreeConfig config) {
        int roots = randomInt(config.getMinRoots(), config.getMaxRoots());
        if (config.isTreeCodeEnabled() && config.getReservedRootCodes() != null
                && !config.getReservedRootCodes().isEmpty()) {
            roots = config.getReservedRootCodes().size();
        }
        long levelCount = Math.max(0, roots);
        long total = levelCount;
        for (int depth = 0; levelCount > 0 && depth < config.getMaxDepth() - 1; depth++) {
            long nextLevel = 0;
            for (long node = 0; node < levelCount; node++) {
                nextLevel += Math.max(0, randomInt(config.getMinChildrenPerNode(), config.getMaxChildrenPerNode()));
                if (total + nextLevel > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("树节点数量超过支持的上限");
                }
            }
            total += nextLevel;
            levelCount = nextLevel;
        }
        return (int) total;
    }

    /**
     * 预计算整棵树的拓扑结构。
     *
     * @param config 树配置
     * @return BFS 顺序的节点列表
     */
    public List<TreeContext> compute(TreeConfig config) {
        List<TreeContext> result = new ArrayList<>();
        int counter = 0;

        int maxDepth = config.getMaxDepth();
        int minRoots = config.getMinRoots();
        int maxRoots = config.getMaxRoots();
        int minChildren = config.getMinChildrenPerNode();
        int maxChildren = config.getMaxChildrenPerNode();
        boolean treeCodeEnabled = config.isTreeCodeEnabled();

        // 1. 生成根节点
        int rootCount = randomInt(minRoots, maxRoots);
        Queue<TreeContext> queue = new LinkedList<>();

        if (treeCodeEnabled) {
            List<Integer> bitWidths = config.getLayerBitWidths();
            List<String> reservedRoots = config.getReservedRootCodes();
            int rootBitWidth = getBitWidth(bitWidths, 0);

            if (reservedRoots != null && !reservedRoots.isEmpty()) {
                // 使用预留根编码
                for (int i = 0; i < reservedRoots.size(); i++) {
                    TreeContext root = new TreeContext(counter++, -1, 0, i + 1,
                            reservedRoots.get(i), null);
                    result.add(root);
                    queue.add(root);
                }
            } else {
                // 自动生成根编码
                for (int i = 1; i <= rootCount; i++) {
                    String code = zeroPad(i, rootBitWidth);
                    TreeContext root = new TreeContext(counter++, -1, 0, i, code, null);
                    result.add(root);
                    queue.add(root);
                }
            }
        } else {
            // 无编码模式：只建拓扑结构
            for (int i = 1; i <= rootCount; i++) {
                TreeContext root = new TreeContext(counter++, -1, 0, i, null, null);
                result.add(root);
                queue.add(root);
            }
        }

        // 2. BFS 生成子树
        while (!queue.isEmpty()) {
            TreeContext node = queue.poll();
            if (node.getDepth() >= maxDepth - 1) {
                continue; // 已达最大深度，叶子节点
            }

            int childCount = randomInt(minChildren, maxChildren);
            int childLevel = node.getDepth() + 1;

            for (int sIdx = 1; sIdx <= childCount; sIdx++) {
                String code = null;
                String parentCode = null;

                if (treeCodeEnabled) {
                    List<Integer> bitWidths = config.getLayerBitWidths();
                    int bitWidth = getBitWidth(bitWidths, childLevel);
                    code = node.getCode() + zeroPad(sIdx, bitWidth);
                    parentCode = node.getCode();
                }

                TreeContext child = new TreeContext(counter++, node.getNodeIndex(),
                        childLevel, sIdx, code, parentCode);
                result.add(child);
                queue.add(child);
            }
        }

        return result;
    }

    /**
     * 获取指定层深的编码位宽。
     * 超出数组长度时，用最后一个值。
     */
    private int getBitWidth(List<Integer> bitWidths, int depth) {
        if (bitWidths == null || bitWidths.isEmpty()) {
            return 2; // 默认值
        }
        if (depth < bitWidths.size()) {
            return bitWidths.get(depth);
        }
        return bitWidths.get(bitWidths.size() - 1);
    }

    /**
     * 零填充：zeroPad(1, 2) → "01"、zeroPad(12, 3) → "012"。
     */
    static String zeroPad(int num, int width) {
        String format = "%0" + width + "d";
        return String.format(format, num);
    }

    private int randomInt(int min, int max) {
        if (min > max) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }
}
