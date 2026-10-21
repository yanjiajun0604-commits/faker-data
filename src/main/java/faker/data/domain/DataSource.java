package faker.data.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class DataSource implements Serializable {
    private JdbcType type;
    private String url;
    private String username;
    private String password;

}