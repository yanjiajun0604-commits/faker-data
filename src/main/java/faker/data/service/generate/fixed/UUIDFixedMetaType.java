package faker.data.service.generate.fixed;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UUIDFixedMetaType extends AbstractFixedMetaType {

    private static final String code = "FIX.UUID";

    @Override
    protected Object parseValue() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "uuid";
    }
}
