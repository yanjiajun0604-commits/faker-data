package faker.data.service.generate.fixed;

import com.github.houbb.data.factory.core.api.regex.DefaultRegexGen;
import faker.data.domain.ColumnInfo;
import faker.data.service.generate.IMetaType;

import java.util.Map;

public abstract class AbstractFixedMetaType implements IMetaType {

    public static DefaultRegexGen defaultRegexGen = new DefaultRegexGen();

    @Override
    public Object parse(String transId, String curTableName, ColumnInfo columnInfo, Map<String, Object> extendArg) {
        return parseValue();
    }

    protected abstract Object parseValue();


}
