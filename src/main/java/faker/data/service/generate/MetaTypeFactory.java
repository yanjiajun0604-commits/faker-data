package faker.data.service.generate;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MetaTypeFactory implements InitializingBean {

    private static final ConcurrentHashMap<String, IMetaType> metaTypeMap = new ConcurrentHashMap<>();

    @Autowired
    public List<IMetaType> metaTypeList;

    public static IMetaType getMetaTypeImpl(MetaTypeVO metaTypeVO) {
        String code = metaTypeVO.getCode();
        if (code == null) {
            return new EmptyMetaType();
        }
        return metaTypeMap.get(code);
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        registerTaskExecutor();
    }

    private void registerTaskExecutor() {
        synchronized (metaTypeMap) {
            if (null != metaTypeList) {
                for (IMetaType metaType : metaTypeList) {
                    String code = metaType.getCode();
                    metaTypeMap.put(code, metaType);
                }
            }
        }
    }

}
