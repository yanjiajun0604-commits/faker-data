package faker.data.service.generate.engine;

import faker.data.domain.ColumnInfo;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** 只负责参数绑定与批写入；事务边界由生成引擎统一管理。 */
public final class JdbcBatchWriter {
    public interface Observer {void row(Object[] values);default void beforeWrite(Connection connection) throws SQLException{}void written(Connection connection) throws SQLException;}
    public static final int BATCH_SIZE = 1000;
    private final String sql;
    private final JdbcValueBinder[] binders;

    public JdbcBatchWriter(String table, List<ColumnInfo> columns) {
        binders = new JdbcValueBinder[columns.size()];
        StringBuilder names = new StringBuilder(), parameters = new StringBuilder();
        for (int i=0;i<columns.size();i++) {
            if(i>0){names.append(',');parameters.append(',');}
            names.append(columns.get(i).getName());parameters.append('?');
            binders[i]=JdbcValueBinder.compile(columns.get(i),i+1);
        }
        sql="insert into "+table+" ("+names+") values ("+parameters+")";
    }

    public void write(JdbcTemplate jdbc, int rows, IntFunction<Object[]> source, IntConsumer progress) {
        write(jdbc,rows,source,progress,null);
    }

    public void write(JdbcTemplate jdbc, int rows, IntFunction<Object[]> source, IntConsumer progress,Observer observer) {
        if(rows<=0)return;
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            boolean batches=JdbcUtils.supportsBatchUpdates(connection);
            // 一张表复用一个 statement，最多仅保留一个批次的驱动参数。
            try(PreparedStatement ps=connection.prepareStatement(sql)) {
                for(int offset=0;offset<rows;) {
                    int end=offset+Math.min(BATCH_SIZE,rows-offset);
                    for(int row=offset;row<end;row++) {
                        Object[] values=source.apply(row);
                        if(observer!=null)observer.row(values);
                        for(int col=0;col<binders.length;col++)binders[col].bind(ps,values[col]);
                        if(batches)ps.addBatch();else {if(observer!=null)observer.beforeWrite(connection);if(ps.executeUpdate()!=1)throw new SQLException("BATCH_INSERT_COUNT_UNEXPECTED");if(observer!=null)observer.written(connection);}
                    }
                    if(batches){
                        if(observer!=null)observer.beforeWrite(connection);
                        int[] counts=ps.executeBatch();
                        if(counts.length!=end-offset)throw new SQLException("BATCH_INSERT_COUNT_UNEXPECTED");
                        for(int affected:counts)if(affected!=1 && affected!=Statement.SUCCESS_NO_INFO)throw new SQLException("BATCH_INSERT_COUNT_UNEXPECTED");
                        ps.clearBatch();
                    }
                    if(batches && observer!=null)observer.written(connection);
                    progress.accept(end);
                    offset=end;
                }
            }
            return null;
        });
    }
}
