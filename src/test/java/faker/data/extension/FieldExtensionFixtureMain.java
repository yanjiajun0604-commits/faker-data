package faker.data.extension;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class FieldExtensionFixtureMain {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper=new ObjectMapper();JsonNode input=mapper.readTree(Paths.get(args[0]).toFile());
        Path job=Paths.get(args[1]).getParent();ArrayNode values=mapper.createArrayNode();
        for(JsonNode field:input.path("fields")) {
            JsonNode row=input.path("tables").path("children").get(field.path("rowIndex").asInt());
            byte[] binary="large-inline".equals(args[2])?new byte[1048577]:row.path("parent_id").asText().getBytes(StandardCharsets.UTF_8);
            String name="payload-"+field.path("rowIndex").asInt()+".bin";
            boolean inline=args[2].contains("inline") || "invalid-base64".equals(args[2]);
            if(!inline)Files.write(job.resolve(name),binary);
            ObjectNode value=values.addObject();value.put("table",field.path("table").asText());
            value.put("rowIndex",field.path("rowIndex").asInt());value.put("column",field.path("column").asText());
            ObjectNode encoded=value.putObject("value");
            if(inline)encoded.put("$base64","invalid-base64".equals(args[2])?"***":java.util.Base64.getEncoder().encodeToString(binary));
            else encoded.put("$binary",name);
            encoded.put("sha256","bad-inline-hash".equals(args[2])?"wrong":ExtensionSupport.sha256(binary));
        }
        if("tamper".equals(args[2]))values.addObject().put("table","parents").put("rowIndex",0).put("column","id").put("value","overwritten");
        ObjectNode output=mapper.createObjectNode();output.set("values",values);mapper.writeValue(Paths.get(args[1]).toFile(),output);
    }
}
