package faker.data.batch;

import faker.data.domain.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

/** 清理只核对已登记主键；不能证明的权限、引用和副作用均拒绝放行。 */
final class BatchCleanupChecks {
    static final int MAX_ROWS=100000;
    static final class Owned {
        TargetRowSnapshot table;List<Map<String,String>> keys=new ArrayList<>();Map<String,String> fingerprints=new LinkedHashMap<>();
    }
    static final class Check {
        Map<String,Object> report=new LinkedHashMap<>();Map<String,Owned> owned=new LinkedHashMap<>();
        List<Map<String,Object>> blockers=new ArrayList<>();List<String> order=new ArrayList<>();String manifest="",foreignHash="";int rowCount;
        void block(String code,String table){if(blockers.size()<100)blockers.add(map("code",code,"table",table));}
        void finish(){report.put("eligible",blockers.isEmpty());report.put("rowCount",rowCount);report.put("blockers",blockers);report.put("deleteOrder",order);report.put("scope","ORIGINAL_TRACKED_INSERTS_AND_PHYSICAL_FOREIGN_KEYS");report.put("notes","Later weakly linked/business-derived rows are not automatically owned. Delete triggers, incomplete visibility and unsupported/cyclic references block cleanup. No target data is written by preview.");}
    }
    private final GenerationBatchStore store;
    BatchCleanupChecks(GenerationBatchStore store){this.store=store;}

    @SuppressWarnings("unchecked") Check inspect(Connection connection,String id,boolean lock) throws SQLException {
        Check check=new Check();Map<String,Object> batch=store.get(id);Map<String,Object> identity=(Map<String,Object>)batch.get("databaseIdentity");
        check.report.put("databaseIdentity",identity);
        if(!Arrays.asList(lock?"CLEANING":"COMMITTED").contains(batch.get("state"))){check.block("BATCH_NOT_COMMITTED",null);check.finish();return check;}
        if(!Objects.equals(identity.get("fingerprint"),GenerationBatchStore.identity(connection).get("fingerprint"))){check.block("DATABASE_IDENTITY_MISMATCH",null);check.finish();return check;}
        List<Map<String,Object>> tables=(List<Map<String,Object>>)batch.get("tables");
        check.rowCount=((Number)batch.get("stagedRowCount")).intValue();
        if(tables.size()>100)check.block("CLEANUP_TABLE_LIMIT",null);
        if(check.rowCount>MAX_ROWS)check.block("CLEANUP_ROW_LIMIT",null);
        for(Map<String,Object> table:tables)if("UNTRACKED".equals(table.get("tracking")) && ((Number)table.get("stagedRows")).intValue()>0)check.block("UNTRACKED_WRITES",(String)table.get("table"));
        if(!check.blockers.isEmpty()){check.finish();return check;}
        MessageDigest manifest=digest();feed(manifest,batch.get("recipeHash"));feed(manifest,identity.get("fingerprint"));
        for(Map<String,Object> entry:tables) {
            String name=(String)entry.get("table");if(((Number)entry.get("stagedRows")).intValue()==0)continue;
            if(name.contains(".")){check.block("QUALIFIED_TARGET_CLEANUP_UNSUPPORTED",name);continue;}
            Owned owned=new Owned();owned.table=store.descriptor(id,name);check.owned.put(name,owned);
            TargetRowSnapshot current=current(connection,owned.table);
            if(current.unsupportedReason!=null || !Objects.equals(owned.table.schemaHash,current.schemaHash))check.block("SCHEMA_CHANGED",name);
            feed(manifest,name);feed(manifest,owned.table.schemaHash);feed(manifest,entry.get("stagedRows"));
        }
        if(!check.blockers.isEmpty()){check.finish();return check;}
        capabilities(connection,check);
        if(!check.blockers.isEmpty()){check.finish();return check;}
        int offset=0,seen=0;long keyCharacters=0;
        while(true) {
            Map<String,Object> page=store.rows(id,offset,200);List<Map<String,Object>> rows=(List<Map<String,Object>>)page.get("rows");
            if(rows.isEmpty())break;
            Map<String,List<String>> ownershipKeys=new LinkedHashMap<>();
            for(Map<String,Object> row:rows) {
                String table=(String)row.get("table");Owned owned=check.owned.get(table);if(owned==null){check.block("JOURNAL_INCONSISTENT",table);continue;}
                Map<String,String> key=(Map<String,String>)row.get("primaryKey");
                if(!key.keySet().equals(new HashSet<>(owned.table.primaryKeys)) || key.values().stream().anyMatch(Objects::isNull)){check.block("JOURNAL_INCONSISTENT",table);continue;}
                String encoded=TargetRowSnapshot.json(key),fingerprint=(String)row.get("fingerprint");
                keyCharacters+=encoded.length()+fingerprint.length();for(String value:key.values())keyCharacters+=value.length();
                if(encoded.length()>8192 || keyCharacters>16777216){check.block("CLEANUP_KEY_BUDGET",table);break;}
                if(owned.fingerprints.put(encoded,fingerprint)!=null)check.block("DUPLICATE_JOURNAL_KEY",table);
                owned.keys.add(key);ownershipKeys.computeIfAbsent(table,k->new ArrayList<>()).add(encoded);feed(manifest,table);feed(manifest,encoded);feed(manifest,fingerprint);
            }
            for(Map.Entry<String,List<String>> keys:ownershipKeys.entrySet())if(store.hasOtherOwners(id,identity,check.owned.get(keys.getKey()).table.physicalName(),keys.getValue()))check.block("SHARED_BATCH_OWNERSHIP",keys.getKey());
            seen+=rows.size();if(seen>MAX_ROWS){check.block("CLEANUP_ROW_LIMIT",null);break;}
            if(!check.blockers.isEmpty())break;
            if(!Boolean.TRUE.equals(page.get("truncated")))break;offset=((Number)page.get("nextOffset")).intValue();
        }
        if(seen!=check.rowCount)check.block("JOURNAL_ROW_COUNT_MISMATCH",null);
        for(Map<String,Object> table:tables)if(check.owned.containsKey(table.get("table")) && check.owned.get(table.get("table")).keys.size()!=((Number)table.get("stagedRows")).intValue())check.block("JOURNAL_ROW_COUNT_MISMATCH",(String)table.get("table"));
        check.manifest=TargetRowSnapshot.hex(manifest.digest());
        if(!check.blockers.isEmpty()){check.finish();return check;}
        for(Map.Entry<String,Owned> entry:check.owned.entrySet()) {
            Owned owned=entry.getValue();List<TargetRowSnapshot.Row> actual=owned.table.read(connection,owned.keys,lock);int matched=0;
            for(TargetRowSnapshot.Row row:actual)if(Objects.equals(owned.fingerprints.get(TargetRowSnapshot.json(row.primaryKey)),row.fingerprint))matched++;
            if(actual.size()!=owned.keys.size() || matched!=owned.keys.size())check.block("ROW_CHANGED_OR_MISSING",entry.getKey());
            if(lock && !Objects.equals(owned.table.schemaHash,current(connection,owned.table).schemaHash))check.block("SCHEMA_CHANGED",entry.getKey());
        }
        if(!check.blockers.isEmpty()){check.finish();return check;}
        foreignKeys(connection,check,lock);
        if(lock && check.blockers.isEmpty())capabilities(connection,check);
        check.finish();return check;
    }

    private static TargetRowSnapshot current(Connection connection,TargetRowSnapshot old) throws SQLException {
        TableVO table=new TableVO();table.setName(old.table);List<ColumnInfo> columns=new ArrayList<>();for(TargetRowSnapshot.Column actual:old.columns){ColumnInfo col=new ColumnInfo();col.setName(actual.name);columns.add(col);}table.setColumns(columns);return TargetRowSnapshot.inspect(connection,table);
    }

    private void capabilities(Connection connection,Check check) throws SQLException {
        DatabaseMetaData meta=connection.getMetaData();String product=meta.getDatabaseProductName().toLowerCase(Locale.ROOT);
        if(!meta.supportsTransactions() || !meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE)){check.block("TRANSACTIONS_REQUIRED",null);return;}
        if(product.contains("mysql")) {
            List<String> grants=new ArrayList<>();try(Statement statement=connection.createStatement()){statement.setQueryTimeout(5);try(ResultSet rows=statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")){while(rows.next())grants.add(rows.getString(1));}}
            boolean globalSelect=false;Set<String> triggerScopes=new HashSet<>();boolean revoked=false;
            for(String grant:grants) {
                String upper=grant.toUpperCase(Locale.ROOT);if(upper.startsWith("REVOKE "))revoked=true;
                java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?i)^GRANT (.+?) ON (.+?) TO ").matcher(grant);
                if(!matcher.find())continue;String privileges=matcher.group(1).toUpperCase(Locale.ROOT),scope=matcher.group(2);Set<String> names=new HashSet<>(Arrays.asList(privileges.split(",\\s*")));
                boolean all=names.contains("ALL PRIVILEGES");if("*.*".equals(scope) && (all || names.contains("SELECT")))globalSelect=true;
                if(all || names.contains("TRIGGER"))triggerScopes.add(scope);
            }
            if(!globalSelect || revoked){check.block("METADATA_VISIBILITY_UNPROVEN",null);return;}
            for(Owned owned:check.owned.values()) {
                TargetRowSnapshot table=owned.table;String scope="`"+table.catalog.replace("`","``")+"`.*";
                if(!triggerScopes.contains("*.*") && !triggerScopes.contains(scope)){check.block("TRIGGER_VISIBILITY_UNPROVEN",table.table);continue;}
                try(PreparedStatement statement=connection.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
                    statement.setString(1,table.catalog);statement.setString(2,table.name);statement.setQueryTimeout(5);try(ResultSet rows=statement.executeQuery()){if(!rows.next() || !"InnoDB".equalsIgnoreCase(rows.getString(1)))check.block("NON_TRANSACTIONAL_TABLE",table.table);}
                }
                if(count(connection,"SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA=? AND EVENT_OBJECT_TABLE=? AND EVENT_MANIPULATION='DELETE'",table.catalog,table.name)>0)check.block("DELETE_TRIGGER_PRESENT",table.table);
            }
        } else if(product.equals("h2")) {
            try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("SELECT ADMIN FROM INFORMATION_SCHEMA.USERS WHERE UPPER(NAME)=UPPER(CURRENT_USER())")) {
                if(!rows.next() || !rows.getBoolean(1) || rows.next()){check.block("METADATA_VISIBILITY_UNPROVEN",null);return;}
            }
            for(Owned owned:check.owned.values())if(count(connection,"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TRIGGERS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND TRIGGER_TYPE LIKE '%DELETE%'",owned.table.schema,owned.table.name)>0)check.block("DELETE_TRIGGER_PRESENT",owned.table.table);
        } else check.block("DATABASE_CLEANUP_NOT_VERIFIED",null);
    }

    private static final class Foreign {
        String parent,childCatalog,childSchema,childName,name;TreeMap<Integer,String[]> columns=new TreeMap<>();int deleteRule,updateRule;
        String physical(){return TargetRowSnapshot.json(Arrays.asList(childCatalog,childSchema,childName));}
    }
    private void foreignKeys(Connection connection,Check check,boolean lock) throws SQLException {
        List<Foreign> foreigns=new ArrayList<>();Map<String,String> byPhysical=new HashMap<>();for(Map.Entry<String,Owned> owned:check.owned.entrySet())byPhysical.put(owned.getValue().table.physicalName(),owned.getKey());
        for(Map.Entry<String,Owned> owned:check.owned.entrySet()) {
            TargetRowSnapshot parent=owned.getValue().table;Map<String,Foreign> grouped=new TreeMap<>();int count=0;
            try(ResultSet rows=connection.getMetaData().getExportedKeys(parent.catalog,parent.schema,parent.name)) {
                while(rows.next()) {
                    if(++count>500){check.block("FOREIGN_KEY_METADATA_LIMIT",parent.table);break;}
                    String name=rows.getString("FK_NAME");if(name==null){check.block("UNNAMED_FOREIGN_KEY",parent.table);continue;}
                    String catalog=rows.getString("FKTABLE_CAT"),schema=rows.getString("FKTABLE_SCHEM"),table=rows.getString("FKTABLE_NAME");String key=TargetRowSnapshot.json(Arrays.asList(catalog,schema,table,name));
                    Foreign fk=grouped.computeIfAbsent(key,k->{Foreign f=new Foreign();f.parent=owned.getKey();f.childCatalog=catalog;f.childSchema=schema;f.childName=table;f.name=name;return f;});
                    if(fk.columns.put(rows.getInt("KEY_SEQ"),new String[]{rows.getString("PKCOLUMN_NAME"),rows.getString("FKCOLUMN_NAME")})!=null)check.block("AMBIGUOUS_FOREIGN_KEY_METADATA",parent.table);
                    fk.deleteRule=rows.getInt("DELETE_RULE");fk.updateRule=rows.getInt("UPDATE_RULE");
                }
            }
            foreigns.addAll(grouped.values());
        }
        foreigns.sort(Comparator.comparing(f->f.parent+"\n"+f.physical()+"\n"+f.name));MessageDigest digest=digest();Map<String,Set<String>> edges=new LinkedHashMap<>();for(String name:check.owned.keySet())edges.put(name,new LinkedHashSet<>());
        int scanned=0;
        for(Foreign fk:foreigns) {
            Owned parent=check.owned.get(fk.parent);TargetRowSnapshot p=parent.table;
            feed(digest,fk.parent);feed(digest,fk.physical());feed(digest,fk.name);feed(digest,fk.columns);feed(digest,fk.deleteRule);feed(digest,fk.updateRule);
            if(!Objects.equals(p.catalog,fk.childCatalog) || !Objects.equals(p.schema,fk.childSchema)){check.block("CROSS_SCOPE_REFERENCE",p.table);continue;}
            Set<String> referenced=new HashSet<>();for(String[] pair:fk.columns.values())referenced.add(pair[0]);
            if(fk.columns.isEmpty() || fk.columns.size()!=p.primaryKeys.size() || !referenced.equals(new HashSet<>(p.primaryKeys))){check.block("NON_PRIMARY_KEY_FOREIGN_REFERENCE",p.table);continue;}
            String childAlias=byPhysical.get(fk.physical());Owned child=childAlias==null?null:check.owned.get(childAlias);boolean hasRows=false;
            int chunk=Math.max(1,Math.min(100,500/fk.columns.size()));
            for(int offset=0;offset<parent.keys.size();offset+=chunk) {
                List<Map<String,String>> keys=parent.keys.subList(offset,Math.min(parent.keys.size(),offset+chunk));StringJoiner where=new StringJoiner(" OR ");
                for(Map<String,String> ignored:keys){StringJoiner terms=new StringJoiner(" AND ");for(String[] pair:fk.columns.values())terms.add(p.quoted(pair[1])+"=?");where.add("("+terms+")");}
                String projection="1";if(child!=null){StringJoiner names=new StringJoiner(",");for(String key:child.table.primaryKeys)names.add(p.quoted(key));projection=names.toString();}
                String prefix=fk.childSchema==null?fk.childCatalog:fk.childSchema;String qualified=(prefix==null?"":p.quoted(prefix)+".")+p.quoted(fk.childName);
                try(PreparedStatement statement=connection.prepareStatement("SELECT "+projection+" FROM "+qualified+" WHERE "+where+(lock?" FOR UPDATE":""))) {
                    int parameter=1;for(Map<String,String> key:keys)for(String[] pair:fk.columns.values())statement.setString(parameter++,key.get(pair[0]));statement.setQueryTimeout(10);statement.setMaxRows(MAX_ROWS+1);statement.setFetchSize(100);
                    try(ResultSet rows=statement.executeQuery()) {
                        while(rows.next()) {
                            if(++scanned>MAX_ROWS){check.block("REFERENCE_SCAN_LIMIT",p.table);break;}
                            hasRows=true;
                            if(child==null || !child.fingerprints.containsKey(TargetRowSnapshot.json(child.table.key(rows)))){check.block("EXTERNAL_REFERENCE",p.table);break;}
                        }
                    }
                }
                if(!check.blockers.isEmpty())break;
            }
            if(childAlias!=null && hasRows)edges.get(childAlias).add(fk.parent);
        }
        check.foreignHash=TargetRowSnapshot.hex(digest.digest());
        Map<String,Integer> degrees=new LinkedHashMap<>();for(String name:edges.keySet())degrees.put(name,0);for(Set<String> parents:edges.values())for(String name:parents)degrees.put(name,degrees.get(name)+1);
        PriorityQueue<String> leaves=new PriorityQueue<>();degrees.forEach((name,degree)->{if(degree==0)leaves.add(name);});
        while(!leaves.isEmpty()){String child=leaves.remove();check.order.add(child);for(String parent:edges.get(child)){int degree=degrees.compute(parent,(k,v)->v-1);if(degree==0)leaves.add(parent);}}
        if(check.order.size()!=check.owned.size())check.block("CYCLIC_OWNERSHIP_GRAPH",null);
    }
    private static int count(Connection connection,String sql,String a,String b) throws SQLException {try(PreparedStatement statement=connection.prepareStatement(sql)){statement.setString(1,a);statement.setString(2,b);statement.setQueryTimeout(5);try(ResultSet rows=statement.executeQuery()){rows.next();return rows.getInt(1);}}}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void feed(MessageDigest digest,Object value){byte[] bytes=TargetRowSnapshot.json(value).getBytes(StandardCharsets.UTF_8);digest.update((byte)(bytes.length>>>24));digest.update((byte)(bytes.length>>>16));digest.update((byte)(bytes.length>>>8));digest.update((byte)bytes.length);digest.update(bytes);}
    private static Map<String,Object> map(Object...pairs){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
}
