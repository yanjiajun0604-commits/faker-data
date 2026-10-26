package faker.data.task;
import faker.data.api.R;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson.JSONObject;
@RestController
public class TaskController {
    private final LocalTaskService tasks;
    public TaskController(LocalTaskService tasks){this.tasks=tasks;}
    @PostMapping("publish/task/{type}") public R submit(@PathVariable String type,@RequestBody JSONObject args,@RequestHeader(value="Idempotency-Key",required=false) String key){
        try{
            if(key!=null){if(args.containsKey("requestId") && !key.equals(args.get("requestId")))throw new IllegalArgumentException("Conflicting requestId and Idempotency-Key");args.put("requestId",key);}
            String id=tasks.submit(type,args);return R.ok().put("data",id).put("requestId",args.get("requestId"));
        }
        catch(IllegalArgumentException|IllegalStateException ex){return R.error(ex.getMessage());}
    }
    @GetMapping("generation/requests/{id}") public R request(@PathVariable String id){try{return R.ok().put("data",tasks.findRequest(id));}catch(IllegalArgumentException error){return R.error(error.getMessage());}}
    @GetMapping("asyncTask/queryProcess/{id}") public R query(@PathVariable String id){return R.ok().put("data",tasks.query(id));}
    @GetMapping("asyncTask/list") public R list(){return R.ok().put("data",tasks.types());}
}
