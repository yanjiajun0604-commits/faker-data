package faker.data.extension;

import faker.data.domain.ColumnInfo;
import faker.data.service.generate.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** 普通字段生成阶段只登记请求，全部普通值就绪后再求复杂字段。 */
@Component
public class FieldExtensionMetaType implements IMetaType {
    public String getCode(){return "EXT.FIELD";}
    public String getTitle(){return "本地字段扩展";}
    public Object parse(String task,String table,ColumnInfo column,Map<String,Object> args) {
        MetaTypeVO rule=(MetaTypeVO)args.get("currentReg");
        if(rule.getExtension()==null || !rule.getExtension().matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("INVALID_FIELD_EXTENSION");
        return new Request(rule.getExtension(),rule.getExtensionParameters());
    }
    public static final class Request {
        public final String extension;
        public final Map<String,Object> parameters;
        Request(String extension,Map<String,Object> parameters){this.extension=extension;this.parameters=parameters==null?Collections.emptyMap():parameters;}
    }
}
