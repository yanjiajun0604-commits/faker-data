package faker.data.domain;

import lombok.Data;

import java.util.List;

@Data
public class GenerateVO {
    private DataSource dataSource;
    private List<TableVO> tables;

}