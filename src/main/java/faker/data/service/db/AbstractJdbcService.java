package faker.data.service.db;

import cn.hutool.core.convert.Convert;
import faker.data.domain.ColumnInfo;
import faker.data.domain.DataSource;
import faker.data.domain.TableVO;
import faker.data.domain.TreeConfig;
import faker.data.service.generate.tree.TreeContext;
import faker.data.service.generate.tree.TreeState;
import faker.data.service.generate.tree.TreeTopologyEngine;
import faker.data.service.generate.IMetaType;
import faker.data.service.generate.MetaTypeFactory;
import faker.data.service.generate.MetaTypeVO;
import faker.data.service.generate.ref.RefOtherTableMetaType;
import faker.data.utils.ApplicationContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import pers.bbs.asynctask.executor.AsyncTaskExecutor;
import pers.bbs.asynctask.monitor.AsyncTaskMonitor;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractJdbcService implements JdbcService {
    public static final int BATCH_SIZE = 1000;
    // 提取 SpEL 条件表达式中的 #字段名 引用。
    // 首字符排除数字（遵循Java标识符规则，SpEL变量名首字符不能是数字）。
    // 注意：虽然正则支持中文（\u4e00-\u9fa5），但SpEL tokenizer不支持中文变量名，
    // 因此 rowValues 的key需使用英文标识符。
    private static final java.util.regex.Pattern CONDITION_REF_PATTERN =
            java.util.regex.Pattern.compile("#([a-zA-Z_\\u4e00-\\u9fa5][a-zA-Z0-9_\\u4e00-\\u9fa5]*)");
    private final DataSource dataSource;

    public AbstractJdbcService(DataSource dataSource) {
        this.dataSource = dataSource;
        checkDataSource(dataSource);
    }

    /**
     * 校验数据源信息是否有效
     *
     * @param dataSource 数据源
     */
    private void checkDataSource(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null. ");
        }
        if (!StringUtils.hasText(dataSource.getUrl())) {
            throw new IllegalStateException("jdbc url is null. ");
        }
        if (!StringUtils.hasText(dataSource.getUsername()) || !StringUtils.hasText(dataSource.getPassword())) {
            throw new IllegalStateException("jdbc user or password is null. ");
        }
    }

    protected DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 关闭(释放)资源
     *
     * @param conn Connection
     */
    protected void close(Connection conn) {
        close(conn, null, null);
    }

    /**
     * 关闭(释放)资源
     *
     * @param conn Connection
     * @param ps   PreparedStatement
     * @param rs   ResultSet
     */
    protected void close(Connection conn, Statement ps, ResultSet rs) {
        //关闭ResultSet
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                rs = null;
            }
        }
        //关闭PreparedStatement
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                ps = null;
            }
        }
        //关闭Connection
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                conn = null;
            }
        }
    }

    @Override
    public boolean test() {
        Connection conn = null;
        try {
            conn = getConnection();
            if (conn == null) {
                return false;
            }

            DatabaseMetaData meta = conn.getMetaData();
            if (meta == null) {
                return false;
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        } finally {
            close(conn);
        }
        return true;
    }

    /**
     * 获取连接
     *
     * @return Connection
     */
    protected Connection getConnection() {
        Connection conn = null;

        try {
            Class.forName(loadDriverClass());
            conn = DriverManager.getConnection(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
        } catch (Exception e) {

        }
        return conn;
    }

    @Override
    public List<Map<String, Object>> listAllTables(Integer withSub) {
        Connection conn = getConnection();
        if (conn == null) {
            return null;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        ResultSet rs = null;
        try {
            //参数1 int resultSetType
            //ResultSet.TYPE_FORWORD_ONLY 结果集的游标只能向下滚动。
            //ResultSet.TYPE_SCROLL_INSENSITIVE 结果集的游标可以上下移动，当数据库变化时，当前结果集不变。
            //ResultSet.TYPE_SCROLL_SENSITIVE 返回可滚动的结果集，当数据库变化时，当前结果集同步改变。

            //参数2 int resultSetConcurrency
            //ResultSet.CONCUR_READ_ONLY 不能用结果集更新数据库中的表。
            //ResultSet.CONCUR_UPDATETABLE 能用结果集更新数据库中的表

            conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            DatabaseMetaData meta = conn.getMetaData();
            //目录名称; 数据库名; 表名称; 表类型;
            rs = meta.getTables(catalog(), schemaPattern(), tableNamePattern(), types());
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", rs.getString("TABLE_NAME"));
                //加上字段详情信息子表，复用同一连接避免 2000 次连接建立
                if (withSub.equals(1)) {
                    List<ColumnInfo> columnInfos = doListAllFields(conn, rs.getString("TABLE_NAME"));
                    map.put("columns", columnInfos);
                }
                result.add(map);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(conn, null, rs);
        }

        return result;
    }

    @Override
    public List<ColumnInfo> listAllFields(String tableName) {
        Connection conn = getConnection();
        if (conn == null) {
            return null;
        }
        try {
            return doListAllFields(conn, tableName);
        } finally {
            close(conn, null, null);
        }
    }

    /**
     * 获取单表的列信息，复用外部传入的连接，避免 N+1 连接问题。
     * 内部不关闭 conn，由调用方负责关闭。
     */
    protected List<ColumnInfo> doListAllFields(Connection conn, String tableName) {
        PreparedStatement preparedStatement = null;
        ResultSetMetaData resultSetMetaData;
        List<ColumnInfo> res = new ArrayList<>();
        try {
            preparedStatement = conn.prepareStatement("select * from " + tableName + " where 1=2");
            resultSetMetaData = preparedStatement.executeQuery().getMetaData();
            int columnCount = resultSetMetaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                ColumnInfo columnInfo = new ColumnInfo();
                columnInfo.setName(resultSetMetaData.getColumnName(i));
                columnInfo.setJavaType(resultSetMetaData.getColumnClassName(i));
                columnInfo.setDbType(resultSetMetaData.getColumnTypeName(i).toLowerCase());
                columnInfo.setLength(resultSetMetaData.getColumnDisplaySize(i));
                columnInfo.setNullable(resultSetMetaData.isNullable(i));
                res.add(columnInfo);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            // 只关闭 PreparedStatement，不关闭 conn
            close(null, preparedStatement, null);
        }
        return res;
    }

    /**
     * a catalog name; must match the catalog name as it is stored in the database; "" retrieves those without a catalog; null means that the catalog name should not be used to narrow the search
     */
    protected String catalog() {
        return null;
    }

    /**
     * a table name pattern; must match the table name as it is stored in the database
     */
    protected String tableNamePattern() {
        return "%";
    }

    /**
     * a list of table types, which must be from the list of table types returned from ,to include; null returns all types
     */
    protected String[] types() {

        return new String[]{"TABLE"};
    }

    /**
     * 加载驱动class
     *
     * @return class
     */
    protected abstract String loadDriverClass();

    /**
     * a schema name pattern; must match the schema name as it is stored in the database; "" retrieves those without a schema; null means that the schema name should not be used to narrow the search
     */
    protected abstract String schemaPattern();


    @Override
    public void generateData(List<TableVO> tables, AsyncTaskMonitor monitor, AsyncTaskExecutor executor) {
        //任务ID（该任务中等同于事务ID）,用来做缓存中的key, 进行统一管理
        String transId = executor.getTaskId();

        if (!test()) {
            throw new RuntimeException("连接数据库失败");
        }
        //拓扑排序解决table之间循环引用
        final List<TableVO> finalTables = init(tables);

        // 对每个表内的列按条件依赖做拓扑排序
        for (TableVO tableVO : finalTables) {
            List<ColumnInfo> sorted = sortColumnsByConditionDeps(tableVO.getColumns());
            tableVO.setColumns(sorted);
        }

        int allTabSize;
        final int[] finishSize = {0};
        allTabSize = tables.stream().mapToInt(TableVO::getSize).sum();

        javax.sql.DataSource datasource = getDrivierDatasource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(datasource);

        // 创建事务管理器
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new DataSourceTransactionManager(datasource));

        Map<String, List<String>> rowIdMap = new HashMap<>();
        transactionTemplate.executeWithoutResult((status) -> {
            try {
                for (TableVO tableVO : finalTables) {
                    String sql = buildInsertSql(tableVO);
                    List<ColumnInfo> columns = tableVO.getColumns();
                    String curTabName = tableVO.getName();

                    // 树形结构表走专用生成路径
                    if (tableVO.getTreeConfig() != null) {
                        generateTreeData(tableVO, transId, curTabName, sql, columns,
                                allTabSize, finishSize, monitor, executor, jdbcTemplate, rowIdMap);
                        continue;
                    }

                    int tabSize = tableVO.getSize();
                    for (int i = 0; i < tabSize; i += BATCH_SIZE) {

                        int finalI = i;
                        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                            @Override
                            public void setValues(PreparedStatement ps, int curI) throws SQLException {
                                String rowId = null;
                                //该表被依赖,需要为该表创建行ID
                                if (tableVO.getRefOtherTables().size() > 0) {
                                    rowId = UUID.randomUUID().toString();
                                    String lowerTabName = curTabName.toLowerCase();
                                    rowIdMap.putIfAbsent(lowerTabName, new ArrayList<>());
                                    rowIdMap.get(lowerTabName).add(rowId);
                                }

                                // 先按列生成每行的值（支持同表字段引用，如 姓氏 取 name 首字）
                                Map<String, Object> rowValues = new HashMap<>();
                                Set<String> visited = new HashSet<>();
                                for (int k = 0; k < columns.size(); k++) {
                                    genColumnValue(columns.get(k), transId, curTabName, rowId, rowIdMap, curI, columns, rowValues, visited);
                                }

                                // 按列顺序写入 PreparedStatement
                                writeRowToPreparedStatement(ps, columns, rowValues);
                            }

                            @Override
                            public int getBatchSize() {
                                return Math.min(BATCH_SIZE, tabSize - finalI);
                            }
                        });

                        // 记录进度
                        String processStr = monitor.progressAndMessage(executor, ((finishSize[0] + finalI) * 1D / allTabSize) * 100, null);
                        log.info("执行进度: {}% , 已完成: {}条, 共计: {}条 ", processStr, finishSize[0] + finalI, allTabSize);
                    }
                    log.info("{} 表数据生成完毕", tableVO.getName());
                    finishSize[0] += tableVO.getSize();
                }
                status.flush();
            } catch (Exception ex) {
                // 捕获异常并回滚事务
                status.setRollbackOnly();
                throw ex;
            }
        });

    }

    /**
     * 生成单个字段的值。
     * 支持「同表字段引用」：本表某字段的值取自同一行另一个字段（可带转换，如取首字）。
     * 通过 rowValues 缓存本行已生成的值，并对被引用列做惰性递归生成，从而不依赖列定义顺序。
     */
    private Object genColumnValue(ColumnInfo columnInfo, String transId, String curTabName,
                                  String rowId, Map<String, List<String>> rowIdMap, int curI,
                                  List<ColumnInfo> columns, Map<String, Object> rowValues, Set<String> visited) {
        return genColumnValue(columnInfo, transId, curTabName, rowId, rowIdMap, curI,
                columns, rowValues, visited, Collections.emptyMap());
    }

    /**
     * 接受 extraContext 的 genColumnValue 重载。
     * 树形结构生成时通过 extraContext 注入 treeContext / parentValue / treeState 等。
     */
    private Object genColumnValue(ColumnInfo columnInfo, String transId, String curTabName,
                                  String rowId, Map<String, List<String>> rowIdMap, int curI,
                                  List<ColumnInfo> columns, Map<String, Object> rowValues,
                                  Set<String> visited, Map<String, Object> extraContext) {
        visited.add(columnInfo.getName());
        visited.add(columnInfo.getName().toLowerCase());
        List<MetaTypeVO> regs = columnInfo.getRegs();
        if (regs == null || regs.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (MetaTypeVO reg : regs) {
            Map<String, Object> extendArg = new HashMap<>();
            extendArg.put("rowId", rowId);
            extendArg.put("currentReg", reg);
            extendArg.put("rowValues", rowValues);

            // 合并树扩展上下文
            if (extraContext != null) {
                extendArg.putAll(extraContext);
            }

            // 跨表字段引用
            String[] refOtherTableColumn = reg.getRefOtherTableColumn();
            if (refOtherTableColumn != null && refOtherTableColumn.length > 0) {
                String refTable = refOtherTableColumn[0].toLowerCase();
                List<String> refRowIds = rowIdMap.get(refTable);
                String refRowId = refRowIds.get(curI % refRowIds.size());
                extendArg.put("refRowId", refRowId);
            }

            // 同表字段引用（递归时传入 extraContext）
            if (reg.getRefSelfColumn() != null) {
                String refCol = reg.getRefSelfColumn().toLowerCase();
                Object refVal = rowValues.get(refCol);
                if (refVal == null && !visited.contains(refCol)) {
                    for (ColumnInfo ci : columns) {
                        if (ci.getName().toLowerCase().equals(refCol)) {
                            refVal = genColumnValue(ci, transId, curTabName, rowId,
                                    rowIdMap, curI, columns, rowValues, visited, extraContext);
                            break;
                        }
                    }
                }
                extendArg.put("refSelfValue", refVal);
            }

            Object sub = MetaTypeFactory.getMetaTypeImpl(reg).parse(transId, curTabName, columnInfo, extendArg);
            if (sub != null) {
                sb.append(sub);
            }
        }
        Object value = sb.length() == 0 ? null : sb.toString();
        rowValues.put(columnInfo.getName(), value);
        rowValues.put(columnInfo.getName().toLowerCase(), value);

        // 该字段被跨表引用时，将完整拼接结果缓存到 Redis
        if (columnInfo.isDepended()) {
            String key = transId + ":" + curTabName.toLowerCase() + ":" + columnInfo.getName().toLowerCase();
            RedisTemplate redisTemplate = (RedisTemplate) ApplicationContextProvider.getContext().getBean("redisTemplate");
            redisTemplate.opsForHash().put(key, rowId, value);
            redisTemplate.expire(key, 60 * 60 * 18, TimeUnit.SECONDS);
        }

        return value;
    }

    /**
     * 树形结构数据生成。
     * 预计算 BFS 拓扑后按顺序逐行生成，通过 TreeState 按 BFS 索引维护父子关系。
     */
    private void generateTreeData(TableVO tableVO, String transId, String curTabName,
                                  String sql, List<ColumnInfo> columns,
                                  int allTabSize, int[] finishSize,
                                  AsyncTaskMonitor monitor, AsyncTaskExecutor executor,
                                  JdbcTemplate jdbcTemplate,
                                  Map<String, List<String>> rowIdMap) {

        TreeConfig config = tableVO.getTreeConfig();
        String nodeField = config.getNodeField().toLowerCase();

        // 1. 预计算 BFS 拓扑
        TreeTopologyEngine engine = new TreeTopologyEngine();
        List<TreeContext> topology = engine.compute(config);

        // 2. 更新表行数
        tableVO.setSize(topology.size());
        int tabSize = topology.size();

        // 3. 运行时状态
        TreeState treeState = new TreeState();

        // 4. 按 BFS 顺序逐行生成
        for (int i = 0; i < tabSize; i += BATCH_SIZE) {
            int finalI = i;
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int curI) throws SQLException {
                    int idx = finalI + curI;
                    TreeContext treeCtx = topology.get(idx);
                    Map<String, Object> rowValues = new HashMap<>();
                    Set<String> visited = new HashSet<>();

                    // 构建树扩展上下文
                    Map<String, Object> treeExtras = new HashMap<>();
                    treeExtras.put("treeContext", treeCtx);
                    treeExtras.put("treeState", treeState);
                    // 预查父节点值，注入 extendArg 供 STRUCT.TREE_PARENT 使用
                    if (treeCtx.getParentIndex() >= 0) {
                        treeExtras.put("parentValue", treeState.get(treeCtx.getParentIndex()));
                    }

                    // 如果该表被其他表跨表引用，为该树节点行生成行 ID
                    String treeRowId = null;
                    if (tableVO.getRefOtherTables().size() > 0) {
                        treeRowId = UUID.randomUUID().toString();
                        String lowerTabName = curTabName.toLowerCase();
                        rowIdMap.putIfAbsent(lowerTabName, new ArrayList<>());
                        rowIdMap.get(lowerTabName).add(treeRowId);
                    }

                    // 逐列生成
                    for (int k = 0; k < columns.size(); k++) {
                        genColumnValue(columns.get(k), transId, curTabName,
                                treeRowId, rowIdMap, curI, columns, rowValues, visited, treeExtras);
                    }

                    // 捕获当前节点值（父节点引用时使用）
                    Object nodeValue = rowValues.get(nodeField);
                    if (nodeValue == null) {
                        nodeValue = rowValues.get(nodeField.toLowerCase());
                    }
                    if (nodeValue != null) {
                        treeState.put(idx, nodeValue);
                    }

                    // 写入 PreparedStatement
                    writeRowToPreparedStatement(ps, columns, rowValues);
                }

                @Override
                public int getBatchSize() {
                    return Math.min(BATCH_SIZE, tabSize - finalI);
                }
            });

            // 进度上报
            int denominator = allTabSize > 0 ? allTabSize : tabSize;
            String processStr = monitor.progressAndMessage(executor,
                    ((finishSize[0] + finalI) * 1D / denominator) * 100, null);
            log.info("执行进度: {}% , 已完成: {}条, 共计: {}条 ", processStr, finishSize[0] + finalI, denominator);
        }
        log.info("{} 表(树形)数据生成完毕, 共 {} 个节点", tableVO.getName(), topology.size());
        finishSize[0] += tabSize;
    }

    /**
     * 将 rowValues 按列顺序写入 PreparedStatement。两套生成路径共用。
     */
    private void writeRowToPreparedStatement(PreparedStatement ps, List<ColumnInfo> columns,
                                              Map<String, Object> rowValues) throws SQLException {
        for (int k = 0; k < columns.size(); k++) {
            ColumnInfo columnInfo = columns.get(k);
            String javaType = columnInfo.getJavaType();
            String type = javaType.substring(javaType.lastIndexOf(".") + 1);
            Object value = rowValues.get(columnInfo.getName());
            switch (type) {
                case "String":
                    if (value == null && columnInfo.getNullable() != null && columnInfo.getNullable() == 0) {
                        // NOT NULL 列且生成值为 null（如树形根节点的 parent_id），写入空串兜底
                        ps.setString(k + 1, "");
                    } else {
                        ps.setString(k + 1, value == null ? null : value.toString());
                    }
                    break;
                case "Integer":
                    Integer anInt = Convert.toInt(value);
                    if (anInt == null) {
                        ps.setNull(k + 1, Types.INTEGER);
                        break;
                    }
                    ps.setInt(k + 1, anInt);
                    break;
                case "Float":
                    Float aFloat = Convert.toFloat(value);
                    if (aFloat == null) {
                        ps.setNull(k + 1, Types.FLOAT);
                        break;
                    }
                    ps.setFloat(k + 1, aFloat);
                    break;
                case "Double":
                    Double aDouble = Convert.toDouble(value);
                    if (aDouble == null) {
                        ps.setNull(k + 1, Types.DOUBLE);
                        break;
                    }
                    ps.setDouble(k + 1, aDouble);
                    break;
                case "TIMESTAMP":
                case "Timestamp":
                case "Date":
                    Long aLong = Convert.toLong(value);
                    ps.setTimestamp(k + 1, aLong == null ? null : new Timestamp(aLong));
                    break;
                case "Boolean":
                    Boolean bool = Convert.toBool(value);
                    if (bool == null) {
                        ps.setNull(k + 1, Types.BOOLEAN);
                        break;
                    }
                    ps.setBoolean(k + 1, bool);
                    break;
                case "BigDecimal":
                    BigDecimal bigDecimal = Convert.toBigDecimal(value);
                    if (bigDecimal == null) {
                        ps.setNull(k + 1, Types.BOOLEAN);
                        break;
                    }
                    ps.setBigDecimal(k + 1, bigDecimal);
                    break;
                default:
                    log.error("未知类型：{}", type);
                    ps.setNull(k + 1, Types.BIT);
            }
        }
    }

    //拓扑排序，找到合适的顺序给表生成数据
    private List<TableVO> init(List<TableVO> tables) {

        // 构建小写映射，解决不同数据库/JDBC返回大小写不一致的问题
        Map<String, TableVO> tableMap = tables.stream()
                .collect(Collectors.toMap(t -> t.getName().toLowerCase(), t -> t));

        //1.构建图
        for (TableVO tableVO : tables) {
            for (ColumnInfo column : tableVO.getColumns()) {
                List<MetaTypeVO> allRegs = column.getRegs();
                if (allRegs == null || allRegs.isEmpty()) {
                    continue;
                }
                for (MetaTypeVO reg : allRegs) {
                    IMetaType metaTypeImpl = MetaTypeFactory.getMetaTypeImpl(reg);
                    String[] refArr = reg.getRefOtherTableColumn();
                    if (metaTypeImpl instanceof RefOtherTableMetaType && refArr != null && refArr.length == 2) {
                        TableVO refTab = tableMap.get(refArr[0].toLowerCase());
                        if (refTab != null) {
                            refTab.getRefOtherTables().add(tableVO.getName());
                            String refCol = refArr[1].toLowerCase();
                            ColumnInfo columnInfo = refTab.getColumns().stream()
                                    .filter(col -> col.getName().toLowerCase().equals(refCol))
                                    .findFirst().orElse(null);
                            if (columnInfo != null) {
                                //该字段被依赖，因此当存储数据时，在缓存中保存一份
                                columnInfo.setDepended(true);
                            }
                        }
                    }
                }
            }
        }

        //统计入度（统一小写 key）
        Map<String, Integer> cnt = new HashMap<>();
        for (TableVO tableVO : tables) {
            cnt.putIfAbsent(tableVO.getName().toLowerCase(), 0);
            for (String refOtherTable : tableVO.getRefOtherTables()) {
                cnt.merge(refOtherTable.toLowerCase(), 1, Integer::sum);
            }
        }

        Queue<TableVO> queue = new LinkedList<>();
        for (int i = 0; i < tables.size(); i++) {
            TableVO tableVO = tables.get(i);
            if (cnt.get(tableVO.getName().toLowerCase()) == 0) {
                queue.add(tableVO);
            }
        }

        List<TableVO> res = new ArrayList<>();
        while (!queue.isEmpty()) {
            TableVO poll = queue.poll();
            res.add(poll);
            for (String refOtherTable : poll.getRefOtherTables()) {
                String lowerRef = refOtherTable.toLowerCase();
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


    //根据列信息生成插入语句
    private String buildInsertSql(TableVO tableVO) {
        StringBuilder insertSqlSb = new StringBuilder();
        insertSqlSb.append("insert into ").append(tableVO.getName()).append(" (");

        List<ColumnInfo> columns = tableVO.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo columnInfo = columns.get(i);
            insertSqlSb.append(columnInfo.getName());
            if (i != columns.size() - 1) {
                insertSqlSb.append(",");
            }
        }
        insertSqlSb.append(") values (");
        for (int i = 0; i < columns.size(); i++) {
            insertSqlSb.append("?");
            if (i != columns.size() - 1) {
                insertSqlSb.append(",");
            }
        }
        insertSqlSb.append(")");
        return insertSqlSb.toString();
    }

    public javax.sql.DataSource getDrivierDatasource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(loadDriverClass());
        dataSource.setUrl(this.dataSource.getUrl());
        dataSource.setUsername(this.dataSource.getUsername());
        dataSource.setPassword(this.dataSource.getPassword());
        return dataSource;
    }

    /**
     * 对列按条件规则依赖进行拓扑排序。
     * 条件规则中 #字段名 引用的列必须在条件列之前生成。
     * 无依赖的列保持原有相对顺序。
     *
     * @param columns 原始列列表
     * @return 按依赖排序后的列列表
     */
    private List<ColumnInfo> sortColumnsByConditionDeps(List<ColumnInfo> columns) {
        if (columns == null || columns.isEmpty()) {
            return columns == null ? new ArrayList<>() : new ArrayList<>(columns);
        }

        // 1. 构建列名 → 索引映射（统一小写，兼容不同数据库/JDBC 的大小写差异）
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            nameToIdx.put(columns.get(i).getName().toLowerCase(), i);
        }

        // 2. 构建依赖图 & 入度（统一小写 key）
        Map<String, Set<String>> deps = new HashMap<>(); // colName → 依赖该列的列名集合
        Map<String, Integer> inDegree = new HashMap<>();
        List<String> colNames = new ArrayList<>();

        for (ColumnInfo col : columns) {
            String name = col.getName().toLowerCase();
            colNames.add(name);
            deps.putIfAbsent(name, new HashSet<>());
            inDegree.putIfAbsent(name, 0);
        }

        for (ColumnInfo col : columns) {
            Set<String> refs = extractConditionRefs(col);
            String colName = col.getName().toLowerCase();
            for (String ref : refs) {
                String lowerRef = ref.toLowerCase();
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
            String name = columns.get(i).getName().toLowerCase();
            if (inDegree.get(name) == 0) {
                queue.offer(name);
            }
        }

        List<ColumnInfo> sorted = new ArrayList<>();
        Map<String, ColumnInfo> colMap = columns.stream()
                .collect(Collectors.toMap(c -> c.getName().toLowerCase(), c -> c));

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
            sorted.forEach(c -> cycleNames.remove(c.getName()));
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
    private Set<String> extractConditionRefs(ColumnInfo columnInfo) {
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
            // 递归处理嵌套条件
            extractConditionRefsRecursive(reg.getOnTrue(), refs);
            extractConditionRefsRecursive(reg.getOnFalse(), refs);
        }
    }

}