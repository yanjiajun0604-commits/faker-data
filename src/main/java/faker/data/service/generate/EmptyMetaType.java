package faker.data.service.generate;

import faker.data.domain.ColumnInfo;

import java.util.Map;

public class EmptyMetaType implements IMetaType {
    @Override
    public String getCode() {
        return null;
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public Object parse(String transId, String curTableName, ColumnInfo columnInfo, Map<String, Object> extendArg) {
        return null;
    }
}
