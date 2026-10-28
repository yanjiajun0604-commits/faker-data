package faker.data.scheme;

import faker.data.api.R;
import faker.data.service.generate.engine.GenerationProblem;
import org.springframework.web.bind.annotation.*;

@RestController
public class PortableSchemeController {
    private final PortableSchemeService portable;
    public PortableSchemeController(PortableSchemeService portable){this.portable=portable;}
    @GetMapping("scheme/{id}/export") public R export(@PathVariable long id,@RequestParam(defaultValue="local") String connectionRef){return R.ok().put("data",portable.exportScheme(id,connectionRef));}
    @PostMapping("scheme/import") public R importing(@RequestBody PortableImportRequest request){return R.ok().put("data",portable.importScheme(request));}
    @ExceptionHandler(RuntimeException.class) public R invalid(RuntimeException error){String message=error.getMessage();return R.error(message!=null && message.startsWith("SCHEME_")?message:"SCHEME_RULES_INVALID").put("diagnostic",GenerationProblem.find(error));}
}
