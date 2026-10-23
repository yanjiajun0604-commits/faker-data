package faker.data.service.generate.executor;

import com.alibaba.fastjson.JSONObject;
import faker.data.domain.GenerateVO;
import faker.data.service.db.JdbcService;
import faker.data.service.db.JdbcServiceFactory;
import faker.data.service.generate.SqlErrorTranslator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import faker.data.task.AsyncTaskExecutor;
import faker.data.task.AsyncTaskMonitor;

@Slf4j
@Component
public class GenerateDataExecutor implements AsyncTaskExecutor {

    @Override
    public void execute(Object args, AsyncTaskMonitor monitor) {
        try {
            GenerateVO generateVO = ((JSONObject) args).toJavaObject(GenerateVO.class);
            JdbcService jdbcService = JdbcServiceFactory.getJdbcService(generateVO.getDataSource());
            jdbcService.generateData(generateVO.getTables(), monitor, this);
        } catch (Exception e) {
            // 把 JDBC/Spring 抛出的原始异常链翻译成用户友好的消息再抛出，
            // 避免前端拿到一长串 "PreparedStatementCallback; SQL [...]; nested exception is ..."
            SqlErrorTranslator.Translated t = SqlErrorTranslator.translate(e);
            log.error("数据生成失败：{}", t.title);
            throw new RuntimeException(t.title + " | " + t.suggestion, e);
        }
    }

    @Override
    public String getTaskPoolType() {
        return "GEN_DATA";
    }

    @Override
    public String getTitle() {
        return "生成测试数据";
    }
}