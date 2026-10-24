package faker.data.controller;

import faker.data.domain.DataSource;
import faker.data.service.db.JdbcService;
import faker.data.service.db.JdbcServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import faker.data.service.db.DatabaseConnectionException;

import faker.data.api.R;

@RestController
@RequestMapping("db")
public class DatabaseController {

    @ExceptionHandler(DatabaseConnectionException.class)
    public R connectionFailure(DatabaseConnectionException failure) {
        return R.error(failure.getMessage()).put("error", failure.diagnostics());
    }



    @RequestMapping("connect/test")
    public R connectTest(@RequestBody DataSource dataSource) {
        JdbcService jdbcService = JdbcServiceFactory.getJdbcService(dataSource);
        return jdbcService.test() ? R.ok() : R.error("无法连接目标数据库，请核对连接地址、账号、密码及访问权限");
    }

    @RequestMapping("showTables")
    public R showTables(@RequestBody DataSource dataSource, @RequestParam(defaultValue = "0") Integer withSub) {
        JdbcService jdbcService = JdbcServiceFactory.getJdbcService(dataSource);
        java.util.List<?> tables = jdbcService.listAllTables(withSub);
        return tables == null ? R.error("无法读取表列表，请重新检查目标数据库连接") : R.ok().put("data", tables);
    }

    @RequestMapping("showColumns/{tableName}")
    public R showColumns(@RequestBody DataSource dataSource, @PathVariable(value = "tableName") String tableName) {
        JdbcService jdbcService = JdbcServiceFactory.getJdbcService(dataSource);
        java.util.List<?> columns = jdbcService.listAllFields(tableName);
        return columns == null || columns.isEmpty()
                ? R.error("无法读取字段，请核对表名、数据库连接及读取权限") : R.ok().put("data", columns);
    }

}
