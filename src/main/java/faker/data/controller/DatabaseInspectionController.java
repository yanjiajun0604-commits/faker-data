package faker.data.controller;

import faker.data.api.R;
import faker.data.domain.DatabaseInspectionRequest;
import faker.data.service.db.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("db")
public class DatabaseInspectionController {
    @PostMapping("inspect")
    public R inspect(@RequestBody DatabaseInspectionRequest request) {
        return R.ok().put("data",JdbcServiceFactory.getJdbcService(request.getDataSource()).inspectDatabase(request));
    }
    @PostMapping("readRows")
    public R rows(@RequestBody DatabaseInspectionRequest request) {
        return R.ok().put("data",JdbcServiceFactory.getJdbcService(request.getDataSource()).readRows(request));
    }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})
    public R invalid(RuntimeException failure) {
        return R.error(failure.getMessage()).put("error", "INVALID_INSPECTION_REQUEST");
    }
    @ExceptionHandler(DatabaseConnectionException.class)
    public R database(DatabaseConnectionException failure) {
        return R.error(failure.getMessage()).put("error",failure.diagnostics());
    }
}
