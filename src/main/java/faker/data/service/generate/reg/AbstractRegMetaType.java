package faker.data.service.generate.reg;

import com.github.houbb.data.factory.core.api.regex.DefaultRegexGen;
import faker.data.domain.ColumnInfo;
import faker.data.service.generate.IMetaType;
import faker.data.service.generate.MetaTypeVO;

import java.util.Map;

public abstract class AbstractRegMetaType implements IMetaType {

    public static DefaultRegexGen defaultRegexGen = new DefaultRegexGen();

    @Override
    public Object parse(String transId, String curTableName, ColumnInfo columnInfo, Map<String, Object> extendArg) {
        MetaTypeVO reg = (MetaTypeVO) extendArg.get("currentReg");
        return doParse(reg, transId, curTableName, columnInfo);
    }

    /**
     * 解析规则值。子类可通过此方法获取完整的上下文信息。
     * 默认委托给 {@link #parseValue(MetaTypeVO)}，仅需 reg 信息的子类直接重写 parseValue 即可。
     */
    protected Object doParse(MetaTypeVO reg, String transId, String curTableName, ColumnInfo columnInfo) {
        return parseValue(reg);
    }

    /**
     * 仅需 reg 参数时重写此方法。
     */
    protected abstract Object parseValue(MetaTypeVO reg);


}
