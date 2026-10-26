package faker.data.controller;

import faker.data.api.R;
import faker.data.batch.GenerationBatchStore;
import faker.data.batch.GenerationBatchRecovery;
import faker.data.domain.BatchReconcileRequest;
import faker.data.service.db.DatabaseConnectionException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("batches")
public class GenerationBatchController {
    private final GenerationBatchStore store;
    private final GenerationBatchRecovery recovery;
    public GenerationBatchController(GenerationBatchStore store,GenerationBatchRecovery recovery){this.store=store;this.recovery=recovery;}
    @GetMapping("{id}") public R get(@PathVariable String id){return R.ok().put("data",store.get(id));}
    @GetMapping("{id}/rows") public R rows(@PathVariable String id,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="100") int limit){return R.ok().put("data",store.rows(id,offset,limit));}
    @PostMapping("{id}/reconcile") public R reconcile(@PathVariable String id,@RequestBody BatchReconcileRequest request){return R.ok().put("data",recovery.reconcile(id,request));}
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) public R invalid(RuntimeException error){return R.error(error.getMessage()).put("error","INVALID_BATCH_REQUEST");}
    @ExceptionHandler(DatabaseConnectionException.class) public R database(DatabaseConnectionException error){return R.error(error.getMessage()).put("error",error.diagnostics());}
}
