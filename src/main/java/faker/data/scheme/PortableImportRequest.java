package faker.data.scheme;

import com.fasterxml.jackson.databind.JsonNode;
import faker.data.domain.DataSource;
import lombok.Data;
import java.util.*;

@Data
public class PortableImportRequest {
    private JsonNode document;
    private DataSource dataSource;
    private String name;
    private Map<String,JsonNode> replacements=new LinkedHashMap<>();
    private boolean replaceExisting;
    private boolean confirmTrustedCode;
}
