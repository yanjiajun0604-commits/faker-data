package faker.data.controller;

import com.fasterxml.jackson.databind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.util.*;

@RestController
public class ContractController {
    private static final Set<String> FILES=new HashSet<>(Arrays.asList("manifest-v1.json","scheme-v1.schema.json","rules-v1.json","diagnostics-v1.json","field-extension-manifest-v1.schema.json","field-extension-request-v1.schema.json","field-extension-response-v1.schema.json"));
    private final ObjectMapper json;
    public ContractController(ObjectMapper json){this.json=json;}
    @GetMapping("contracts") public JsonNode manifest(){return read("manifest-v1.json");}
    @GetMapping(value="contracts/README.md",produces="text/markdown;charset=UTF-8") public String guide(){try(InputStream input=getClass().getResourceAsStream("/contracts/README.md")){if(input==null)throw new IllegalStateException("CONTRACT_RESOURCE_MISSING");return org.springframework.util.StreamUtils.copyToString(input,java.nio.charset.StandardCharsets.UTF_8);}catch(IOException error){throw new IllegalStateException("CONTRACT_RESOURCE_INVALID",error);}}
    @GetMapping("contracts/{name}") public JsonNode read(@PathVariable String name){if(!FILES.contains(name))throw new ResponseStatusException(HttpStatus.NOT_FOUND);try(InputStream input=getClass().getResourceAsStream("/contracts/"+name)){if(input==null)throw new IllegalStateException("CONTRACT_RESOURCE_MISSING");return json.readTree(input);}catch(IOException error){throw new IllegalStateException("CONTRACT_RESOURCE_INVALID",error);}}
}
