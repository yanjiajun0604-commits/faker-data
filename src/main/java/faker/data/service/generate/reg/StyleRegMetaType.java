package faker.data.service.generate.reg;

import faker.data.service.generate.MetaTypeVO;
import org.springframework.stereotype.Component;


@Component
public class StyleRegMetaType extends AbstractRegMetaType {

    public static final String code = "REG.STYLE";

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "获取自定义正则表达式生成数据";
    }

    @Override
    protected Object parseValue(MetaTypeVO reg) {
        return defaultRegexGen.generate(reg.getStyleReg());
    }
}
