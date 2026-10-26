package faker.data.domain;
import lombok.Data;
import java.util.*;
@Data
public class OwnershipConfig {
    private List<String> mutableColumns=new ArrayList<>();
    private List<DerivedRowsConfig> derivedRows=new ArrayList<>();
}
