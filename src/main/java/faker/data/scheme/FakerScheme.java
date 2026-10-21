package faker.data.scheme;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * 测试数据生成方案：保存数据源 + 已选表（含字段规则）以便复用。
 */
@Data
@Entity
@Table(name = "faker_scheme")
public class FakerScheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 序列化后的 DataSource JSON */
    @Lob
    @Column(name = "data_source_json", columnDefinition = "TEXT")
    private String dataSourceJson;

    /** 序列化后的 List<TableVO> JSON（含列的生成规则） */
    @Lob
    @Column(name = "tables_json", columnDefinition = "TEXT")
    private String tablesJson;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt;
}
