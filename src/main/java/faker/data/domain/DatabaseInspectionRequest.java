package faker.data.domain;

import lombok.Data;
import java.util.List;

/** 只接受结构化读取参数，不接收 SQL 或表达式。 */
@Data
public class DatabaseInspectionRequest {
    private DataSource dataSource;
    private String table;
    private String nameContains;
    private Integer limit;
    private Integer offset;
    private List<String> columns;
    private List<Filter> where;
    private List<Order> orderBy;

    @Data public static class Filter {
        private String column;
        private String op="eq";
        private Object value;
    }
    @Data public static class Order {
        private String column;
        private String direction="ASC";
    }
}
