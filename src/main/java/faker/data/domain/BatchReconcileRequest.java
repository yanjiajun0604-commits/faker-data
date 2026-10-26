package faker.data.domain;
import lombok.Data;
@Data
public class BatchReconcileRequest {
    private DataSource dataSource;
    private boolean confirmCommit;
}
