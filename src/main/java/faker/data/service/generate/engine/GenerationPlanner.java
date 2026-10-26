package faker.data.service.generate.engine;

import faker.data.domain.*;
import faker.data.service.generate.*;
import java.util.*;
import java.util.stream.Collectors;

/** 任务准备阶段的依赖排序，保留稳定的表/列顺序和循环依赖检测。 */
public final class GenerationPlanner {
    private static final java.util.regex.Pattern CONDITION_REF_PATTERN =
            java.util.regex.Pattern.compile("#([a-zA-Z_\\u4e00-\\u9fa5][a-zA-Z0-9_\\u4e00-\\u9fa5]*)");

    public List<TableVO> plan(List<TableVO> tables) {
        for(TableVO table:tables) {
            table.getRefOtherTables().clear();
            for(ColumnInfo column:table.getColumns())column.setDepended(false);
        }
        List<TableVO> ordered=orderTables(tables);
        for(TableVO table:ordered)table.setColumns(sortColumnsByConditionDeps(table.getColumns()));
        return ordered;
    }

    //拓扑排序，找到合适的顺序给表生成数据
    public List<TableVO> orderTables(List<TableVO> tables) {

        // 构建小写映射，解决不同数据库/JDBC返回大小写不一致的问题
        Map<String, TableVO> tableMap = tables.stream()
                .collect(Collectors.toMap(t -> t.getName().toLowerCase(Locale.ROOT), t -> t));

        //1.构建图
        for (TableVO tableVO : tables) {
            ParentRowsConfig parent=tableVO.getParentRows();
            if(parent!=null) {
                if(tableVO.getExistingData()!=null || tableVO.getTreeConfig()!=null)
                    throw new IllegalArgumentException("parentRows cannot use existingData or treeConfig");
                if(parent.getRowsPerParent()==null || parent.getRowsPerParent()<1 || parent.getRowsPerParent()>10000)
                    throw new IllegalArgumentException("parentRows.rowsPerParent must be between 1 and 10000");
                TableVO source=parent.getTable()==null?null:tableMap.get(parent.getTable().toLowerCase(Locale.ROOT));
                if(source==null)throw new IllegalArgumentException("Unknown parentRows table");
                source.getRefOtherTables().add(tableVO.getName());
                // 筛选表达式使用父记录上下文，同一行的值由引用缓存统一保存。
                if(parent.getCondition()!=null && !parent.getCondition().trim().isEmpty())source.getColumns().forEach(c->c.setDepended(true));
            }
            for (ColumnInfo column : tableVO.getColumns()) {
                List<MetaTypeVO> allRegs = column.getRegs();
                if (allRegs == null || allRegs.isEmpty()) {
                    continue;
                }
                visitRules(allRegs, reg -> {
                    if(reg.getRefMatch()!=null && !"REF.OTHER_TABLE".equals(reg.getCode()))
                        throw new IllegalArgumentException("refMatch requires REF.OTHER_TABLE");
                    String[] refArr = reg.getRefOtherTableColumn();
                    if ("REF.OTHER_TABLE".equals(reg.getCode())) {
                        if(refArr==null || refArr.length!=2 || refArr[0]==null || refArr[1]==null)
                            throw new IllegalArgumentException("REF.OTHER_TABLE requires a table and column");
                        TableVO refTab = tableMap.get(refArr[0].toLowerCase(Locale.ROOT));
                        if (refTab == null)throw new IllegalArgumentException("Unknown referenced table: "+refArr[0]);
                        refTab.getRefOtherTables().add(tableVO.getName());
                        String refCol = refArr[1].toLowerCase(Locale.ROOT);
                        ColumnInfo columnInfo = refTab.getColumns().stream()
                                .filter(col -> col.getName().toLowerCase(Locale.ROOT).equals(refCol))
                                .findFirst().orElse(null);
                        if (columnInfo != null) {
                            columnInfo.setDepended(true);
                        } else throw new IllegalArgumentException("Unknown referenced column: "+refArr[1]);
                        if(reg.getRefMatch()!=null) {
                            new ReferenceLookup(reg);
                            reg.getRefMatch().forEach((source,binding)->{
                                ColumnInfo matchColumn=refTab.getColumns().stream().filter(c->c.getName().equalsIgnoreCase(source)).findFirst()
                                    .orElseThrow(()->new IllegalArgumentException("Unknown refMatch source column: "+source));
                                matchColumn.setDepended(true);
                                if(binding.containsKey("field")) {
                                    String target=(String)binding.get("field");
                                    if(tableVO.getColumns().stream().noneMatch(c->c.getName().equalsIgnoreCase(target)))
                                        throw new IllegalArgumentException("Unknown refMatch target column: "+target);
                                }
                            });
                        }
                    }
                });
            }
        }

        //统计入度（统一小写 key）
        Map<String, Integer> cnt = new HashMap<>();
        for (TableVO tableVO : tables) {
            cnt.putIfAbsent(tableVO.getName().toLowerCase(Locale.ROOT), 0);
            for (String refOtherTable : tableVO.getRefOtherTables()) {
                cnt.merge(refOtherTable.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
        }

        Queue<TableVO> queue = new LinkedList<>();
        for (int i = 0; i < tables.size(); i++) {
            TableVO tableVO = tables.get(i);
            if (cnt.get(tableVO.getName().toLowerCase(Locale.ROOT)) == 0) {
                queue.add(tableVO);
            }
        }

        List<TableVO> res = new ArrayList<>();
        while (!queue.isEmpty()) {
            TableVO poll = queue.poll();
            res.add(poll);
            for (String refOtherTable : poll.getRefOtherTables()) {
                String lowerRef = refOtherTable.toLowerCase(Locale.ROOT);
                cnt.merge(lowerRef, -1, Integer::sum);
                if (cnt.get(lowerRef) == 0) {
                    queue.add(tableMap.get(lowerRef));
                }
            }
        }

        if (res.size() != tables.size()) {
            throw new RuntimeException("表之间存在循环依赖");
        }

        return res;
    }

    private void visitRules(List<MetaTypeVO> rules,java.util.function.Consumer<MetaTypeVO> visitor) {
        if(rules==null)return;
        for(MetaTypeVO rule:rules) {
            visitor.accept(rule);
            visitRules(rule.getOnTrue(),visitor);
            visitRules(rule.getOnFalse(),visitor);
        }
    }


    /**
     * 对列按条件规则依赖进行拓扑排序。
     * 条件规则中 #字段名 引用的列必须在条件列之前生成。
     * 无依赖的列保持原有相对顺序。
     *
     * @param columns 原始列列表
     * @return 按依赖排序后的列列表
     */
    public List<ColumnInfo> sortColumnsByConditionDeps(List<ColumnInfo> columns) {
        if (columns == null || columns.isEmpty()) {
            return columns == null ? new ArrayList<>() : new ArrayList<>(columns);
        }

        // 1. 构建列名 → 索引映射（统一小写，兼容不同数据库/JDBC 的大小写差异）
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            nameToIdx.put(columns.get(i).getName().toLowerCase(Locale.ROOT), i);
        }

        // 2. 构建依赖图 & 入度（统一小写 key）
        Map<String, Set<String>> deps = new HashMap<>(); // colName → 依赖该列的列名集合
        Map<String, Integer> inDegree = new HashMap<>();
        List<String> colNames = new ArrayList<>();

        for (ColumnInfo col : columns) {
            String name = col.getName().toLowerCase(Locale.ROOT);
            colNames.add(name);
            deps.putIfAbsent(name, new HashSet<>());
            inDegree.putIfAbsent(name, 0);
        }

        for (ColumnInfo col : columns) {
            Set<String> refs = extractConditionRefs(col);
            String colName = col.getName().toLowerCase(Locale.ROOT);
            for (String ref : refs) {
                String lowerRef = ref.toLowerCase(Locale.ROOT);
                if (nameToIdx.containsKey(lowerRef)) {
                    deps.get(lowerRef).add(colName);
                    inDegree.merge(colName, 1, Integer::sum);
                }
            }
        }

        // 3. Kahn 拓扑排序（同时保持原始顺序稳定性）
        //    入度为 0 的列按原始索引先后入队（key 统一小写）
        Queue<String> queue = new LinkedList<>();
        for (int i = 0; i < columns.size(); i++) {
            String name = columns.get(i).getName().toLowerCase(Locale.ROOT);
            if (inDegree.get(name) == 0) {
                queue.offer(name);
            }
        }

        List<ColumnInfo> sorted = new ArrayList<>();
        Map<String, ColumnInfo> colMap = columns.stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(Locale.ROOT), c -> c));

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            sorted.add(colMap.get(cur));
            for (String dependent : deps.getOrDefault(cur, Collections.emptySet())) {
                int d = inDegree.merge(dependent, -1, Integer::sum);
                if (d == 0) {
                    queue.offer(dependent);
                }
            }
        }

        // 4. 循环依赖检测
        if (sorted.size() != columns.size()) {
            Set<String> cycleNames = new HashSet<>(colNames);
            sorted.forEach(c -> cycleNames.remove(c.getName().toLowerCase(Locale.ROOT)));
            throw new RuntimeException("条件规则存在循环依赖，涉及的字段: " + String.join(", ", cycleNames));
        }

        return sorted;
    }

    /**
     * 递归收集列的条件规则中所有引用的字段名。
     * 正则匹配 "#字段名"，首字符不能是数字（SpEL变量名遵循Java标识符规则），
     * 后续字符支持英文/数字/下划线/中文。
     * 注意：SpEL tokenizer不支持中文变量名，提取出的中文字段仅供依赖分析使用。
     */
    public Set<String> extractConditionRefs(ColumnInfo columnInfo) {
        Set<String> refs = new HashSet<>();
        List<MetaTypeVO> regs = columnInfo.getRegs();
        if (regs == null) return refs;
        extractConditionRefsRecursive(regs, refs);
        return refs;
    }

    private void extractConditionRefsRecursive(List<MetaTypeVO> regs, Set<String> refs) {
        if (regs == null) return;
        for (MetaTypeVO reg : regs) {
            if (reg.getCondition() != null) {
                java.util.regex.Matcher m = CONDITION_REF_PATTERN.matcher(reg.getCondition());
                while (m.find()) {
                    refs.add(m.group(1));
                }
            }
            // 识别同表字段引用（REF.SELF_COLUMN）依赖，确保被引用列在引用列之前生成
            if (reg.getRefSelfColumn() != null) {
                refs.add(reg.getRefSelfColumn());
            }
            if(reg.getRefMatch()!=null)for(Map<String,Object> binding:reg.getRefMatch().values())
                if(binding!=null && binding.get("field") instanceof String)refs.add((String)binding.get("field"));
            // 递归处理嵌套条件
            extractConditionRefsRecursive(reg.getOnTrue(), refs);
            extractConditionRefsRecursive(reg.getOnFalse(), refs);
        }
    }

}
