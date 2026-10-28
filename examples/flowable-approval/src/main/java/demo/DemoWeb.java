package demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Loopback-only inspection UI for the public acceptance example, not an auth system. */
public final class DemoWeb {
 private DemoWeb() {}

 static Map<String,String> query(String raw)throws Exception {
  Map<String,String> result=new HashMap<>();if(raw==null)return result;
  for(String part:raw.split("&")){String[] pair=part.split("=",2);result.put(URLDecoder.decode(pair[0],"UTF-8"),pair.length==1?"":URLDecoder.decode(pair[1],"UTF-8"));}
  return result;
 }

 static List<Map<String,Object>> list(String state)throws Exception {
  if(!Arrays.asList("SAVED","SUBMITTED","APPROVED").contains(state))throw new IllegalArgumentException("Unknown state");
  List<Map<String,Object>> documents=new ArrayList<>();
  try(Connection c=Fixture.db();PreparedStatement s=c.prepareStatement("SELECT * FROM FD_DOCUMENTS WHERE STATE=? ORDER BY ID LIMIT 1000")){
   s.setString(1,state);try(ResultSet r=s.executeQuery()){
    while(r.next()){
     Map<String,Object> d=new LinkedHashMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)d.put(r.getMetaData().getColumnName(i),r.getObject(i));
     if(d.get("PROCESS_ID")!=null){List<org.flowable.task.api.Task> tasks=Fixture.tasks(d);if(tasks.size()==1)d.put("currentAssignee",tasks.get(0).getAssignee());}
     documents.add(d);
    }
   }
  }
  return documents;
 }

 static Map<String,Object> detail(String id)throws Exception {
  Map<String,Object> d=Fixture.document(id);List<Map<String,Object>> lines=new ArrayList<>();
  try(Connection c=Fixture.db();PreparedStatement s=c.prepareStatement("SELECT * FROM FD_LINES WHERE DOC_ID=? ORDER BY ID")){
   s.setString(1,id);try(ResultSet r=s.executeQuery()){while(r.next()){
    Map<String,Object> line=new LinkedHashMap<>();line.put("ID",r.getString("ID"));line.put("AMOUNT",r.getBigDecimal("AMOUNT"));lines.add(line);
   }}
  }
  d.put("lines",lines);d.put("decodedPayload",Fixture.payload(d));return d;
 }

 static void send(HttpExchange exchange,int code,byte[] bytes,String type)throws IOException {
  exchange.getResponseHeaders().set("Content-Type",type+";charset=utf-8");
  exchange.getResponseHeaders().set("Cache-Control","no-store");
  exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
  exchange.sendResponseHeaders(code,bytes.length);try(OutputStream stream=exchange.getResponseBody()){stream.write(bytes);}
 }

 static byte[] page()throws IOException {
  try(InputStream input=DemoWeb.class.getResourceAsStream("/index.html")){
   if(input==null)throw new FileNotFoundException("Missing public example index.html");
   ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
   while((n=input.read(buffer))!=-1)output.write(buffer,0,n);return output.toByteArray();
  }
 }

 public static void start(int port)throws Exception {
  if(port<1024 || port>65535)throw new IllegalArgumentException("Use a separate local demo port");
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
  server.createContext("/",exchange->{
   try{
    String host=exchange.getRequestHeaders().getFirst("Host");
    if(!("127.0.0.1:"+port).equals(host) && !("localhost:"+port).equals(host))throw new IllegalArgumentException("Loopback host required");
    String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();
    Map<String,String> q=query(exchange.getRequestURI().getRawQuery());Object result;
    if("GET".equals(method) && "/".equals(path)){send(exchange,200,page(),"text/html");return;}
    if("GET".equals(method) && "/api/documents".equals(path))result=list(q.getOrDefault("state","SAVED"));
    else {
     String[] parts=path.split("/");
     if(parts.length<4 || !"api".equals(parts[1]) || !"documents".equals(parts[2]) || !parts[3].matches("[a-fA-F0-9-]{36}"))throw new IllegalArgumentException("Unknown route");
     String id=parts[3];
     if("GET".equals(method) && parts.length==4)result=detail(id);
     else {
      String origin=exchange.getRequestHeaders().getFirst("Origin");
      if(!"POST".equals(method) || parts.length!=5 || !"1".equals(exchange.getRequestHeaders().getFirst("X-Demo-Action"))
         || origin!=null && !("http://127.0.0.1:"+port).equals(origin) && !("http://localhost:"+port).equals(origin))throw new IllegalArgumentException("Explicit same-origin action required");
      String actor=q.get("actor");if(!Arrays.asList("owner","reviewer","auditor").contains(actor))throw new IllegalArgumentException("Choose a demo role");
      if("submit".equals(parts[4]))Fixture.submit(id,actor);
      else if("approve".equals(parts[4]))Fixture.approve(id,actor);
      else throw new IllegalArgumentException("Unknown action");
      result=detail(id);
     }
    }
    send(exchange,200,Fixture.JSON.writeValueAsBytes(result),"application/json");
   }catch(Exception error){send(exchange,400,Fixture.JSON.writeValueAsBytes(Collections.singletonMap("error","Request rejected; check state, current assignee and demo configuration")),"application/json");}
  });
  Runtime.getRuntime().addShutdownHook(new Thread(()->server.stop(0)));
  server.start();System.out.println("Public Flowable example: http://127.0.0.1:"+port+"/ (local role simulation, not authentication)");
  new CountDownLatch(1).await();
 }
}
