package faker.data.domain;

import lombok.Data;
import java.util.*;

/** 只读候选数据集：等值过滤、稳定排序和容量上限。 */
@Data
public class ExistingDataConfig {
    private Map<String,Object> where = new LinkedHashMap<>();
    private List<String> orderBy = new ArrayList<>();
    private Integer maxRows = 10000;
}
