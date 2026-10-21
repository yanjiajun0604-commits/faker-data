package faker.data.service.generate.fixed;

import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class TelFixedMetaType extends AbstractFixedMetaType {

    private static final String code = "FIX.TEL";

    // 定义手机号码的前三位
    public static final String[] prefixArray = {"130", "131", "132", "133", "134", "135", "136", "137", "138", "139",
            "150", "151", "152", "153", "155", "156", "157", "158", "159",
            "176", "177", "178",
            "180", "181", "182", "183", "184", "185", "186", "187", "188", "189"};

    public static final Random random = new Random();

    @Override
    protected Object parseValue() {
        return generateRandomPhoneNumber();
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "手机号";
    }

    public static String generateRandomPhoneNumber() {
        // 随机生成前三位
        int index = random.nextInt(prefixArray.length);
        String prefix = prefixArray[index];

        // 随机生成后八位
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int digit = random.nextInt(10);
            suffix.append(digit);
        }
        return prefix + suffix.toString();
    }
}
