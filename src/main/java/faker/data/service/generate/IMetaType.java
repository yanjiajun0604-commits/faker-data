package faker.data.service.generate;

import faker.data.domain.ColumnInfo;

import java.util.Map;

public interface IMetaType {

    /**
     * 获取标识
     * @return 标识
     */
    String getCode();

    /**
     * 获取标题
     * @return 标题
     */
    String getTitle();

    /**
     * 解析
     *
     * @param transId      事务ID
     * @param curTableName 当前table
     * @param columnInfo   当前列信息
     * @param extendArg    扩展参数
     * @return 生成的随机数据结果
     */
    Object parse(String transId, String curTableName, ColumnInfo columnInfo, Map<String, Object> extendArg);


}
