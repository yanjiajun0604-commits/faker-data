package faker.data.domain;
import lombok.Data;
import java.util.*;
@Data
public class DerivedRowsConfig {
    private String table;
    /** 子表字段 -> 完整父主键字段，全部条件为等值 AND。 */
    private Map<String,String> join=new LinkedHashMap<>();
    private Map<String,Object> where=new LinkedHashMap<>();
    private String freshness="AUTO";
    private Integer baselineLimit=10000;
    private Integer maxRows=10000;
    private List<DerivedRowsConfig> derivedRows=new ArrayList<>();
}
