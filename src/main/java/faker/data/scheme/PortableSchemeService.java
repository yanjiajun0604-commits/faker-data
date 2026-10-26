package faker.data.scheme;

import faker.data.domain.*;
import faker.data.service.db.JdbcServiceFactory;
import faker.data.service.generate.engine.GenerationValidator;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** 分享文件与本机连接分离；导入仅保存配置，不连接目标库或运行扩展。 */
@Service
public class PortableSchemeService {
    private final FakerSchemeService schemes;private final FakerSchemeRepository repository;private final JdbcTemplate jdbc;
    private final ObjectMapper json;private final PortableShape shape;private final TransactionTemplate local,snapshot;
    public PortableSchemeService(FakerSchemeService schemes,FakerSchemeRepository repository,JdbcTemplate jdbc,ObjectMapper json){
        this.schemes=schemes;this.repository=repository;this.jdbc=jdbc;this.json=json;shape=new PortableShape(json);local=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));snapshot=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));snapshot.setReadOnly(true);
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_scheme_private_path(scheme_id INTEGER NOT NULL,pointer TEXT NOT NULL,PRIMARY KEY(scheme_id,pointer))");
    }
    public JsonNode exportScheme(long id,String connectionRef){
        return snapshot.execute(status->exportSnapshot(id,connectionRef));
    }
    private JsonNode exportSnapshot(long id,String connectionRef){
        FakerScheme scheme=schemes.getById(id);if(scheme==null || !"RULES".equals(scheme.getKind()))throw new IllegalArgumentException("SCHEME_RULES_NOT_FOUND");
        if(connectionRef==null || !connectionRef.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("SCHEME_CONNECTION_REF_INVALID");
        ObjectNode document=json.createObjectNode();document.put("format","faker-data.scheme");document.put("formatVersion",1);document.put("name",scheme.getName());document.put("connectionRef",connectionRef);
        if(scheme.getTablesJson()==null || scheme.getTablesJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2097152)throw new IllegalArgumentException("SCHEME_DOCUMENT_LIMIT");JsonNode tables=parse(scheme.getTablesJson());removeComputed(tables);document.set("tables",tables);document.set("requiredLocalValues",json.createArrayNode());document.set("requiredExtensions",json.createArrayNode());document.put("trustedCodeRequired",false);
        shape.validate(document);List<TableVO> typed=tables(tables);GenerationValidator.validatePortable(copy(typed));document.put("trustedCodeRequired",GenerationValidator.requiresTrustedCode(typed));
        Set<String> extensions=new TreeSet<>();collectExtensions(tables,extensions);for(String extension:extensions)((ArrayNode)document.get("requiredExtensions")).add(extension);
        List<String> secrets=new ArrayList<>();JsonNode source=parse(scheme.getDataSourceJson());for(String key:Arrays.asList("url","password","username"))if(source.path(key).isTextual() && !source.path(key).asText().isEmpty())secrets.add(source.path(key).asText());
        if(privateText(scheme.getName(),secrets))throw new IllegalArgumentException("SCHEME_PRIVATE_NAME_REQUIRES_RENAME");
        Map<String,String> redactions=new LinkedHashMap<>();
        for(String pointer:jdbc.queryForList("SELECT pointer FROM faker_scheme_private_path WHERE scheme_id=? ORDER BY pointer",String.class,id))if(!document.at(pointer).isMissingNode())redact(document,pointer,"PRIVATE_BINDING",redactions);
        scanTables(document,secrets,redactions);
        ArrayNode required=(ArrayNode)document.get("requiredLocalValues");redactions.forEach((pointer,reason)->required.addObject().put("pointer",pointer).put("reason",reason));
        shape.validate(document);bounded(document);return document;
    }
    public Map<String,Object> importScheme(PortableImportRequest request){
        JsonNode supplied=request.getDocument();if(supplied==null || !supplied.isObject() || !"faker-data.scheme".equals(supplied.path("format").asText()))throw new IllegalArgumentException("SCHEME_FORMAT_UNSUPPORTED");
        if(!supplied.path("formatVersion").isIntegralNumber() || !supplied.path("formatVersion").canConvertToInt() || supplied.path("formatVersion").asInt()!=1)throw new IllegalArgumentException("SCHEME_VERSION_UNSUPPORTED");
        shape.validate(supplied);bounded(supplied);ObjectNode document=supplied.deepCopy();Map<String,JsonNode> replacements=request.getReplacements();if(replacements==null)throw new IllegalArgumentException("SCHEME_VALUES_REQUIRED");
        Set<String> required=new LinkedHashSet<>();for(JsonNode value:document.get("requiredLocalValues")){
            String pointer=value.path("pointer").asText();if(!pointer.startsWith("/tables/") || !required.add(pointer) || document.at(pointer).isMissingNode() || !document.at(pointer).isNull())throw new IllegalArgumentException("SCHEME_BINDING_POINTER_INVALID");
        }
        if(!replacements.keySet().equals(required))throw new IllegalArgumentException("SCHEME_VALUES_REQUIRED");
        for(String pointer:required)set(document,pointer,replacements.get(pointer)==null?NullNode.instance:replacements.get(pointer));
        shape.validate(document);List<TableVO> tables=tables(document.get("tables"));GenerationValidator.validatePortable(copy(tables));
        boolean trusted=GenerationValidator.requiresTrustedCode(tables);if(trusted && !request.isConfirmTrustedCode())throw new IllegalArgumentException("SCHEME_TRUST_CONFIRMATION_REQUIRED");
        try{JdbcServiceFactory.getJdbcService(request.getDataSource());}catch(IllegalArgumentException|IllegalStateException invalid){throw new IllegalArgumentException("SCHEME_LOCAL_CONNECTION_INVALID");}
        String name=request.getName()==null?document.path("name").asText():request.getName();if(name==null || name.trim().isEmpty() || name.trim().length()>200)throw new IllegalArgumentException("SCHEME_NAME_INVALID");String chosen=name.trim();
        String source=JSON.toJSONString(request.getDataSource()),tableJson=JSON.toJSONString(tables);
        FakerScheme saved=local.execute(status->{
            FakerScheme result=request.isReplaceExisting()?repository.upsert(chosen,source,tableJson):repository.insertNew(chosen,source,tableJson);
            jdbc.update("DELETE FROM faker_scheme_private_path WHERE scheme_id=?",result.getId());List<Object[]> paths=new ArrayList<>();for(String pointer:required)paths.add(new Object[]{result.getId(),pointer});if(!paths.isEmpty())jdbc.batchUpdate("INSERT INTO faker_scheme_private_path(scheme_id,pointer) VALUES(?,?)",paths);return result;
        });
        Map<String,Object> result=new LinkedHashMap<>();result.put("schemeId",saved.getId());result.put("name",saved.getName());result.put("formatVersion",1);result.put("connectionRef",document.get("connectionRef").asText());result.put("trustedCodeRequired",trusted);result.put("boundValueCount",required.size());result.put("generated",false);return result;
    }
    private void scanTables(ObjectNode document,List<String> secrets,Map<String,String> redactions){
        JsonNode tables=document.get("tables");
        for(int t=0;t<tables.size();t++){
            JsonNode table=tables.get(t);String base="/tables/"+t;
            scanWhere(document,table.path("existingData").get("where"),base+"/existingData/where",true,secrets,redactions);
            if(table.path("parentRows").path("condition").isTextual() && privateText(table.path("parentRows").get("condition").asText(),secrets))redact(document,base+"/parentRows/condition","PRIVATE_EXPRESSION",redactions);
            JsonNode columns=table.path("columns");for(int c=0;c<columns.size();c++){
                JsonNode column=columns.get(c);String path=base+"/columns/"+c;
                if(sensitive(column.path("name").asText()) && column.has("regs") && !column.get("regs").isNull())redact(document,path+"/regs","CREDENTIAL_COLUMN",redactions);
                else scanRules(document,column.get("regs"),path+"/regs",secrets,redactions);
            }
            scanDerived(document,table.path("ownership").get("derivedRows"),base+"/ownership/derivedRows",secrets,redactions);
        }
    }
    private void scanRules(ObjectNode document,JsonNode rules,String path,List<String> secrets,Map<String,String> redactions){
        if(rules==null || !rules.isArray())return;
        for(int r=0;r<rules.size();r++){
            JsonNode rule=rules.get(r);String base=path+"/"+r;
            JsonNode inputs=rule.get("inputs");if(inputs!=null && inputs.isArray())for(int i=0;i<inputs.size();i++)if(inputs.get(i).path("value").isTextual() && privateText(inputs.get(i).get("value").asText(),secrets))redact(document,base+"/inputs/"+i+"/value","PRIVATE_LITERAL",redactions);
            if(rule.path("condition").isTextual() && privateText(rule.get("condition").asText(),secrets))redact(document,base+"/condition","PRIVATE_EXPRESSION",redactions);
            JsonNode matches=rule.get("refMatch");if(matches!=null && matches.isObject()){Iterator<Map.Entry<String,JsonNode>> fields=matches.fields();while(fields.hasNext()){Map.Entry<String,JsonNode> field=fields.next();JsonNode value=field.getValue().get("value");if(value!=null && (sensitive(field.getKey()) || value.isTextual() && privateText(value.asText(),secrets)))redact(document,base+"/refMatch/"+PortableShape.escape(field.getKey())+"/value","PRIVATE_MATCH_VALUE",redactions);}}
            JsonNode parameters=rule.get("extensionParameters");if(parameters!=null && parameters.isObject()){
                List<String> names=new ArrayList<>();parameters.fieldNames().forEachRemaining(names::add);for(String name:names){JsonNode value=parameters.get(name);if(!benignParameter(name,value) || value.isTextual() && privateText(value.asText(),secrets))redact(document,base+"/extensionParameters/"+PortableShape.escape(name),"OPAQUE_EXTENSION_PARAMETER",redactions);}
            }
            scanRules(document,rule.get("onTrue"),base+"/onTrue",secrets,redactions);scanRules(document,rule.get("onFalse"),base+"/onFalse",secrets,redactions);
        }
    }
    private void scanDerived(ObjectNode document,JsonNode derived,String path,List<String> secrets,Map<String,String> redactions){if(derived==null || !derived.isArray())return;for(int i=0;i<derived.size();i++){JsonNode relation=derived.get(i);scanWhere(document,relation.get("where"),path+"/"+i+"/where",false,secrets,redactions);scanDerived(document,relation.get("derivedRows"),path+"/"+i+"/derivedRows",secrets,redactions);}}
    private void scanWhere(ObjectNode document,JsonNode where,String path,boolean all,List<String> secrets,Map<String,String> redactions){if(where==null || !where.isObject())return;List<String> names=new ArrayList<>();where.fieldNames().forEachRemaining(names::add);for(String name:names){JsonNode value=where.get(name);if(all || sensitive(name) || value.isTextual() && privateText(value.asText(),secrets))redact(document,path+"/"+PortableShape.escape(name),all?"LOCAL_SOURCE_SELECTOR":"PRIVATE_PREDICATE",redactions);}}
    private static boolean benignParameter(String key,JsonNode value){if(key.equals("sourceField"))return value.isTextual() && value.asText().matches("[\\p{L}_][\\p{L}\\p{N}_]{0,127}");if(key.equals("binaryEncoding"))return value.isTextual() && Arrays.asList("base64","file").contains(value.asText());if(key.equals("role"))return value.isTextual() && value.asText().matches("[A-Za-z0-9_-]{1,64}");return key.equals("formatVersion") && value.isIntegralNumber() && value.canConvertToInt();}
    private static boolean sensitive(String name){return name.toLowerCase(Locale.ROOT).matches("(?:.*_)?(?:password|passwd|pwd|secret|api_key|access_token|refresh_token|username)");}
    private static boolean privateText(String text,List<String> secrets){for(String secret:secrets)if(text.equals(secret) || secret.length()>=4 && text.contains(secret))return true;return java.util.regex.Pattern.compile("(?i)(jdbc:|bearer\\s+\\S+|-----BEGIN[^\\r\\n]*PRIVATE KEY|(?:password|passwd|pwd|token|secret)\\s*[=:]|^[a-z]:[/\\\\]|^/)").matcher(text).find();}
    private static void collectExtensions(JsonNode node,Set<String> ids){if(node.isObject()){if("EXT.FIELD".equals(node.path("code").asText()) && node.path("extension").isTextual())ids.add(node.get("extension").asText());node.elements().forEachRemaining(child->collectExtensions(child,ids));}else if(node.isArray())node.elements().forEachRemaining(child->collectExtensions(child,ids));}
    private static void removeComputed(JsonNode tables){if(!tables.isArray())throw new IllegalArgumentException("SCHEME_TABLES_REQUIRED");for(JsonNode table:tables){if(!table.isObject())throw new IllegalArgumentException("SCHEME_TABLES_REQUIRED");((ObjectNode)table).remove("refOtherTables");for(JsonNode column:table.path("columns"))if(column.isObject())((ObjectNode)column).remove("depended");}}
    private static void redact(ObjectNode root,String pointer,String reason,Map<String,String> redactions){for(String existing:redactions.keySet())if(pointer.startsWith(existing+"/"))return;redactions.keySet().removeIf(existing->existing.startsWith(pointer+"/"));set(root,pointer,NullNode.instance);redactions.putIfAbsent(pointer,reason);}
    private static void set(ObjectNode root,String pointer,JsonNode value){
        JsonPointer path;try{path=JsonPointer.compile(pointer);}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("SCHEME_BINDING_POINTER_INVALID");}if(!pointer.startsWith("/tables/") || path.matches())throw new IllegalArgumentException("SCHEME_BINDING_POINTER_INVALID");JsonNode parent=root.at(path.head());JsonPointer leaf=path.last();
        if(parent.isObject() && parent.has(leaf.getMatchingProperty()))((ObjectNode)parent).set(leaf.getMatchingProperty(),value);else if(parent.isArray() && leaf.getMatchingIndex()>=0 && leaf.getMatchingIndex()<parent.size())((ArrayNode)parent).set(leaf.getMatchingIndex(),value);else throw new IllegalArgumentException("SCHEME_BINDING_POINTER_INVALID");
    }
    private JsonNode parse(String text){try{return json.readTree(text);}catch(Exception invalid){throw new IllegalArgumentException("SCHEME_STORED_JSON_INVALID");}}
    private List<TableVO> tables(JsonNode node){return json.convertValue(node,new TypeReference<List<TableVO>>(){});}
    private List<TableVO> copy(List<TableVO> tables){return json.convertValue(json.valueToTree(tables),new TypeReference<List<TableVO>>(){});}
    private void bounded(JsonNode node){try{if(json.writeValueAsBytes(node).length>2097152)throw new IllegalArgumentException("SCHEME_DOCUMENT_LIMIT");}catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalArgumentException("SCHEME_DOCUMENT_INVALID");}}
}
