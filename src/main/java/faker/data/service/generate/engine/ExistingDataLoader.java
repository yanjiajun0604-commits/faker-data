package faker.data.service.generate.engine;

import faker.data.cache.ReferenceCache;
import faker.data.domain.*;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

/** 每个数据集在写入前读取一次；标识符受限，过滤值全部参数绑定。 */
final class ExistingDataLoader {
    private final TableVO table;
    private final String sql;
    private final List<Object> parameters = new ArrayList<>();
    private final int maxRows;

    ExistingDataLoader(TableVO table) {
        this.table=table;
        ExistingDataConfig config=table.getExistingData();
        if(table.getTreeConfig()!=null)throw new IllegalArgumentException("existingData cannot use treeConfig");
        Integer limit=config.getMaxRows();
        if(limit==null || limit<1 || limit>100000)throw new IllegalArgumentException("existingData.maxRows must be between 1 and 100000");
        maxRows=limit;
        identifier(table.getName(),true);
        List<String> names=new ArrayList<>();
        for(ColumnInfo column:table.getColumns()) {
            identifier(column.getName(),false);
            if(column.getRegs()!=null && !column.getRegs().isEmpty())throw new IllegalArgumentException("existingData columns cannot contain generation rules");
            names.add(column.getName());
        }
        if(names.isEmpty())throw new IllegalArgumentException("existingData requires explicit columns");
        StringBuilder statement=new StringBuilder("SELECT ").append(String.join(",",names)).append(" FROM ").append(table.getName());
        if(config.getWhere()!=null && !config.getWhere().isEmpty()) {
            List<String> predicates=new ArrayList<>();
            config.getWhere().forEach((name,value)->{
                identifier(name,false);
                if(value!=null && !(value instanceof String || value instanceof Number || value instanceof Boolean))
                    throw new IllegalArgumentException("existingData.where accepts only scalar values or null");
                predicates.add(name+(value==null?" IS NULL":" = ?"));
                if(value!=null)parameters.add(value);
            });
            statement.append(" WHERE ").append(String.join(" AND ",predicates));
        }
        List<String> order=config.getOrderBy();
        if(order==null || order.isEmpty())order=names;
        for(String name:order)identifier(name,false);
        statement.append(" ORDER BY ").append(String.join(",",order));
        sql=statement.toString();
    }

    private void identifier(String name,boolean qualified) {
        String part="[\\p{L}_][\\p{L}\\p{N}_]*";
        if(name==null || !name.matches(qualified?part+"(?:\\."+part+")*":part))
            throw new IllegalArgumentException("Invalid existingData SQL identifier");
    }

    int load(JdbcTemplate jdbc,String task,ReferenceCache cache,Map<String,List<String>> rowIds) {
        final String name=table.getName().toLowerCase(Locale.ROOT);
        List<String> ids=new ArrayList<>();
        rowIds.put(name,ids);
        jdbc.execute((ConnectionCallback<Void>)connection->{
            try(PreparedStatement statement=connection.prepareStatement(sql)) {
                statement.setMaxRows(maxRows+1);
                statement.setQueryTimeout(30);
                statement.setFetchSize(Math.min(1000,maxRows+1));
                for(int index=0;index<parameters.size();index++)statement.setObject(index+1,parameters.get(index));
                try(ResultSet rows=statement.executeQuery()) {
                    ResultSetMetaData metadata=rows.getMetaData();
                    for(int c=1;c<=metadata.getColumnCount();c++) {
                        int type=metadata.getColumnType(c);
                        if(type==Types.BLOB || type==Types.BINARY || type==Types.VARBINARY || type==Types.LONGVARBINARY || type==Types.CLOB || type==Types.NCLOB || type==Types.ARRAY || type==Types.STRUCT || type==Types.SQLXML)
                            throw new IllegalArgumentException("Unsupported existingData column type: "+table.getColumns().get(c-1).getName());
                    }
                    while(rows.next()) {
                        if(ids.size()==maxRows)throw new IllegalArgumentException("existingData exceeds maxRows: "+table.getName());
                        String id=Integer.toString(ids.size());ids.add(id);
                        for(int c=0;c<table.getColumns().size();c++) {
                            ColumnInfo column=table.getColumns().get(c);
                            if(column.isDepended())cache.put(task,name+":"+column.getName().toLowerCase(Locale.ROOT),id,text(rows.getObject(c+1)));
                        }
                    }
                }
            }
            return null;
        });
        if(ids.isEmpty())throw new IllegalArgumentException("existingData has no matching rows: "+table.getName());
        return ids.size();
    }

    private Object text(Object value) {
        if(value==null)return null;
        if(value instanceof java.util.Date)return Long.toString(((java.util.Date)value).getTime());
        if(value instanceof BigDecimal)return ((BigDecimal)value).toPlainString();
        if(value instanceof String || value instanceof Number || value instanceof Boolean)return value.toString();
        throw new IllegalArgumentException("Unsupported existingData value type");
    }
}
