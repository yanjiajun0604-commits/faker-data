package faker.data.scheme;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.*;
import java.util.*;
import java.math.BigDecimal;

/** 仅解释本项目固定形状契约的受限关键字，不加载外部 schema 或执行表达式。 */
final class PortableShape {
    private final JsonNode root;
    private static final JsonNode ANY=JsonNodeFactory.instance.objectNode();
    private static final Set<String> KEYWORDS=new HashSet<>(Arrays.asList("$schema","$id","title","description","default","$comment","examples","definitions","$ref","type","const","enum","properties","required","additionalProperties","items","minLength","maxLength","pattern","minimum","maximum","minItems","maxItems"));
    PortableShape(ObjectMapper json){try(InputStream input=getClass().getResourceAsStream("/contracts/scheme-v1.schema.json")){if(input==null)throw new IllegalStateException("SCHEME_CONTRACT_RESOURCE_MISSING");root=json.readTree(input);schema(root);}catch(IOException error){throw new IllegalStateException("SCHEME_CONTRACT_RESOURCE_INVALID",error);}}
    private void schema(JsonNode node){if(!node.isObject())throw new IllegalStateException("SCHEME_SCHEMA_KEYWORD_UNSUPPORTED");node.fieldNames().forEachRemaining(key->{if(!KEYWORDS.contains(key))throw new IllegalStateException("SCHEME_SCHEMA_KEYWORD_UNSUPPORTED");});for(String group:Arrays.asList("properties","definitions"))if(node.path(group).isObject())node.get(group).elements().forEachRemaining(this::schema);for(String child:Arrays.asList("items","additionalProperties"))if(node.path(child).isObject())schema(node.get(child));if(node.has("$ref") && (!node.get("$ref").asText().startsWith("#/") || root.at(node.get("$ref").asText().substring(1)).isMissingNode()))throw new IllegalStateException("SCHEME_EXTERNAL_SCHEMA_NOT_ALLOWED");}
    void validate(JsonNode value){check(value,root,"",0,new int[]{0});}
    private void check(JsonNode value,JsonNode schema,String path,int depth,int[] nodes){
        if(depth>40 || ++nodes[0]>100000)throw new IllegalArgumentException("SCHEME_SHAPE_LIMIT");
        if(schema.has("$ref")){String ref=schema.get("$ref").asText();if(!ref.startsWith("#/"))throw new IllegalStateException("SCHEME_EXTERNAL_SCHEMA_NOT_ALLOWED");JsonNode target=root.at(ref.substring(1));if(target.isMissingNode())throw new IllegalStateException("SCHEME_CONTRACT_RESOURCE_INVALID");check(value,target,path,depth,nodes);return;}
        if(schema.has("type")){JsonNode types=schema.get("type");boolean matches=false;if(types.isArray()){for(JsonNode type:types)matches|=type(value,type.asText());}else matches=type(value,types.asText());if(!matches)invalid(path);}
        if(schema.has("const") && !same(value,schema.get("const")))invalid(path);
        if(schema.has("enum")){boolean matched=false;for(JsonNode candidate:schema.get("enum"))matched|=same(value,candidate);if(!matched)invalid(path);}
        if(value.isNull())return;
        if(value.isTextual()){String text=value.asText();int length=text.codePointCount(0,text.length());if(schema.has("minLength") && length<schema.get("minLength").asInt() || schema.has("maxLength") && length>schema.get("maxLength").asInt() || schema.has("pattern") && !java.util.regex.Pattern.compile(schema.get("pattern").asText()).matcher(text).find())invalid(path);}
        if(value.isNumber()){BigDecimal number=value.decimalValue();if(schema.has("minimum") && number.compareTo(schema.get("minimum").decimalValue())<0 || schema.has("maximum") && number.compareTo(schema.get("maximum").decimalValue())>0)invalid(path);}
        if(value.isArray()){if(schema.has("minItems") && value.size()<schema.get("minItems").asInt() || schema.has("maxItems") && value.size()>schema.get("maxItems").asInt())invalid(path);int index=0;for(JsonNode item:value)check(item,schema.has("items")?schema.get("items"):ANY,path+"/"+index++,depth+1,nodes);}
        if(value.isObject()){
            for(JsonNode name:schema.path("required"))if(!value.has(name.asText()))invalid(path+"/"+escape(name.asText()));
            JsonNode properties=schema.path("properties"),additional=schema.path("additionalProperties");Iterator<Map.Entry<String,JsonNode>> fields=value.fields();
            while(fields.hasNext()){Map.Entry<String,JsonNode> field=fields.next();if(field.getKey().equals("@type") || field.getKey().equals("@class"))throw new IllegalArgumentException("SCHEME_RESERVED_TYPE_METADATA");JsonNode shape=properties.get(field.getKey());if(shape==null){if(additional.isBoolean() && !additional.booleanValue())invalid(path+"/"+escape(field.getKey()));shape=additional.isObject()?additional:ANY;}check(field.getValue(),shape,path+"/"+escape(field.getKey()),depth+1,nodes);}
        }
    }
    private static boolean type(JsonNode value,String type){switch(type){case "null":return value.isNull();case "object":return value.isObject();case "array":return value.isArray();case "string":return value.isTextual();case "boolean":return value.isBoolean();case "number":return value.isNumber();case "integer":return value.isNumber() && value.decimalValue().stripTrailingZeros().scale()<=0;default:throw new IllegalStateException("SCHEME_SCHEMA_TYPE_UNSUPPORTED");}}
    private static boolean same(JsonNode a,JsonNode b){return a.isNumber() && b.isNumber()?a.decimalValue().compareTo(b.decimalValue())==0:a.equals(b);}
    static String escape(String value){return value.replace("~","~0").replace("/","~1");}
    private static void invalid(String path){throw new IllegalArgumentException("SCHEME_CONTRACT_INVALID: "+path);}
}
