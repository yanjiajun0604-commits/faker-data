package faker.data.controller;

import faker.data.api.R;
import faker.data.domain.GenerationPreviewRequest;
import faker.data.service.generate.engine.*;
import faker.data.service.db.DatabaseConnectionException;
import org.springframework.web.bind.annotation.*;

@RestController
public class GenerationPreviewController {
    private final GenerationPreview preview;
    public GenerationPreviewController(GenerationPreview preview){this.preview=preview;}
    @PostMapping("generation/preview") public R preview(@RequestBody GenerationPreviewRequest request){try{return R.ok().put("data",preview.preview(request));}catch(DatabaseConnectionException error){return R.error(error.getMessage()).put("error",error.diagnostics());}catch(RuntimeException error){return R.error("Preview failed; inspect the diagnostic and supported preview limits").put("error","INVALID_PREVIEW_REQUEST").put("diagnostic",GenerationProblem.find(error)).put("reason",error.getMessage()!=null && error.getMessage().matches("[A-Z_]+")?error.getMessage():"PREVIEW_FAILED");}}
}
