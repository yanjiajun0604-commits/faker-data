package faker.data.task;
import faker.data.api.R;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson.JSONObject;
@RestController
public class TaskController {
    private final LocalTaskService tasks;
    public TaskController(LocalTaskService tasks){this.tasks=tasks;}
    @PostMapping("publish/task/{type}") public R submit(@PathVariable String type,@RequestBody JSONObject args){
        try{return R.ok().put("data",tasks.submit(type,args));}
        catch(IllegalArgumentException|IllegalStateException ex){return R.error(ex.getMessage());}
    }
    @GetMapping("asyncTask/queryProcess/{id}") public R query(@PathVariable String id){return R.ok().put("data",tasks.query(id));}
    @GetMapping("asyncTask/list") public R list(){return R.ok().put("data",tasks.types());}
}
