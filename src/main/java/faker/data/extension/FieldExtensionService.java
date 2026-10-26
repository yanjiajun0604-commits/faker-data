package faker.data.extension;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.*;
import java.util.*;

/** 扩展只能返回已声明字段的值，不接收连接信息，不允许新增记录或覆盖普通字段。 */
@Slf4j
@Component
public class FieldExtensionService {
    private final ObjectMapper mapper;
    private final Path root,jobs;
    private final boolean enabled;
    public boolean isEnabled(){return enabled;}
    public FieldExtensionService(ObjectMapper mapper,@Value("${faker.extensions.root:}") String root,
            @Value("${faker.data-dir:./data}") String directory,@Value("${faker.extensions.enabled:false}") boolean enabled) {
        this.mapper=mapper;Path data=Paths.get(directory).toAbsolutePath().normalize();
        this.root=root.isEmpty()?data.resolve("extensions"):Paths.get(root).toAbsolutePath().normalize();
        jobs=data.resolve("field-extension-jobs");this.enabled=enabled;
    }
    public void resolve(String task,Map<String,List<Map<String,Object>>> rows) {
        if(!enabled)throw new IllegalStateException("LOCAL_EXTENSIONS_DISABLED");
        Map<String,ArrayNode> groups=new LinkedHashMap<>();
        for(Map.Entry<String,List<Map<String,Object>>> table:rows.entrySet())for(int index=0;index<table.getValue().size();index++)
            for(Map.Entry<String,Object> column:table.getValue().get(index).entrySet())if(column.getValue() instanceof FieldExtensionMetaType.Request) {
                FieldExtensionMetaType.Request request=(FieldExtensionMetaType.Request)column.getValue();
                ObjectNode field=groups.computeIfAbsent(request.extension,k->mapper.createArrayNode()).addObject();
                field.put("table",table.getKey());field.put("rowIndex",index);field.put("column",column.getKey());field.set("parameters",mapper.valueToTree(request.parameters));
            }
        try {
            if(!task.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("INVALID_FIELD_TASK_ID");
            long binaryBytes=0;
            for(Map.Entry<String,ArrayNode> group:groups.entrySet()) {
                Path folder=root.resolve(group.getKey()).toRealPath();
                if(!folder.startsWith(root.toRealPath()))throw new IllegalArgumentException("INVALID_EXTENSION_DIRECTORY");
                Path manifest=folder.resolve("extension.json").toRealPath();
                if(!manifest.startsWith(folder) || Files.size(manifest)>1048576)throw new IllegalArgumentException("INVALID_EXTENSION_MANIFEST");
                JsonNode spec=mapper.readTree(manifest.toFile()),allowed=spec.path("allowedFields");
                if(!allowed.isObject())throw new IllegalArgumentException("FIELD_EXTENSION_ALLOWLIST_REQUIRED");
                Set<String> expected=new HashSet<>();
                for(JsonNode field:group.getValue()) {
                    String table=field.path("table").asText(),column=field.path("column").asText();
                    ExtensionSupport.identifier(table,true);ExtensionSupport.identifier(column,false);
                    boolean permitted=false;for(JsonNode candidate:allowed.path(table))if(column.equals(candidate.asText()))permitted=true;
                    if(!permitted)throw new IllegalArgumentException("EXTENSION_FIELD_NOT_ALLOWED");
                    expected.add(key(table,field.path("rowIndex").asInt(),column));
                }
                ObjectNode input=mapper.createObjectNode();input.put("protocol","faker-field-extension/1");input.put("taskId",task);input.set("fields",group.getValue());
                ObjectNode context=input.putObject("tables");
                for(Map.Entry<String,List<Map<String,Object>>> table:rows.entrySet()) {
                    ArrayNode records=context.putArray(table.getKey());
                    for(Map<String,Object> row:table.getValue()) {
                        ObjectNode record=records.addObject();
                        for(Map.Entry<String,Object> field:row.entrySet())record.set(field.getKey(),field.getValue() instanceof FieldExtensionMetaType.Request?NullNode.instance:mapper.valueToTree(field.getValue()));
                    }
                }
                if(mapper.writeValueAsBytes(input).length>16777216)throw new IllegalArgumentException("FIELD_EXTENSION_CONTEXT_LIMIT");
                Path job=jobs.resolve(task).resolve(group.getKey());Files.createDirectories(job.getParent());Files.createDirectory(job);
                Path output=ExtensionRunner.run(mapper,spec,folder,job,input);JsonNode result=mapper.readTree(output.toFile());
                if(!result.isObject() || result.size()!=1 || !result.path("values").isArray())throw new IllegalArgumentException("INVALID_FIELD_EXTENSION_OUTPUT");
                for(JsonNode field:result.path("values")) {
                    String table=field.path("table").asText(),column=field.path("column").asText();
                    if(!field.path("rowIndex").isIntegralNumber())throw new IllegalArgumentException("INVALID_FIELD_EXTENSION_ROW");
                    int row=field.path("rowIndex").asInt(-1);
                    if(!expected.remove(key(table,row,column)) || !field.has("value"))throw new IllegalArgumentException("UNREQUESTED_EXTENSION_FIELD");
                    Object value=decode(field.get("value"),job);
                    if(value instanceof byte[]){binaryBytes+=((byte[])value).length;if(binaryBytes>67108864)throw new IllegalArgumentException("EXTENSION_BINARY_LIMIT");}
                    rows.get(table).get(row).put(column,value);
                }
                if(!expected.isEmpty())throw new IllegalArgumentException("MISSING_EXTENSION_FIELD");
            }
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("FIELD_EXTENSION_FAILED",e);}
    }
    private String key(String table,int row,String column){return table+":"+row+":"+column;}
    private Object decode(JsonNode value,Path job)throws Exception {
        if(value.isNull())return null;
        if(value.isTextual())return value.asText();if(value.isBoolean())return value.asBoolean();
        if(value.isIntegralNumber())return value.bigIntegerValue();if(value.isFloatingPointNumber())return value.decimalValue();
        if(value.isObject() && value.size()==1 && value.path("$timestampMillis").isIntegralNumber())return value.get("$timestampMillis").longValue();
        if(value.isObject() && value.size()==2 && value.has("$base64") && value.has("sha256")) {
            // 小二进制直接传输，避免为每个字段创建和重新读取一个文件。
            JsonNode encoded=value.get("$base64");
            if(!encoded.isTextual() || encoded.textValue().length()>1398104)throw new IllegalArgumentException("INVALID_EXTENSION_BASE64");
            byte[] bytes;
            try{bytes=Base64.getDecoder().decode(encoded.textValue());}
            catch(IllegalArgumentException e){throw new IllegalArgumentException("INVALID_EXTENSION_BASE64");}
            if(bytes.length>1048576)throw new IllegalArgumentException("INVALID_EXTENSION_BASE64");
            if(!ExtensionSupport.sha256(bytes).equalsIgnoreCase(value.path("sha256").asText()))throw new IllegalArgumentException("EXTENSION_BINARY_HASH_MISMATCH");
            return bytes;
        }
        if(value.isObject() && value.size()==2 && value.has("$binary") && value.has("sha256")) {
            Path relative=Paths.get(value.path("$binary").asText());
            if(relative.isAbsolute() || relative.normalize().startsWith(".."))throw new IllegalArgumentException("INVALID_EXTENSION_BINARY_PATH");
            Path file=job.resolve(relative).toRealPath();
            if(!file.startsWith(job.toRealPath()) || !Files.isRegularFile(file) || Files.size(file)>16777216)throw new IllegalArgumentException("INVALID_EXTENSION_BINARY_PATH");
            byte[] bytes=Files.readAllBytes(file);
            if(!ExtensionSupport.sha256(bytes).equalsIgnoreCase(value.path("sha256").asText()))throw new IllegalArgumentException("EXTENSION_BINARY_HASH_MISMATCH");
            return bytes;
        }
        throw new IllegalArgumentException("UNSUPPORTED_EXTENSION_VALUE");
    }
    public void audit(String task,Object audit) {
        if(!enabled)return;
        try{Path folder=jobs.resolve(task);Files.createDirectories(folder);mapper.writerWithDefaultPrettyPrinter().writeValue(folder.resolve("rule-audit.json").toFile(),audit);}
        catch(Exception e){log.warn("生成已提交，但规则审计文件写入失败: {}",task);}
    }
}
