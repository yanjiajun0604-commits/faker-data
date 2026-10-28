package faker.data.controller;

import faker.data.api.R;
import faker.data.domain.GenerateVO;
import faker.data.service.generate.engine.GenerationValidator;
import faker.data.service.generate.engine.GenerationProblem;
import faker.data.service.db.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class GenerationValidationController {
    @PostMapping("generation/preflight")
    public R preflight(@RequestBody GenerateVO request) {
        try{return R.ok().put("data",JdbcServiceFactory.getJdbcService(request.getDataSource()).preflightGeneration(request.getTables()));}
        catch(DatabaseConnectionException e){return R.error(e.getMessage()).put("error",e.diagnostics());}
        catch(IllegalArgumentException|IllegalStateException e){return R.error(e.getMessage()).put("error","INVALID_PREFLIGHT_REQUEST").put("diagnostic",GenerationProblem.find(e));}
    }
    @PostMapping("generation/validate")
    public R validate(@RequestBody GenerateVO request) {
        try{return R.ok().put("data",GenerationValidator.validate(request.getTables()));}
        catch(RuntimeException e){return R.error(e.getMessage()==null?"Invalid generation configuration":e.getMessage()).put("diagnostic",GenerationProblem.find(e));}
    }
}
