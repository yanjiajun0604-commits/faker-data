package faker.data.scheme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Rebase local binding paths without storing a second copy of private values. */
final class PrivateBindingPaths {
    private static final ObjectMapper JSON=new ObjectMapper();
    private PrivateBindingPaths() {}

    static Map<String,String> fingerprints(String tables,Collection<String> paths) {
        Map<String,String> result=new LinkedHashMap<>();if(paths.isEmpty())return result;
        try {
            ObjectNode root=JSON.createObjectNode();root.set("tables",JSON.readTree(tables));
            for(String pointer:paths){JsonNode value=root.at(pointer);if(value.isMissingNode())throw changed();
                byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder hash=new StringBuilder("v1:");for(byte b:digest)hash.append(String.format("%02x",b & 255));result.put(pointer,hash.toString());
            }
            return result;
        }catch(java.io.IOException | java.security.NoSuchAlgorithmException invalid){throw changed();}
    }

    static void requireCurrent(String tables,Map<String,String> expected) {
        Map<String,String> actual=fingerprints(tables,expected.keySet());
        for(Map.Entry<String,String> entry:expected.entrySet())if(entry.getValue()==null || !entry.getValue().equals(actual.get(entry.getKey())))throw changed();
    }

    static List<String> rebase(String previous,String next,List<String> paths) {
        if(paths.isEmpty())return paths;
        try {
            ObjectNode oldRoot=JSON.createObjectNode(),newRoot=JSON.createObjectNode();
            oldRoot.set("tables",JSON.readTree(previous));newRoot.set("tables",JSON.readTree(next));
            Set<String> result=new LinkedHashSet<>();
            for(String pointer:paths) {
                if(!pointer.startsWith("/tables/"))throw changed();
                JsonNode old=oldRoot,current=newRoot;String mapped="",container="",ruleBlock=null;
                boolean anonymousArray=false;
                for(String token:pointer.substring(1).split("/",-1)) {
                    String name=token.replace("~1","/").replace("~0","~");
                    if(old.isArray()) {
                        if(!name.matches("0|[1-9][0-9]*") || !current.isArray())throw changed();
                        int index=Integer.parseInt(name);JsonNode value=old.path(index);
                        if(value.isMissingNode())throw changed();
                        String identity=container.equals("tables") || container.equals("columns")?"name":container.equals("derivedRows")?"table":null;
                        if(identity!=null)index=byIdentity(current,identity,value.path(identity).asText());
                        else anonymousArray=true;
                        mapped+="/"+index;old=value;current=current.path(index);
                    } else {
                        if(!old.isObject())throw changed();
                        mapped+="/"+PortableShape.escape(name);old=old.path(name);current=current.path(name);container=name;
                        if(name.equals("regs") && mapped.matches("/tables/[0-9]+/columns/[0-9]+/regs"))ruleBlock=mapped;
                    }
                    if(old.isMissingNode())throw changed();
                    if(current.isMissingNode())break;
                }
                String target=mapped;
                // Anonymous rule/input arrays have no durable element identity.
                // If their positions changed, retain privacy for the whole field.
                if(ruleBlock!=null && (current.isMissingNode() || anonymousArray && !old.equals(current)))target=ruleBlock;
                else if(current.isMissingNode())throw changed();
                if(newRoot.at(target).isMissingNode())throw changed();
                boolean covered=false;for(String prior:result)if(target.equals(prior) || target.startsWith(prior+"/")){covered=true;break;}
                if(!covered){final String parent=target;result.removeIf(prior->prior.startsWith(parent+"/"));result.add(target);}
            }
            return new ArrayList<>(result);
        } catch(java.io.IOException | NumberFormatException invalid) {throw changed();}
    }

    private static int byIdentity(JsonNode values,String key,String name) {
        if(name.isEmpty())throw changed();int found=-1;
        for(int i=0;i<values.size();i++)if(name.equalsIgnoreCase(values.get(i).path(key).asText())) {
            if(found>=0)throw changed();found=i;
        }
        if(found<0)throw changed();return found;
    }

    private static IllegalArgumentException changed(){return new IllegalArgumentException("SCHEME_PRIVATE_BINDING_LOCATION_CHANGED");}
}
