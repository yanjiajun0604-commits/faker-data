package faker.data.batch;

import java.sql.*;
import java.util.*;

/** 只有预先声明的关系及生成前键基线能够扩展清理范围。 */
final class DerivedOwnershipResolver {
    private final GenerationBatchStore store;
    DerivedOwnershipResolver(GenerationBatchStore store){this.store=store;}
    void expand(Connection connection,String batch,BatchCleanupChecks.Check check,Map<String,Object> identity,boolean lock) throws SQLException {
        check.declared=store.ownership().relations(batch);
        Map<String,String> aliases=new HashMap<>();Map<String,TargetRowSnapshot> graph=new HashMap<>();
        for(Map.Entry<String,BatchCleanupChecks.Owned> entry:check.owned.entrySet()){aliases.put(entry.getValue().table.physicalName(),entry.getKey());graph.put(entry.getValue().table.physicalName(),entry.getValue().table);}
        for(OwnershipJournal.Relation relation:check.declared){graph.put(relation.parent.physicalName(),relation.parent);graph.put(relation.child.physicalName(),relation.child);}
        long budget=0;for(BatchCleanupChecks.Owned owned:check.owned.values())for(Map<String,String> key:owned.keys)budget+=cost(key);
        for(OwnershipJournal.Relation relation:ordered(check.declared)) {
            String parentAlias=aliases.get(relation.parentPhysical);if(parentAlias==null)continue;
            BatchCleanupChecks.Owned parent=check.owned.get(parentAlias);TargetRowSnapshot fresh=BatchCleanupChecks.current(connection,relation.child);
            if(fresh.unsupportedReason!=null || !Objects.equals(fresh.schemaHash,relation.child.schemaHash)){check.block("SCHEMA_CHANGED",relation.child.table);return;}
            List<OwnershipJournal.Candidate> candidates;
            try{candidates=OwnershipJournal.candidates(connection,relation,parent.keys,lock);}catch(IllegalArgumentException failure){check.block("DERIVED_SCAN_LIMIT_OR_INVALID",relation.child.table);return;}
            candidates.sort(Comparator.comparing(candidate->TargetRowSnapshot.json(candidate.key)));
            if(lock && !Objects.equals(BatchCleanupChecks.current(connection,relation.child).schemaHash,relation.child.schemaHash)){check.block("SCHEMA_CHANGED",relation.child.table);return;}
            String alias=aliases.get(relation.child.physicalName());BatchCleanupChecks.Owned child=alias==null?null:check.owned.get(alias);List<Map<String,String>> added=new ArrayList<>();
            Set<String> parentKeys=new HashSet<>();for(Map<String,String> key:parent.keys)parentKeys.add(TargetRowSnapshot.json(key));
            for(OwnershipJournal.Candidate candidate:candidates){
                String encoded=TargetRowSnapshot.json(candidate.key);
                if(!parentKeys.contains(TargetRowSnapshot.json(candidate.parent))){check.block("DERIVED_PARENT_NOT_OWNED",relation.child.table);return;}
                if(child!=null && child.fingerprints.containsKey(encoded))continue;
                if(!store.ownership().fresh(batch,relation,candidate.key)){check.block("PREEXISTING_DERIVED_REFERENCE",relation.child.table);return;}
                if(++check.rowCount>BatchCleanupChecks.MAX_ROWS || (budget+=cost(candidate.key))>16777216 || encoded.length()>8192){check.block("CLEANUP_KEY_OR_ROW_BUDGET",relation.child.table);return;}
                added.add(candidate.key);
            }
            if(store.hasOtherOwners(batch,identity,relation.child.physicalName(),encoded(added))){check.block("SHARED_BATCH_OWNERSHIP",relation.child.table);return;}
            if(!candidates.isEmpty()){
                if(child==null){child=new BatchCleanupChecks.Owned();child.table=relation.child;alias=relation.child.table;check.owned.put(alias,child);aliases.put(relation.child.physicalName(),alias);}
                List<TargetRowSnapshot.Row> actual=relation.child.read(connection,added,lock);
                if(actual.size()!=added.size()){check.block("DERIVED_ROW_CHANGED_OR_MISSING",alias);return;}
                Map<String,TargetRowSnapshot.Row> rows=new HashMap<>();for(TargetRowSnapshot.Row row:actual)rows.put(TargetRowSnapshot.json(row.primaryKey),row);
                for(Map<String,String> key:added){String encoded=TargetRowSnapshot.json(key);TargetRowSnapshot.Row row=rows.get(encoded);if(row==null){check.block("DERIVED_ROW_CHANGED_OR_MISSING",alias);return;}child.keys.add(key);child.fingerprints.put(encoded,row.fingerprint);child.origins.put(encoded,"DERIVED");child.relations.put(encoded,relation.id);check.derivedRowCount++;}
                check.declaredEdges.computeIfAbsent(alias,k->new LinkedHashSet<>()).add(parentAlias);
            }
        }
        if(check.owned.size()>100){check.block("CLEANUP_TABLE_LIMIT",null);return;}
        for(OwnershipJournal.Relation relation:check.declared)if(!relation.where.isEmpty()){
            BatchCleanupChecks.Owned parent=check.owned.get(aliases.get(relation.parentPhysical)),child=check.owned.get(aliases.get(relation.child.physicalName()));if(parent==null)continue;
            try{for(OwnershipJournal.Candidate candidate:OwnershipJournal.candidates(connection,relation,parent.keys,lock,true))if(child==null || !child.fingerprints.containsKey(TargetRowSnapshot.json(candidate.key))){check.block("EXCLUDED_DERIVED_REFERENCE",relation.child.table);return;}}
            catch(IllegalArgumentException failure){check.block("DERIVED_SCAN_LIMIT_OR_INVALID",relation.child.table);return;}
        }
        for(BatchCleanupChecks.Owned owned:check.owned.values())if(owned.origins.containsValue("DERIVED"))outbound(connection,owned,check,graph,aliases,lock);
    }
    private static List<OwnershipJournal.Relation> ordered(List<OwnershipJournal.Relation> relations){
        List<OwnershipJournal.Relation> result=new ArrayList<>(),pending=new ArrayList<>(relations);Set<String> children=new HashSet<>(),done=new HashSet<>();for(OwnershipJournal.Relation r:relations)children.add(r.child.physicalName());
        while(!pending.isEmpty()){boolean changed=false;Iterator<OwnershipJournal.Relation> iterator=pending.iterator();while(iterator.hasNext()){OwnershipJournal.Relation r=iterator.next();if(!children.contains(r.parentPhysical) || done.contains(r.parentPhysical)){result.add(r);done.add(r.child.physicalName());iterator.remove();changed=true;}}if(!changed)throw new IllegalStateException("OWNERSHIP_CYCLIC_GRAPH");}return result;
    }
    private static final class Foreign {String parentPhysical;TreeMap<Integer,String[]> pairs=new TreeMap<>();}
    private static void outbound(Connection connection,BatchCleanupChecks.Owned child,BatchCleanupChecks.Check check,Map<String,TargetRowSnapshot> graph,Map<String,String> aliases,boolean lock) throws SQLException {
        Map<String,Foreign> foreigns=new TreeMap<>();int count=0;
        try(ResultSet rows=connection.getMetaData().getImportedKeys(child.table.catalog,child.table.schema,child.table.name)){
            while(rows.next()){
                if(++count>500){check.block("FOREIGN_KEY_METADATA_LIMIT",child.table.table);return;}
                String physical=TargetRowSnapshot.json(Arrays.asList(rows.getString("PKTABLE_CAT"),rows.getString("PKTABLE_SCHEM"),rows.getString("PKTABLE_NAME")));if(!graph.containsKey(physical))continue;
                String name=rows.getString("FK_NAME");if(name==null){check.block("UNNAMED_FOREIGN_KEY",child.table.table);return;}
                Foreign foreign=foreigns.computeIfAbsent(physical+name,k->new Foreign());foreign.parentPhysical=physical;
                if(foreign.pairs.put(rows.getInt("KEY_SEQ"),new String[]{rows.getString("FKCOLUMN_NAME"),rows.getString("PKCOLUMN_NAME")})!=null){check.block("AMBIGUOUS_FOREIGN_KEY_METADATA",child.table.table);return;}
            }
        }
        for(Foreign foreign:foreigns.values()){
            TargetRowSnapshot parent=graph.get(foreign.parentPhysical);Set<String> fields=new HashSet<>();for(String[] pair:foreign.pairs.values())fields.add(pair[1]);
            if(!fields.equals(new HashSet<>(parent.primaryKeys)) || fields.size()!=foreign.pairs.size()){check.block("NON_PRIMARY_KEY_FOREIGN_REFERENCE",child.table.table);return;}
            BatchCleanupChecks.Owned parentOwned=check.owned.get(aliases.get(foreign.parentPhysical));
            StringJoiner projection=new StringJoiner(","),where=new StringJoiner(" AND ");for(String[] pair:foreign.pairs.values())projection.add(child.table.quoted(pair[0]));for(String key:child.table.primaryKeys)where.add(child.table.quoted(key)+"=?");
            try(PreparedStatement statement=connection.prepareStatement("SELECT "+projection+" FROM "+child.table.qualified()+" WHERE "+where+(lock?" FOR UPDATE":""))){
                statement.setQueryTimeout(10);
                for(Map<String,String> key:child.keys){if(!"DERIVED".equals(child.origins.get(TargetRowSnapshot.json(key))))continue;int p=1;for(String field:child.table.primaryKeys)statement.setString(p++,key.get(field));
                    try(ResultSet rows=statement.executeQuery()){if(!rows.next()){check.block("DERIVED_ROW_CHANGED_OR_MISSING",child.table.table);return;}Map<String,String> reference=new LinkedHashMap<>();boolean anyNull=false;
                        for(String pk:parent.primaryKeys)for(String[] pair:foreign.pairs.values())if(pair[1].equals(pk)){Object value=rows.getObject(pair[0]);if(value==null)anyNull=true;else reference.put(pk,parent.canonical(pk,value));}
                        if(!anyNull && (parentOwned==null || !parentOwned.fingerprints.containsKey(TargetRowSnapshot.json(reference)))){check.block("SHARED_DERIVED_REFERENCE",child.table.table);return;}
                    }
                }
            }
        }
    }
    private static long cost(Map<String,String> key){long size=TargetRowSnapshot.json(key).length()+128;for(String value:key.values())size+=value.length();return size;}
    private static List<String> encoded(List<Map<String,String>> keys){List<String> result=new ArrayList<>();for(Map<String,String> key:keys)result.add(TargetRowSnapshot.json(key));return result;}
}
