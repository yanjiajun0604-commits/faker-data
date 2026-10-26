package faker.data.service.generate.engine;

import cn.hutool.core.convert.Convert;
import faker.data.domain.ColumnInfo;
import java.math.BigDecimal;
import java.sql.*;

/** 按列预选 JDBC 绑定方式，不在每一行重新解析 Java 类型名称。 */
public final class JdbcValueBinder {
    private interface Setter { void set(PreparedStatement statement, Object value) throws SQLException; }
    private final Setter setter;
    private JdbcValueBinder(Setter setter) { this.setter = setter; }
    public void bind(PreparedStatement statement, Object value) throws SQLException { setter.set(statement, value); }

    public static JdbcValueBinder compile(ColumnInfo column, int position) {
        String javaType = column.getJavaType();
        String type = javaType.substring(javaType.lastIndexOf('.') + 1);
        final boolean required = Integer.valueOf(0).equals(column.getNullable());
        switch (type) {
            case "[B": case "byte[]": case "Blob":
                return new JdbcValueBinder((ps,v) -> {
                    if(v==null)ps.setNull(position,Types.BLOB);
                    else if(v instanceof byte[])ps.setBytes(position,(byte[])v);
                    else throw new IllegalArgumentException("Binary columns require byte[] values");
                });
            case "Long": return new JdbcValueBinder((ps,v) -> {
                Long n=Convert.toLong(v); if(n==null)ps.setNull(position,Types.BIGINT);else ps.setLong(position,n);
            });
            case "BigInteger": return new JdbcValueBinder((ps,v) -> {
                java.math.BigInteger n=Convert.toBigInteger(v); if(n==null)ps.setNull(position,Types.NUMERIC);else ps.setBigDecimal(position,new BigDecimal(n));
            });
            case "Short": return new JdbcValueBinder((ps,v) -> {
                Short n=Convert.toShort(v); if(n==null)ps.setNull(position,Types.SMALLINT);else ps.setShort(position,n);
            });
            case "Byte": return new JdbcValueBinder((ps,v) -> {
                Byte n=Convert.toByte(v); if(n==null)ps.setNull(position,Types.TINYINT);else ps.setByte(position,n);
            });
            case "String": return new JdbcValueBinder((ps,v) -> ps.setString(position,v==null?(required?"":null):v.toString()));
            case "Integer": return new JdbcValueBinder((ps,v) -> {
                Integer n=Convert.toInt(v); if(n==null)ps.setNull(position,Types.INTEGER);else ps.setInt(position,n);
            });
            case "Float": return new JdbcValueBinder((ps,v) -> {
                Float n=Convert.toFloat(v); if(n==null)ps.setNull(position,Types.FLOAT);else ps.setFloat(position,n);
            });
            case "Double": return new JdbcValueBinder((ps,v) -> {
                Double n=Convert.toDouble(v); if(n==null)ps.setNull(position,Types.DOUBLE);else ps.setDouble(position,n);
            });
            case "TIMESTAMP": case "Timestamp": case "LocalDateTime": case "Date":
                return new JdbcValueBinder((ps,v) -> { Long n=Convert.toLong(v);ps.setTimestamp(position,n==null?null:new Timestamp(n)); });
            case "LocalDate": return new JdbcValueBinder((ps,v) -> { Long n=Convert.toLong(v);ps.setDate(position,n==null?null:new java.sql.Date(n)); });
            case "Time": case "LocalTime": return new JdbcValueBinder((ps,v) -> { Long n=Convert.toLong(v);ps.setTime(position,n==null?null:new Time(n)); });
            case "Boolean": return new JdbcValueBinder((ps,v) -> {
                Boolean n=Convert.toBool(v); if(n==null)ps.setNull(position,Types.BOOLEAN);else ps.setBoolean(position,n);
            });
            case "BigDecimal": return new JdbcValueBinder((ps,v) -> {
                BigDecimal n=Convert.toBigDecimal(v); if(n==null)ps.setNull(position,Types.DECIMAL);else ps.setBigDecimal(position,n);
            });
            default: throw new IllegalArgumentException("尚不支持字段 " + column.getName() + " 的 JDBC Java 类型：" + javaType);
        }
    }
}
