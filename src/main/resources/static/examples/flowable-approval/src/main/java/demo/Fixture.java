package demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.*;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;

public class Fixture {
 static final ObjectMapper JSON=new ObjectMapper();
 static Map<String,String> profile; static ProcessEngine engine;
 static final String BPMN="<?xml version='1.0' encoding='UTF-8'?><definitions xmlns='http://www.omg.org/spec/BPMN/20100524/MODEL' xmlns:flowable='http://flowable.org/bpmn' targetNamespace='faker.example'><process id='publicApproval' name='Public approval' isExecutable='true'><startEvent id='start'/><sequenceFlow id='f1' sourceRef='start' targetRef='review'/><userTask id='review' name='Review document' flowable:assignee='${reviewer}'/><sequenceFlow id='f2' sourceRef='review' targetRef='audit'/><userTask id='audit' name='Audit document' flowable:assignee='${auditor}'/><sequenceFlow id='f3' sourceRef='audit' targetRef='end'/><endEvent id='end'/></process></definitions>";
 static Connection db()throws Exception{return DriverManager.getConnection(profile.get("url"),profile.get("username"),profile.get("password"));}
 static void businessSchema()throws Exception{
  try(Connection c=db();Statement s=c.createStatement()){
   s.execute("CREATE TABLE FD_DEMO_META(ID INT PRIMARY KEY,VERSION INT NOT NULL)");s.execute("INSERT INTO FD_DEMO_META VALUES(1,1)");
   s.execute("CREATE TABLE FD_DOCUMENTS(ID VARCHAR(36) PRIMARY KEY,STATE VARCHAR(16) NOT NULL,OWNER VARCHAR(64),REVIEWER VARCHAR(64),AUDITOR VARCHAR(64),SUBJECT VARCHAR(120),AMOUNT DECIMAL(12,2),PROCESS_ID VARCHAR(64)) ENGINE=InnoDB");
   s.execute("CREATE TABLE FD_LINES(ID VARCHAR(36) PRIMARY KEY,DOC_ID VARCHAR(36) NOT NULL,AMOUNT DECIMAL(12,2),FOREIGN KEY(DOC_ID) REFERENCES FD_DOCUMENTS(ID)) ENGINE=InnoDB");
  }
 }
 static void requireDemo()throws Exception{try(Connection c=db();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT VERSION FROM FD_DEMO_META WHERE ID=1")){if(!r.next()||r.getInt(1)!=1)throw new IllegalStateException("Not a prepared public demo schema");}}
 static void purge(File proof)throws Exception{
  // Only explicitly named demo documents are touched. Engine-created history and
  // runtime rows are removed by Flowable itself, not broad DELETE predicates.
  Map<?,?> receipt=JSON.readValue(proof,Map.class);Object raw=receipt.get("documents");
  if(!"faker-data.flowable-acceptance/1".equals(receipt.get("format")) || !(raw instanceof List))throw new IllegalArgumentException("Expected acceptance proof with exact document IDs");
  Set<String> ids=new LinkedHashSet<>();for(Object value:(List<?>)raw){
   String id=String.valueOf(value);if(!id.matches("[a-fA-F0-9-]{36}"))throw new IllegalArgumentException("Invalid document ID");ids.add(id);
  }
  for(String id:ids){
   Map<String,Object> doc=document(id);String process=(String)doc.get("PROCESS_ID");
   if(process!=null){
    ProcessInstance runtime=engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(process).singleResult();
    if(runtime!=null && !id.equals(runtime.getBusinessKey()))throw new IllegalStateException("Process ownership mismatch");
    org.flowable.engine.history.HistoricProcessInstance history=engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(process).singleResult();
    if(history!=null && !id.equals(history.getBusinessKey()))throw new IllegalStateException("History ownership mismatch");
    if(runtime!=null)engine.getRuntimeService().deleteProcessInstance(process,"explicit public demo cleanup");
    if(history!=null)engine.getHistoryService().deleteHistoricProcessInstance(process);
   }
   try(Connection c=db()){
    c.setAutoCommit(false);try(PreparedStatement lines=c.prepareStatement("DELETE FROM FD_LINES WHERE DOC_ID=?");PreparedStatement document=c.prepareStatement("DELETE FROM FD_DOCUMENTS WHERE ID=?")){
     lines.setString(1,id);lines.executeUpdate();document.setString(1,id);if(document.executeUpdate()!=1)throw new IllegalStateException("Document changed");c.commit();
    }
   }
  }
  System.out.println("Removed only "+ids.size()+" explicitly named demo documents via engine-aware cleanup");
 }
 static Map<String,Object> rows()throws Exception{
  Map<String,Object> out=new TreeMap<>();try(Connection c=db();ResultSet tables=c.getMetaData().getTables(c.getCatalog(),null,"ACT_%",new String[]{"TABLE"})){
   while(tables.next()){String table=tables.getString("TABLE_NAME");List<Map<String,Object>> values=new ArrayList<>();List<Map<String,Object>> columns=new ArrayList<>();
    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM `"+table+"`")){ResultSetMetaData m=r.getMetaData();for(int i=1;i<=m.getColumnCount();i++){Map<String,Object> col=new LinkedHashMap<>();col.put("name",m.getColumnName(i));col.put("javaType",m.getColumnType(i)==Types.TIMESTAMP ? "java.sql.Timestamp" : m.getColumnClassName(i));col.put("sqlType",m.getColumnType(i));columns.add(col);}while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=m.getColumnCount();i++){Object v=r.getObject(i);if(v instanceof java.time.LocalDateTime || v instanceof Timestamp)v=r.getTimestamp(i).getTime();if(v instanceof byte[])v=Base64.getEncoder().encodeToString((byte[])v);row.put(m.getColumnName(i),v);}values.add(row);}}
    Map<String,Object> entry=new LinkedHashMap<>();entry.put("columns",columns);entry.put("rows",values);List<Map<String,String>> fks=new ArrayList<>();try(ResultSet f=c.getMetaData().getImportedKeys(c.getCatalog(),null,table)){while(f.next()){Map<String,String> fk=new HashMap<>();fk.put("column",f.getString("FKCOLUMN_NAME"));fk.put("table",f.getString("PKTABLE_NAME").toUpperCase(Locale.ROOT));fks.add(fk);}}entry.put("foreignKeys",fks);out.put(table.toUpperCase(Locale.ROOT),entry);
   }
  }return out;
 }
 @SuppressWarnings("unchecked") static Map<String,Object> delta(Map<String,Object> baseline,Map<String,Object> current){Map<String,Object> delta=new TreeMap<>();for(String table:current.keySet()){if(table.equals("ACT_GE_PROPERTY"))continue;Map<String,Object> e=(Map<String,Object>)current.get(table);List<Object> old=(List<Object>)((Map<String,Object>)baseline.get(table)).get("rows");List<Object> fresh=new ArrayList<>();for(Object row:(List<Object>)e.get("rows"))if(!old.contains(row))fresh.add(row);if(!fresh.isEmpty()){Map<String,Object> n=new LinkedHashMap<>(e);n.put("rows",fresh);delta.put(table,n);}}return delta;}
 static Map<String,Object> document(String id)throws Exception{
  try(Connection c=db();PreparedStatement s=c.prepareStatement("SELECT * FROM FD_DOCUMENTS WHERE ID=?")){s.setString(1,id);try(ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("No document");Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnName(i),r.getObject(i));return row;}}
 }
 static void updateDocument(String id,String state,String process)throws Exception{
  try(Connection c=db();PreparedStatement s=c.prepareStatement("UPDATE FD_DOCUMENTS SET STATE=?,PROCESS_ID=? WHERE ID=?")){s.setString(1,state);s.setString(2,process);s.setString(3,id);if(s.executeUpdate()!=1)throw new IllegalStateException("Missing document");}
 }
 static Object payload(Map<String,Object> doc){
  String process=(String)doc.get("PROCESS_ID");if(process==null)return null;
  if(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(process).count()>0)return engine.getRuntimeService().getVariable(process,"payload");
  org.flowable.variable.api.history.HistoricVariableInstance value=engine.getHistoryService().createHistoricVariableInstanceQuery().processInstanceId(process).variableName("payload").singleResult();return value==null?null:value.getValue();
 }
 static List<Task> tasks(Map<String,Object> doc){return engine.getTaskService().createTaskQuery().processInstanceId((String)doc.get("PROCESS_ID")).list();}
 static void submit(String id,String actor)throws Exception{
  Map<String,Object> d=document(id);if(!"SAVED".equals(d.get("STATE")) || !d.get("OWNER").equals(actor))throw new IllegalArgumentException("Owner and SAVED state required");
  Map<String,Object> vars=new HashMap<>();vars.put("reviewer",d.get("REVIEWER"));vars.put("auditor",d.get("AUDITOR"));Map<String,Object> p=new LinkedHashMap<>();p.put("documentId",id);p.put("amount",200);vars.put("payload",p);
  ProcessInstance instance=engine.getRuntimeService().startProcessInstanceByKey("publicApproval",id,vars);updateDocument(id,"SUBMITTED",instance.getId());
 }
 static void approve(String id,String actor)throws Exception{
  Map<String,Object> d=document(id);if(!"SUBMITTED".equals(d.get("STATE")))throw new IllegalArgumentException("SUBMITTED required");
  List<Task> tasks=tasks(d);if(tasks.size()!=1 || !actor.equals(tasks.get(0).getAssignee()))throw new IllegalArgumentException("Current assignee required");
  engine.getTaskService().complete(tasks.get(0).getId());if(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId((String)d.get("PROCESS_ID")).count()==0)updateDocument(id,"APPROVED",(String)d.get("PROCESS_ID"));
 }
 @SuppressWarnings("unchecked") static void accept(File output,boolean actions)throws Exception{
  Map<String,String> ids=new LinkedHashMap<>();Map<String,Integer> counts=new LinkedHashMap<>();List<Map.Entry<String,String>> cases=new ArrayList<>();List<String> documents=new ArrayList<>();
  try(Connection c=db();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT ID,STATE FROM FD_DOCUMENTS ORDER BY STATE,ID")){
   while(r.next()){
    String id=r.getString(1),state=r.getString(2);ids.putIfAbsent(state,id);counts.put(state,counts.getOrDefault(state,0)+1);cases.add(new AbstractMap.SimpleImmutableEntry<>(state,id));documents.add(id);
    if(documents.size()>600)throw new IllegalStateException("Public acceptance limit is 600 documents");
   }
  }
  if(ids.size()!=3)throw new IllegalStateException("Missing states");
  for(Map.Entry<String,String> e:cases){
   Map<String,Object> d=document(e.getValue());try(Connection c=db();PreparedStatement s=c.prepareStatement("SELECT COUNT(*),SUM(AMOUNT) FROM FD_LINES WHERE DOC_ID=?")){s.setString(1,e.getValue());try(ResultSet r=s.executeQuery()){r.next();if(r.getInt(1)!=2||r.getBigDecimal(2).compareTo((java.math.BigDecimal)d.get("AMOUNT"))!=0)throw new IllegalStateException("Detail mismatch");}}
   if(!"SAVED".equals(e.getKey())){Map<String,Object> decoded=(Map<String,Object>)payload(d);if(!d.get("ID").equals(decoded.get("documentId"))||!Integer.valueOf(200).equals(decoded.get("amount")))throw new IllegalStateException("Binary variable mismatch");
    if("SUBMITTED".equals(e.getKey()) && (tasks(d).size()!=1 || !"reviewer".equals(tasks(d).get(0).getAssignee())))throw new IllegalStateException("Missing real inbox task");
    if("APPROVED".equals(e.getKey()) && (engine.getRuntimeService().createProcessInstanceQuery().processInstanceId((String)d.get("PROCESS_ID")).count()!=0 || engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId((String)d.get("PROCESS_ID")).finished().count()!=1 || engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId((String)d.get("PROCESS_ID")).finished().count()!=2))throw new IllegalStateException("Not really finished");
   }
  }
  // Both original generated in-flight and originally saved documents must remain actionable.
  if(actions)for(String state:Arrays.asList("SUBMITTED","SAVED")){
   String id=ids.get(state);if(state.equals("SAVED"))submit(id,"owner");
   try{approve(id,"auditor");throw new IllegalStateException("Wrong assignee accepted");}catch(IllegalArgumentException expected){}
   approve(id,"reviewer");if(tasks(document(id)).size()!=1||!"auditor".equals(tasks(document(id)).get(0).getAssignee()))throw new IllegalStateException("Did not advance to auditor");
   approve(id,"auditor");if(!"APPROVED".equals(document(id).get("STATE")))throw new IllegalStateException("Did not finish");
  }
  Map<String,Object> proof=new LinkedHashMap<>();proof.put("format","faker-data.flowable-acceptance/1");proof.put("initialStates",ids);proof.put("initialStateCounts",counts);proof.put("documents",documents);proof.put("realEngineReadAndDeserialize",true);proof.put("ownerSubmit",actions);proof.put("reviewerThenAuditor",actions);proof.put("wrongActorRejected",actions);proof.put("sampleActionsApproved",actions);JSON.writerWithDefaultPrettyPrinter().writeValue(output,proof);
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  profile=(Map<String,String>)((Map<?,?>)JSON.readValue(new File(args[1]),Map.class)).get("default");
  if(!profile.get("url").matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/[A-Za-z0-9_]+_flowable_demo(?:\\?.*)?"))throw new IllegalArgumentException("Only a dedicated loopback *_flowable_demo schema is accepted");
  String mode=args[0];
  if(!Arrays.asList("init","templates","verify","accept","purge","serve").contains(mode))throw new IllegalArgumentException("Unknown demo command");
  if(mode.equals("init")){try(Connection c=db();ResultSet r=c.getMetaData().getTables(c.getCatalog(),null,"%",new String[]{"TABLE"})){if(r.next())throw new IllegalStateException("Schema must be empty");}}
  ProcessEngineConfiguration configuration=ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration().setJdbcDriver("com.mysql.cj.jdbc.Driver").setJdbcUrl(profile.get("url")).setJdbcUsername(profile.get("username")).setJdbcPassword(profile.get("password")).setDatabaseSchemaUpdate(mode.equals("init")?"true":"false").setAsyncExecutorActivate(false).setHistory("audit");
  // Explicit catalog prevents MySQL metadata from mistaking another visible
  // schema's ACT_GE_PROPERTY for this empty demo schema during initialization.
  try(Connection c=db()){configuration.setDatabaseCatalog(c.getCatalog());}
  engine=configuration.buildProcessEngine();
  try{
   if(mode.equals("init")){businessSchema();engine.getRepositoryService().createDeployment().addString("approval.bpmn20.xml",BPMN).deploy();System.out.println("DEPLOYED");}
   else if(mode.equals("purge")){requireDemo();purge(new File(args[2]));}
   else if(mode.equals("serve")){requireDemo();DemoWeb.start(Integer.parseInt(args[2]));}
   else if(mode.equals("accept") || mode.equals("verify")){requireDemo();accept(new File(args[2]),mode.equals("accept"));}
   else if(mode.equals("templates")){
    requireDemo();try(Connection c=db();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM FD_DOCUMENTS")){r.next();if(r.getInt(1)!=0)throw new IllegalStateException("Capture reference shapes before generating documents");}
    Map<String,Object> baseline=rows(),out=new LinkedHashMap<>();
    for(String state:Arrays.asList("SUBMITTED","APPROVED")){
     Map<String,Object> vars=new HashMap<>();vars.put("reviewer","reviewer");vars.put("auditor","auditor");Map<String,Object> payload=new LinkedHashMap<>();payload.put("documentId","template-document");payload.put("amount",200);vars.put("payload",payload);
     ProcessInstance p=engine.getRuntimeService().startProcessInstanceByKey("publicApproval","template-document",vars);
     Map<String,Object> active=delta(baseline,rows()); if(state.equals("APPROVED")){for(int i=0;i<2;i++){Task t=engine.getTaskService().createTaskQuery().processInstanceId(p.getId()).singleResult();engine.getTaskService().complete(t.getId());}}
     Map<String,Object> sample=new LinkedHashMap<>();sample.put("processId",p.getId());Map<String,Object> finished=delta(baseline,rows());sample.put("tables",finished);Set<String> tokens=new TreeSet<>();for(Map<String,Object> graph:Arrays.asList(active,finished))for(Object e:graph.values())for(Object row:(List<?>)((Map<?,?>)e).get("rows"))tokens.add(String.valueOf(((Map<?,?>)row).get("ID_")));sample.put("idTokens",tokens);out.put(state,sample);
     if(!p.isEnded() && engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(p.getId()).count()>0)engine.getRuntimeService().deleteProcessInstance(p.getId(),"template-only");engine.getHistoryService().deleteHistoricProcessInstance(p.getId());
    }
    JSON.writerWithDefaultPrettyPrinter().writeValue(new File(args[2]),out);
   }
  }finally{engine.close();}
 }
}



