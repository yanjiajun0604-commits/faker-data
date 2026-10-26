package faker.data.service.generate.engine;

import faker.data.service.generate.MetaTypeVO;

/** 在准备规则时拒绝明显缺失的参数；不通过试生成值进行校验。 */
final class RuleArguments {
    static void check(MetaTypeVO rule) {
        if(rule==null)throw new IllegalArgumentException("Null generation rule");
        String code=rule.getCode();
        if("REG.RANGE_NUM".equals(code) || "REG.RANGE_STRING".equals(code)) {
            Integer min=rule.getMinSize(),max=rule.getMaxSize();
            if(min==null || max==null || min>max || (long)max-min+1>Integer.MAX_VALUE || ("REG.RANGE_STRING".equals(code) && min<0))
                throw new IllegalArgumentException(code+" requires valid minSize/maxSize");
        }
        if("REG.DATE".equals(code)) {
            Long[] range=rule.getDateRange();
            if(range==null || range.length!=2 || range[0]==null || range[1]==null || range[0]>range[1] || range[1]==Long.MAX_VALUE)
                throw new IllegalArgumentException("REG.DATE requires a valid two-value dateRange");
        }
        if("REG.STYLE".equals(code) && (rule.getStyleReg()==null || rule.getStyleReg().trim().isEmpty()))
            throw new IllegalArgumentException("REG.STYLE requires styleReg");
        if("REF.SELF_COLUMN".equals(code) && (rule.getRefSelfColumn()==null || rule.getRefSelfColumn().trim().isEmpty()))
            throw new IllegalArgumentException("REF.SELF_COLUMN requires refSelfColumn");
        if("EXT.FIELD".equals(code) && (rule.getExtension()==null || !rule.getExtension().matches("[A-Za-z0-9_-]{1,80}")))
            throw new IllegalArgumentException("INVALID_FIELD_EXTENSION");
    }
}
