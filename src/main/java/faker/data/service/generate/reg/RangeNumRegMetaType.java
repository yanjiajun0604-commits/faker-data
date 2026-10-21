package faker.data.service.generate.reg;

import faker.data.service.generate.MetaTypeVO;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class RangeNumRegMetaType extends AbstractRegMetaType {

    public static final Random random = new Random();


    public static final String code = "REG.RANGE_NUM";

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "获取固定范围大小的数字";
    }

    @Override
    protected Object parseValue(MetaTypeVO reg) {
        return random.nextInt(reg.getMaxSize() - reg.getMinSize() + 1) + reg.getMinSize();
    }
}
