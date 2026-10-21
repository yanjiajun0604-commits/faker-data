package faker.data.service.generate.res;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.IMetaType;
import faker.data.service.generate.MetaTypeVO;

import java.util.Map;

public abstract class AbstractResMetaType implements IMetaType {

    @Override
    public Object parse(String transId, String curTableName, ColumnInfo columnInfo, Map<String, Object> extendArg) {
        String key = transId + ":" + curTableName + ":" + columnInfo.getName();
        MetaTypeVO reg = (MetaTypeVO) extendArg.get("currentReg");
        return parseValue(key, reg);
    }

    protected abstract Object parseValue(String key, MetaTypeVO reg);
}
