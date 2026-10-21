package faker.data.service.generate.ref;

import faker.data.service.generate.MetaTypeVO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 引用本表其他字段（同一行内）。
 * 例如：Student 表的 姓氏 字段 取自 同一行 name 字段的首字。
 */
@Component
public class RefSelfMetaType extends AbstractRefMetaType {

    public static final String code = "REF.SELF_COLUMN";

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getTitle() {
        return "引用本表字段";
    }

    @Override
    protected Object parseValue(String transId, MetaTypeVO reg, Map<String, Object> extendArg) {
        // refSelfValue 由调用方（AbstractJdbcService 或 CondExprMetaType）注入 extendArg
        Object refValue = extendArg.get("refSelfValue");
        if (refValue == null) {
            return null;
        }
        String transform = reg.getRefSelfTransform();
        if ("FIRST_CHAR".equals(transform)) {
            String str = refValue.toString();
            return str.isEmpty() ? "" : str.substring(0, 1);
        }
        // 默认直接取被引用字段的值
        return refValue;
    }
}
