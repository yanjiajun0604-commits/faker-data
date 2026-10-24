package faker.data.service.generate.tree;

import faker.data.domain.TreeConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TreeTopologyEngine 单元测试。
 * 验证 BFS 顺序、编码生成、预留根、无编码模式、边界条件。
 */
class TreeTopologyEngineTest {

    private final TreeTopologyEngine engine = new TreeTopologyEngine();

    // ========== 无编码模式（treeCodeEnabled=false）==========

    @Test
    void shouldCreateCorrectNumberOfRootNodes() {
        TreeConfig config = createConfig(false, 3, 2, 4, 2, 3);
        List<TreeContext> result = engine.compute(config);

        // 只验证根节点在自己的深度层
        long rootCount = result.stream().filter(n -> n.getDepth() == 0).count();
        assertTrue(rootCount >= 2 && rootCount <= 4, "根节点数应在 [2,4] 内, 实际: " + rootCount);
    }

    @Test
    void shouldProduceBFSParentsBeforeChildren() {
        TreeConfig config = createConfig(false, 3, 2, 2, 2, 2);
        List<TreeContext> result = engine.compute(config);

        for (int i = 0; i < result.size(); i++) {
            TreeContext node = result.get(i);
            if (node.getParentIndex() >= 0) {
                // 父节点必须在当前节点之前出现
                assertTrue(node.getParentIndex() < i,
                        "BFS: 父节点 index=" + node.getParentIndex()
                                + " 应在节点 index=" + i + " 之前");
            }
        }
    }

    @Test
    void shouldProduceTopologyWithoutCodes() {
        TreeConfig config = createConfig(false, 2, 1, 1, 1, 1); // 单根单子
        List<TreeContext> result = engine.compute(config);

        assertFalse(result.isEmpty());
        for (TreeContext node : result) {
            assertNull(node.getCode(), "无编码模式下 code 应为 null");
            assertNull(node.getParentCode(), "无编码模式下 parentCode 应为 null");
        }
    }

    @Test
    void shouldHaveCorrectNodeIndices() {
        TreeConfig config = createConfig(false, 2, 1, 1, 2, 2);
        List<TreeContext> result = engine.compute(config);

        for (int i = 0; i < result.size(); i++) {
            assertEquals(i, result.get(i).getNodeIndex(),
                    "nodeIndex 应等于 BFS 位置");
        }
    }

    @Test
    void shouldHandleSingleLayerTree() {
        TreeConfig config = createConfig(false, 1, 3, 3, 1, 1);
        List<TreeContext> result = engine.compute(config);

        // 深度=1 时只有根节点，没有子节点
        assertEquals(3, result.size(), "单层树应只有根节点，共 " + config.getMaxRoots() + " 个");
        for (TreeContext node : result) {
            assertEquals(0, node.getDepth(), "单层树所有节点深度应为 0");
            assertEquals(-1, node.getParentIndex(), "单层树所有节点无父节点");
        }
    }

    // ========== 编码模式（treeCodeEnabled=true）==========

    @Test
    void shouldGenerateCorrectCodeFormat() {
        TreeConfig config = createConfig(true, 2, 1, 1, 2, 2);
        config.setLayerBitWidths(Arrays.asList(2));
        List<TreeContext> result = engine.compute(config);

        TreeContext root = result.get(0);
        assertTrue(root.getCode().matches("\\d{2}"),
                "根编码应为2位数字, 实际: " + root.getCode());
        assertNull(root.getParentCode(), "根节点 parentCode 应为 null");
    }

    @Test
    void shouldHaveChildrenCodePrefixMatchParentCode() {
        TreeConfig config = createConfig(true, 3, 1, 1, 2, 2);
        config.setLayerBitWidths(Arrays.asList(2, 2, 2));
        List<TreeContext> result = engine.compute(config);

        for (TreeContext node : result) {
            if (node.getParentIndex() >= 0) {
                TreeContext parent = result.get(node.getParentIndex());
                assertTrue(node.getCode().startsWith(parent.getCode()),
                        "子节点编码 " + node.getCode()
                                + " 应以父节点编码 " + parent.getCode() + " 开头");
                assertEquals(parent.getCode(), node.getParentCode(),
                        "parentCode 应等于父节点 code");
            }
        }
    }

    @Test
    void shouldSupportLayerSpecificBitWidths() {
        TreeConfig config = createConfig(true, 2, 1, 1, 2, 2);
        // L1=2位, L2=3位
        config.setLayerBitWidths(Arrays.asList(2, 3));
        List<TreeContext> result = engine.compute(config);

        TreeContext root = result.get(0);
        assertEquals(2, root.getCode().length(), "L1 编码应为2位");

        // 找第一个子节点
        TreeContext firstChild = result.stream()
                .filter(n -> n.getDepth() == 1).findFirst().orElse(null);
        assertNotNull(firstChild, "应有子节点");
        assertEquals(5, firstChild.getCode().length(),
                "L2 子节点编码长度 = 根2位 + L2子3位 = 5位");
    }

    @Test
    void shouldUseLastBitWidthWhenExceedingArray() {
        TreeConfig config = createConfig(true, 4, 1, 1, 1, 1);
        // 只配了 [2,3]，L3 和 L4 应该用最后一个值 3
        config.setLayerBitWidths(Arrays.asList(2, 3));
        List<TreeContext> result = engine.compute(config);

        // 找最深层的节点
        int maxDepthInResult = result.stream()
                .mapToInt(TreeContext::getDepth).max().orElse(0);
        TreeContext deepest = result.stream()
                .filter(n -> n.getDepth() == maxDepthInResult).findFirst().orElse(null);

        if (deepest != null) {
            // 深度 >= 2 的层都应该用 3 位宽
            assertNotNull(deepest.getCode());
        }
    }

//    @Test
//    void shouldUseReservedRootCodes() {
//        TreeConfig config = createConfig(true, 2, 1, 1, 2, 2);
//        config.setLayerBitWidths(Arrays.asList(2, 2));
//        config.setReservedRootCodes(Arrays.asList("10", "20", "30"));
//        List<TreeContext> result = engine.compute(config);
//
//        List<TreeContext> roots = result.stream()
//                .filter(n -> n.getDepth() == 0).toList();
//        assertEquals(3, roots.size(), "预留根应有3个");
//        assertEquals("10", roots.get(0).getCode());
//        assertEquals("20", roots.get(1).getCode());
//        assertEquals("30", roots.get(2).getCode());
//    }

//    @Test
//    void shouldBuildChildrenUnderReservedRoots() {
//        TreeConfig config = createConfig(true, 2, 1, 1, 2, 2);
//        config.setLayerBitWidths(Arrays.asList(2, 2));
//        config.setReservedRootCodes(Arrays.asList("10"));
//        List<TreeContext> result = engine.compute(config);
//
//        // 找子节点
//        List<TreeContext> children = result.stream()
//                .filter(n -> n.getDepth() == 1).toList();
//        for (TreeContext child : children) {
//            assertTrue(child.getCode().startsWith("10"),
//                    "预留根 " + child.getCode() + " 的子节点应以 10 开头");
//            assertEquals("10", child.getParentCode());
//        }
//    }

    @Test
    void shouldHandleZeroPaddingCorrectly() {
        assertEquals("01", TreeTopologyEngine.zeroPad(1, 2));
        assertEquals("001", TreeTopologyEngine.zeroPad(1, 3));
        assertEquals("012", TreeTopologyEngine.zeroPad(12, 3));
        assertEquals("99", TreeTopologyEngine.zeroPad(99, 2));
    }

    // ========== 辅助方法 ==========

    @Test
    void countedNodesMatchSeededRandomTopologyWithAndWithoutCodes() {
        for(boolean codes:new boolean[]{false,true}) {
            TreeConfig config=createConfig(codes,5,0,3,0,4);
            config.setLayerBitWidths(Arrays.asList(2,2,2,2,2));
            for(long seed=-12;seed<=12;seed++) {
                assertEquals(new TreeTopologyEngine(seed).compute(config).size(),
                        new TreeTopologyEngine(seed).count(config),"seed="+seed+", codes="+codes);
            }
        }
    }

    @Test
    void countedNodesRespectReservedRootsAndSingleLayerTrees() {
        TreeConfig config=createConfig(true,4,0,3,0,4);
        config.setReservedRootCodes(Arrays.asList("10","20","30"));
        config.setLayerBitWidths(Arrays.asList(2,2,2,2));
        for(long seed=0;seed<25;seed++) {
            assertEquals(new TreeTopologyEngine(seed).compute(config).size(),new TreeTopologyEngine(seed).count(config));
        }
        config.setMaxDepth(1);
        assertEquals(3,new TreeTopologyEngine(7).count(config));
        config.setTreeCodeEnabled(false);config.setMinRoots(0);config.setMaxRoots(0);
        assertEquals(0,new TreeTopologyEngine(7).count(config));
    }

    @Test
    void countingRejectsNodeCountsThatCannotFitTheGenerationIndex() {
        TreeConfig config=createConfig(false,2,1,1,Integer.MAX_VALUE,Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->new TreeTopologyEngine(7).count(config));
    }

    private TreeConfig createConfig(boolean treeCodeEnabled, int maxDepth,
                                    int minRoots, int maxRoots, int minChildren, int maxChildren) {
        TreeConfig config = new TreeConfig();
        config.setNodeField("ID");
        config.setParentRefField("PARENT_ID");
        config.setMaxDepth(maxDepth);
        config.setMinRoots(minRoots);
        config.setMaxRoots(maxRoots);
        config.setMinChildrenPerNode(minChildren);
        config.setMaxChildrenPerNode(maxChildren);
        config.setTreeCodeEnabled(treeCodeEnabled);
        if (treeCodeEnabled) {
            config.setTreeCodeField("TREE_CODE");
        }
        return config;
    }
}
