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
        return repository.upsert(name,JSON.toJSONString(dataSource),JSON.toJSONString(tables));
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
