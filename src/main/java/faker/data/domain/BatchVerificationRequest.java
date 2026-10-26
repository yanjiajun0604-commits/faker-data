package faker.data.domain;

import lombok.Data;
import java.util.*;

@Data
public class BatchVerificationRequest {
    private DataSource dataSource;
    private List<Map<String,Object>> checks=new ArrayList<>();
}
