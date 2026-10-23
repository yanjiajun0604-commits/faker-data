package faker.data.scheme;
import lombok.Data;
import java.util.Date;
@Data
public class FakerScheme {
    private Long id;
    private String name;
    private String dataSourceJson;
    private String tablesJson;
    private Date createdAt;
    private Date updatedAt;
}
