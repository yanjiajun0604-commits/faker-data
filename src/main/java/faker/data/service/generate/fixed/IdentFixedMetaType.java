package faker.data.service.generate.fixed;

import org.springframework.stereotype.Component;

@Component
public class IdentFixedMetaType extends AbstractFixedMetaType {

    private static final String code = "FIX.IDENTIF";

    private static final String reg = "([1-6][1-9]|50)\\d{4}(18|19|20)\\d{2}((0[1-9])|10|11|12)(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]";


    @Override
    protected Object parseValue() {
        return defaultRegexGen.generate(reg);
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "身份证号";
    }
}
