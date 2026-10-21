package faker.data.service.generate.reg;

import faker.data.service.generate.MetaTypeVO;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class RangeStringRegMetaType extends AbstractRegMetaType {

    public static final Random random = new Random();

    public static final String str = "0123456789abcdefghigklmnopqrstuvwkyzABCDEFGHIGKLMNOPQRSTUVWKYZ";


    public static final String code = "REG.RANGE_STRING";

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "获取固定范围长度的字符串";
    }

    @Override
    protected Object parseValue(MetaTypeVO reg) {
        int len = random.nextInt(reg.getMaxSize() - reg.getMinSize() + 1) + reg.getMinSize();
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < len; i++) {
            res.append(str.charAt(random.nextInt(str.length())));
        }
        return res.toString();
    }
}
