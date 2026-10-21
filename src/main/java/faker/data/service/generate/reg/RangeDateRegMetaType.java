package faker.data.service.generate.reg;

import faker.data.service.generate.MetaTypeVO;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class RangeDateRegMetaType extends AbstractRegMetaType {

    public static final Random random = new Random();


    public static final String code = "REG.DATE";

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "获取范围时间";
    }

    @Override
    protected Object parseValue(MetaTypeVO reg) {
        Long[] dateRange = reg.getDateRange();
        Long start = dateRange[0];
        Long end = dateRange[1];
        return random.longs(start, end + 1).findFirst().getAsLong();
    }
}
