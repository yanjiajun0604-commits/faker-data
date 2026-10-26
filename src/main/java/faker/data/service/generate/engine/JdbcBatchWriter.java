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
    private final int[] sourcePositions;
    private final String tableName;
    private final String[] fieldNames;

    public JdbcBatchWriter(String table, List<ColumnInfo> columns) {
        tableName=table;
        int count=(int)columns.stream().filter(c->!c.isVirtual()).count();
        if(count==0)throw new IllegalArgumentException("PHYSICAL_COLUMN_REQUIRED: "+table);
        binders = new JdbcValueBinder[count];sourcePositions=new int[count];fieldNames=new String[count];
        StringBuilder names = new StringBuilder(), parameters = new StringBuilder();
        int persisted=0;
        for (int i=0;i<columns.size();i++) {
            if(columns.get(i).isVirtual()){compile(columns.get(i),1);continue;}
            if(persisted>0){names.append(',');parameters.append(',');}
            names.append(columns.get(i).getName());parameters.append('?');
            sourcePositions[persisted]=i;fieldNames[persisted]=columns.get(i).getName();binders[persisted]=compile(columns.get(i),persisted+1);persisted++;
        }
        sql="insert into "+table+" ("+names+") values ("+parameters+")";
    }
    private JdbcValueBinder compile(ColumnInfo column,int position){try{return JdbcValueBinder.compile(column,position);}catch(RuntimeException error){throw GenerationProblem.at(error,tableName,column.getName(),"javaType",null,null,"UNSUPPORTED_JDBC_TYPE");}}

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
                        for(int col=0;col<binders.length;col++)try{binders[col].bind(ps,values[sourcePositions[col]]);}catch(SQLException|RuntimeException error){throw GenerationProblem.at(error,tableName,fieldNames[col],"JDBC_BIND",null,row,"JDBC_VALUE_BIND_FAILED");}
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
