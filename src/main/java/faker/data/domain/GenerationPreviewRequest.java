package faker.data.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class GenerationPreviewRequest extends GenerateVO {
    private Integer rowsPerTable=5;
}
