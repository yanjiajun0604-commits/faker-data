package faker.data.service.generate.ref;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.IMetaType;
import faker.data.service.generate.MetaTypeVO;

import java.util.Map;

public abstract class AbstractRefMetaType implements IMetaType {

    @Override
    public Object parse(String transId, String curTableName, ColumnInfo columnInfo, Map<String, Object> extendArg) {
        MetaTypeVO reg = (MetaTypeVO) extendArg.get("currentReg");
        return parseValue(transId, reg, extendArg);
    }

    protected abstract Object parseValue(String transId, MetaTypeVO reg, Map<String, Object> extendArg);

}
