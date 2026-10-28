package demo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.*;

/** Field-only extension: no JDBC, HTTP or Flowable API calls. */
public class PayloadCodec {
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  ObjectMapper json=new ObjectMapper();Map<String,Object> request=json.readValue(new File(args[0]),Map.class);
  if(!"faker-field-extension/1".equals(request.get("protocol")))throw new IllegalArgumentException("Unexpected protocol");
  Map<String,List<Map<String,Object>>> tables=(Map)request.get("tables");List<Map<String,Object>> values=new ArrayList<>();
  for(Map<String,Object> field:(List<Map<String,Object>>)request.get("fields")){
   String table=(String)field.get("table"),column=(String)field.get("column");
   if(!"ACT_GE_BYTEARRAY".equals(table)||!"BYTES_".equals(column))throw new IllegalArgumentException("Unexpected field");
   Map<String,Object> row=tables.get(table).get(((Number)field.get("rowIndex")).intValue());
   Map<String,Object> payload=new LinkedHashMap<>();payload.put("documentId",row.get("fixture_document"));payload.put("amount",200);
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ObjectOutputStream stream=new ObjectOutputStream(bytes)){stream.writeObject(payload);}
   Map<String,Object> encoded=new HashMap<>();encoded.put("$base64",Base64.getEncoder().encodeToString(bytes.toByteArray()));StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()))hash.append(String.format("%02x",b & 255));encoded.put("sha256",hash.toString());
   Map<String,Object> result=new LinkedHashMap<>(field);result.remove("parameters");result.put("value",encoded);values.add(result);
  }
  json.writeValue(new File(args[1]),Collections.singletonMap("values",values));
 }
}

