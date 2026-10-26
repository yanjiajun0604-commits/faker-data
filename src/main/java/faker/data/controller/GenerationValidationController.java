package faker.data.controller;

import faker.data.api.R;
import faker.data.domain.GenerateVO;
import faker.data.service.generate.engine.GenerationValidator;
import org.springframework.web.bind.annotation.*;

@RestController
public class GenerationValidationController {
    @PostMapping("generation/validate")
    public R validate(@RequestBody GenerateVO request) {
        try{return R.ok().put("data",GenerationValidator.validate(request.getTables()));}
        catch(RuntimeException e){return R.error(e.getMessage()==null?"Invalid generation configuration":e.getMessage());}
    }
}
