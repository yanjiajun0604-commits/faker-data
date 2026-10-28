package faker.data.controller;

import faker.data.api.R;
import faker.data.batch.GenerationBatchCleanup;
import faker.data.domain.BatchCleanupRequest;
import faker.data.service.db.DatabaseConnectionException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("batches/{batch}/cleanup")
public class GenerationCleanupController {
    private final GenerationBatchCleanup cleanup;
    public GenerationCleanupController(GenerationBatchCleanup cleanup){this.cleanup=cleanup;}
    @PostMapping("preview") public R preview(@PathVariable String batch,@RequestBody BatchCleanupRequest request){return R.ok().put("data",cleanup.preview(batch,request));}
    @PostMapping("execute") public R execute(@PathVariable String batch,@RequestBody BatchCleanupRequest request){return R.ok().put("data",cleanup.execute(batch,request));}
    @GetMapping("plans/{id}") public R get(@PathVariable String batch,@PathVariable String id){return R.ok().put("data",cleanup.get(batch,id));}
    @GetMapping("plans/{id}/rows") public R rows(@PathVariable String batch,@PathVariable String id,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="100") int limit){return R.ok().put("data",cleanup.rows(batch,id,offset,limit));}
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) public R invalid(RuntimeException e){return R.error(e.getMessage()).put("error","INVALID_CLEANUP_REQUEST");}
    @ExceptionHandler(DatabaseConnectionException.class) public R database(DatabaseConnectionException e){return R.error(e.getMessage()).put("error",e.diagnostics());}
}
