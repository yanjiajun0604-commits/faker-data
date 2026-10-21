package faker.data.service.generate.res;

import faker.data.service.generate.MetaTypeVO;
import faker.data.service.generate.MetaTypeVO.InputItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class AbstractResInputMetaType extends AbstractResMetaType {

    @Override
    public String getCode() {
        return "RES.INPUTAREA";
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    protected Object parseValue(String key, MetaTypeVO reg) {
        List<InputItem> inputs = reg.getInputs();
        if (inputs == null || inputs.isEmpty()) return null;

        int totalWeight = 0;
        List<InputItem> valid = new ArrayList<>();
        for (InputItem item : inputs) {
            if (item.getValue() == null || item.getValue().trim().isEmpty()) continue;
            int w = item.getWeight() != null && item.getWeight() > 0 ? item.getWeight() : 1;
            totalWeight += w;
            valid.add(item);
        }
        if (valid.isEmpty() || totalWeight == 0) return null;

        int rand = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (InputItem item : valid) {
            cumulative += item.getWeight() != null && item.getWeight() > 0 ? item.getWeight() : 1;
            if (rand < cumulative) return item.getValue().trim();
        }
        return null;
    }
}
