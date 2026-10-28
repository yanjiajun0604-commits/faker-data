package faker.data.domain;
import lombok.Data;
@Data
public class BatchCleanupRequest {
    private DataSource dataSource;
    private String planId;
    private String planHash;
    private boolean confirmDelete;
}
