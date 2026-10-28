package faker.data.service.db;

import faker.data.domain.ColumnInfo;
import faker.data.domain.DatabaseInspectionRequest;
import faker.data.domain.TableVO;
import faker.data.service.generate.executor.GenerateDataExecutor;
import faker.data.task.AsyncTaskExecutor;
import faker.data.task.AsyncTaskMonitor;

import java.util.List;
import java.util.Map;

public interface JdbcService {
    /**
     * 测试数据库的连通性
     *
     * @return true表示成功
     */
    boolean test();

    /**
     * 列出所有的表和视图
     *
     * @return 所有的表和视图
     */
    List<Map<String, Object>> listAllTables(Integer withSub);

    /**
     * 列出所有的字段
     *
     * @return 所有的字段
     */
    List<ColumnInfo> listAllFields(String tableName);

    Map<String,Object> inspectDatabase(DatabaseInspectionRequest request);

    Map<String,Object> readRows(DatabaseInspectionRequest request);

    Map<String,Object> preflightGeneration(List<TableVO> tables);

    javax.sql.DataSource getDrivierDatasource();

    void generateData(List<TableVO> tables, AsyncTaskMonitor monitor, AsyncTaskExecutor executor);
}
