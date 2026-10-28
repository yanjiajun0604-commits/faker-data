package faker.data.scheme;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Repository
public class FakerSchemeRepository {
    private final JdbcTemplate jdbc;
    public FakerSchemeRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private final RowMapper<FakerScheme> mapper=(rs,n)->{
        FakerScheme s=new FakerScheme();s.setId(rs.getLong("id"));s.setName(rs.getString("name"));
        s.setDataSourceJson(rs.getString("data_source_json"));s.setTablesJson(rs.getString("tables_json"));
        s.setKind(rs.getString("scheme_kind"));s.setExtensionJson(rs.getString("extension_json"));
        s.setCreatedAt(new Date(rs.getLong("created_at")));s.setUpdatedAt(new Date(rs.getLong("updated_at")));return s;
    };
    @Transactional public FakerScheme upsert(String name,String source,String tables){
        return upsert(name,source,tables,"RULES",null);
    }
    @Transactional public FakerScheme upsert(String name,String source,String tables,String kind,String extension){
        return write(name,source,tables,kind,extension,true);
    }
    /** Portable import supplies its own complete replacement binding set. */
    @Transactional public FakerScheme replaceImported(String name,String source,String tables){
        return write(name,source,tables,"RULES",null,false);
    }
    private FakerScheme write(String name,String source,String tables,String kind,String extension,boolean preserveBindings){
        Long previousId=null;List<String> rebased=Collections.emptyList();
        if(preserveBindings && !jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name='faker_scheme_private_path'").isEmpty()){
            List<FakerScheme> previous=jdbc.query("SELECT * FROM faker_scheme WHERE name=?",mapper,name);
            if(!previous.isEmpty()){
                FakerScheme old=previous.get(0);previousId=old.getId();
                Map<String,String> expected=new LinkedHashMap<>();
                jdbc.query("SELECT pointer,value_hash FROM faker_scheme_private_path WHERE scheme_id=? ORDER BY pointer",rs->{expected.put(rs.getString("pointer"),rs.getString("value_hash"));},previousId);
                PrivateBindingPaths.requireCurrent(old.getTablesJson(),expected);
                rebased=PrivateBindingPaths.rebase(old.getTablesJson(),tables,new ArrayList<>(expected.keySet()));
            }
        }
        long now=System.currentTimeMillis();
        jdbc.update("INSERT INTO faker_scheme(name,data_source_json,tables_json,scheme_kind,extension_json,created_at,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(name) DO UPDATE SET data_source_json=excluded.data_source_json,tables_json=excluded.tables_json,scheme_kind=excluded.scheme_kind,extension_json=excluded.extension_json,updated_at=excluded.updated_at",name,source,tables,kind,extension,now,now);
        if(previousId!=null){Map<String,String> hashes=PrivateBindingPaths.fingerprints(tables,rebased);jdbc.update("DELETE FROM faker_scheme_private_path WHERE scheme_id=?",previousId);for(String pointer:rebased)jdbc.update("INSERT INTO faker_scheme_private_path(scheme_id,pointer,value_hash) VALUES(?,?,?)",previousId,pointer,hashes.get(pointer));}
        return jdbc.queryForObject("SELECT * FROM faker_scheme WHERE name=?",mapper,name);
    }
    @Transactional public FakerScheme insertNew(String name,String source,String tables){
        long now=System.currentTimeMillis();
        try{jdbc.update("INSERT INTO faker_scheme(name,data_source_json,tables_json,scheme_kind,created_at,updated_at) VALUES(?,?,?,'RULES',?,?)",name,source,tables,now,now);}
        catch(org.springframework.dao.DataAccessException conflict){if(!jdbc.queryForList("SELECT 1 FROM faker_scheme WHERE name=?",name).isEmpty())throw new IllegalArgumentException("SCHEME_NAME_CONFLICT");throw conflict;}
        return jdbc.queryForObject("SELECT * FROM faker_scheme WHERE name=?",mapper,name);
    }
    public List<FakerScheme> findAll(){return jdbc.query("SELECT id,name,data_source_json,tables_json,scheme_kind,NULL AS extension_json,created_at,updated_at FROM faker_scheme WHERE scheme_kind='RULES' ORDER BY updated_at DESC,id DESC",mapper);}
    public Optional<FakerScheme> findById(Long id){return jdbc.query("SELECT * FROM faker_scheme WHERE id=?",mapper,id).stream().findFirst();}
    @Transactional public void deleteById(Long id){if(!jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name='faker_scheme_private_path'").isEmpty())jdbc.update("DELETE FROM faker_scheme_private_path WHERE scheme_id=?",id);jdbc.update("DELETE FROM faker_scheme WHERE id=?",id);}
}
