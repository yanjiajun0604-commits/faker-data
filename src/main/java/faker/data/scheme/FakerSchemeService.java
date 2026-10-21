package faker.data.scheme;

import com.alibaba.fastjson.JSON;
import faker.data.domain.DataSource;
import faker.data.domain.TableVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class FakerSchemeService {

    @Autowired
    private FakerSchemeRepository repository;

    public FakerScheme save(String name, DataSource dataSource, List<TableVO> tables) {
        // 同名方案已存在 → 更新记录（保留 ID 和 createdAt）
        Optional<FakerScheme> existing = repository.findByName(name);
        if (existing.isPresent()) {
            FakerScheme scheme = existing.get();
            scheme.setDataSourceJson(JSON.toJSONString(dataSource));
            scheme.setTablesJson(JSON.toJSONString(tables));
            scheme.setUpdatedAt(new Date());
            return repository.save(scheme);
        }
        // 否则新增
        FakerScheme scheme = new FakerScheme();
        scheme.setName(name);
        scheme.setDataSourceJson(JSON.toJSONString(dataSource));
        scheme.setTablesJson(JSON.toJSONString(tables));
        Date now = new Date();
        scheme.setCreatedAt(now);
        scheme.setUpdatedAt(now);
        return repository.save(scheme);
    }

    public List<FakerScheme> listAll() {
        return repository.findAll();
    }

    public FakerScheme getById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
