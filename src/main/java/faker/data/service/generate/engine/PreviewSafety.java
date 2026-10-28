package faker.data.service.generate.engine;

import faker.data.service.generate.*;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.standard.*;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import java.util.*;

/** 预览只执行固定内置规则和受限表达式，正式生成的扩展能力保持原样。 */
final class PreviewSafety {
    private static final Set<String> CLASSES=new HashSet<>(Arrays.asList("EmptyMetaType","CondExprMetaType","fixed.UUIDFixedMetaType","fixed.PeopleNameFixedMetaType","fixed.TelFixedMetaType","fixed.IdentFixedMetaType","reg.IncrementNumRegMetaType","reg.RangeNumRegMetaType","reg.RangeStringRegMetaType","reg.RangeDateRegMetaType","ref.RefOtherTableMetaType","ref.RefSelfMetaType","res.AbstractResInputMetaType","tree.TreeCodeMetaType","tree.TreeCodeRegMetaType","tree.TreeParentMetaType"));
    private static final Set<String> NODES=new HashSet<>(Arrays.asList("IntLiteral","LongLiteral","RealLiteral","FloatLiteral","BooleanLiteral","NullLiteral","StringLiteral","VariableReference","OpEQ","OpNE","OpGT","OpGE","OpLT","OpLE","OpAnd","OpOr","OperatorNot","OpPlus","OpMinus","OpMultiply","OpDivide","OpModulus","Ternary","Elvis","FunctionReference"));
    private static final Set<String> FUNCTIONS=new HashSet<>(Arrays.asList("length","contains","startsWith","endsWith","isEmpty","isNotEmpty","between","toString"));
    static void rule(MetaTypeVO rule,IMetaType implementation){
        String name=implementation.getClass().getName();boolean extension=name.equals("faker.data.extension.FieldExtensionMetaType");
        if(!extension && (!name.startsWith("faker.data.service.generate.") || !CLASSES.contains(name.substring("faker.data.service.generate.".length()))))throw new IllegalArgumentException("PREVIEW_RULE_UNSUPPORTED");
        if("REG.RANGE_STRING".equals(rule.getCode()) && rule.getMaxSize()!=null && rule.getMaxSize()>1024)throw new IllegalArgumentException("PREVIEW_STRING_LIMIT");
        if(rule.getInputs()!=null)for(MetaTypeVO.InputItem input:rule.getInputs())if(input!=null && input.getValue()!=null && input.getValue().length()>4096)throw new IllegalArgumentException("PREVIEW_STRING_LIMIT");
    }
    static Expression expression(String source){
        if(source==null || source.trim().isEmpty() || source.length()>2048)throw new IllegalArgumentException("PREVIEW_EXPRESSION_UNSUPPORTED");
        String prepared=source;for(String name:FUNCTIONS)prepared=prepared.replaceAll("(?<!#)\\b"+name+"\\(","#"+name+"(");
        SpelExpression expression=(SpelExpression)new SpelExpressionParser().parseExpression(prepared);check(expression.getAST(),0,new int[]{0});return expression;
    }
    private static void check(SpelNode node,int depth,int[] count){
        if(depth>20 || ++count[0]>200 || !NODES.contains(node.getClass().getSimpleName()))throw new IllegalArgumentException("PREVIEW_EXPRESSION_UNSUPPORTED");
        if(node.getClass().getSimpleName().equals("FunctionReference")){String text=node.toStringAST();int paren=text.indexOf('(');if(paren<2 || !FUNCTIONS.contains(text.substring(1,paren)))throw new IllegalArgumentException("PREVIEW_EXPRESSION_UNSUPPORTED");}
        for(int index=0;index<node.getChildCount();index++)check(node.getChild(index),depth+1,count);
    }
    static boolean evaluate(Expression expression,Map<String,Object> values){
        StandardEvaluationContext context=new StandardEvaluationContext();context.setTypeLocator(name->{throw new org.springframework.expression.EvaluationException("PREVIEW_EXPRESSION_UNSUPPORTED");});context.setConstructorResolvers(Collections.emptyList());context.setMethodResolvers(Collections.emptyList());context.setPropertyAccessors(Collections.emptyList());
        values.forEach((name,value)->{if(value==null || value instanceof String || value instanceof Number || value instanceof Boolean){context.setVariable(name,value);context.setVariable(name.toLowerCase(Locale.ROOT),value);}});
        new SpelBuiltinFunctionProvider().register(context);return Boolean.TRUE.equals(expression.getValue(context,Boolean.class));
    }
    static boolean requiresTrustedCode(MetaTypeVO rule){
        IMetaType implementation=MetaTypeFactory.getMetaTypeImpl(rule);if(implementation==null)return true;String name=implementation.getClass().getName();
        if("EXT.FIELD".equals(rule.getCode()))return true;
        if(!name.equals("faker.data.service.generate.reg.StyleRegMetaType") && (!name.startsWith("faker.data.service.generate.") || !CLASSES.contains(name.substring("faker.data.service.generate.".length()))))return true;
        if("COND.EXPR".equals(rule.getCode()))try{expression(rule.getCondition());}catch(RuntimeException unsupported){return true;}
        return false;
    }
}
