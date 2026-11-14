package faker.data.service.generate.fixed;

import org.springframework.stereotype.Component;

@Component
public class PeopleNameFixedMetaType extends AbstractFixedMetaType {

    private static final String code = "FIX.PEOPLE_NAME";

    @Override
    protected Object parseValue() {
        java.util.Random random=faker.data.service.generate.GenerationRandom.current();
        if("en".equals(faker.data.service.generate.GenerationRandom.locale())) {
            String[] first={"Alex","Jamie","Taylor","Morgan","Casey","Jordan","Riley","Sam","Robin","Avery","Cameron","Quinn"};
            String[] last={"Smith","Jones","Brown","Wilson","Taylor","Clark","Walker","Hall","Young","King","Green","Baker"};
            return first[random.nextInt(first.length)]+" "+last[random.nextInt(last.length)];
        }
        if(faker.data.service.generate.GenerationRandom.seeded()) {
            String[] parts={"张李乔吴程肖周武袁冯王刘闫郑白","春秋雪东飞宇洁嘉泊哲建","霖丽浩成俊帅豪"};StringBuilder name=new StringBuilder();
            for(String part:parts)name.append(part.charAt(random.nextInt(part.length())));return name.toString();
        }
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
