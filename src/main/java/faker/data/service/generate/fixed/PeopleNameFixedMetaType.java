package faker.data.service.generate.fixed;

import org.springframework.stereotype.Component;

@Component
public class PeopleNameFixedMetaType extends AbstractFixedMetaType {

    private static final String code = "FIX.PEOPLE_NAME";

    @Override
    protected Object parseValue() {
        return defaultRegexGen.generate("[张李乔吴程肖周武袁冯王刘闫郑白][春秋雪东飞宇洁嘉泊哲建][霖丽浩成俊帅豪]");
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "人名";
    }
}
