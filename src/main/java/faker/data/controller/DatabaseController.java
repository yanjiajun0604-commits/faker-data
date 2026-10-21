package faker.data.controller;

import faker.data.domain.DataSource;
import faker.data.service.db.JdbcService;
import faker.data.service.db.JdbcServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pers.bbs.asynctask.dispatcher.AsyncTaskManager;
import pers.bbs.common.R;

@RestController
@RequestMapping("db")
public class DatabaseController {

    @Autowired
    AsyncTaskManager asyncTaskManager;

    @RequestMapping("connect/test")
    public R connectTest(@RequestBody DataSource dataSource) {
        JdbcService jdbcService = JdbcServiceFactory.getJdbcService(dataSource);
        return jdbcService.test() ? R.ok() : R.error();
    }

    @RequestMapping("showTables")
    public R showTables(@RequestBody DataSource dataSource, Integer withSub) {
        JdbcService jdbcService = JdbcServiceFactory.getJdbcService(dataSource);
        return R.ok().put("data", jdbcService.listAllTables(withSub));
    }

    @RequestMapping("showColumns/{tableName}")
    public R showColumns(@RequestBody DataSource dataSource, @PathVariable(value = "tableName") String tableName) {
        JdbcService jdbcService = JdbcServiceFactory.getJdbcService(dataSource);
        return R.ok().put("data", jdbcService.listAllFields(tableName));
    }

}
