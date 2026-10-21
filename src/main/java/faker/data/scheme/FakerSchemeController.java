package faker.data.scheme;

import faker.data.domain.DataSource;
import faker.data.domain.TableVO;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.bbs.common.R;

import java.util.List;

@RestController
@RequestMapping("scheme")
public class FakerSchemeController {

    @Autowired
    private FakerSchemeService service;

    @PostMapping("save")
    public R save(@RequestBody SaveSchemeRequest request) {
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            return R.error("方案名称不能为空");
        }
        FakerScheme scheme = service.save(request.getName().trim(),
                request.getDataSource(), request.getTables());
        return R.ok().put("data", scheme.getId());
    }

    @GetMapping("list")
    public R list() {
        return R.ok().put("data", service.listAll());
    }

    @GetMapping("{id}")
    public R get(@PathVariable Long id) {
        FakerScheme scheme = service.getById(id);
        if (scheme == null) {
            return R.error("方案不存在");
        }
        return R.ok().put("data", scheme);
    }

    @DeleteMapping("{id}")
    public R delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Data
    public static class SaveSchemeRequest {
        private String name;
        private DataSource dataSource;
        private List<TableVO> tables;
    }
}
