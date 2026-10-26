package faker.data.service.generate.engine;

import java.util.*;

/** 错误位置不携带字段值、连接或规则参数。 */
public final class GenerationProblem extends IllegalArgumentException {
    private final Map<String,Object> diagnostic;
    private GenerationProblem(Throwable cause,String table,String column,String path,String rule,Integer row,String fallback){
        super(cause.getMessage()==null?fallback:cause.getMessage(),cause);String message=cause.getMessage()==null?"":cause.getMessage();
        java.util.regex.Matcher known=java.util.regex.Pattern.compile("^([A-Z][A-Z_]{2,79})(?::|$)").matcher(message);String code=known.find()?known.group(1):fallback;
        diagnostic=new LinkedHashMap<>();diagnostic.put("code",code);diagnostic.put("table",table);diagnostic.put("column",column);diagnostic.put("rulePath",path);diagnostic.put("ruleCode",rule);if(row!=null)diagnostic.put("rowIndex",row);
        diagnostic.put("hint",code.equals("REF_MATCH_NONE")?"Check the source candidates and parent/role markers for this field":code.equals("REF_MATCH_MULTIPLE")?"Make the declared source match unambiguous":code.startsWith("PREVIEW_")?"Use the supported bounded preview subset; formal generation is separate":"Check this field's rule arguments and dependencies");
    }
    static GenerationProblem at(Throwable cause,String table,String column,String path,String rule,Integer row,String fallback){return cause instanceof GenerationProblem?(GenerationProblem)cause:new GenerationProblem(cause,table,column,path,rule,row,fallback);}
    public Map<String,Object> diagnostic(){return new LinkedHashMap<>(diagnostic);}
    public static Map<String,Object> find(Throwable cause){Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());for(int depth=0;cause!=null && depth<20 && seen.add(cause);depth++,cause=cause.getCause())if(cause instanceof GenerationProblem)return ((GenerationProblem)cause).diagnostic();return null;}
}
