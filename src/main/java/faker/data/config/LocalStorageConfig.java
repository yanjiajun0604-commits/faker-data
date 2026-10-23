package faker.data.config;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.nio.channels.*;
import java.nio.file.*;
import java.io.IOException;

@Configuration
public class LocalStorageConfig {
    private FileChannel channel;
    private FileLock lock;
    @Bean public DataSource dataSource(@Value("${faker.data-dir:./data}") String directory) throws IOException {
        Path dir=Paths.get(directory).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        channel=FileChannel.open(dir.resolve("application.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        lock=channel.tryLock();
        if(lock==null) throw new IllegalStateException("数据目录正被另一个实例使用："+dir);
        SQLiteConfig config=new SQLiteConfig();
        config.setBusyTimeout(30000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        SQLiteDataSource ds=new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:"+dir.resolve("faker-data.db"));
        JdbcTemplate jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_scheme(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,data_source_json TEXT,tables_json TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS faker_task(id TEXT PRIMARY KEY,type TEXT NOT NULL,state TEXT NOT NULL,progress REAL NOT NULL,result TEXT,error_msg TEXT,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        jdbc.update("UPDATE faker_task SET state='ERROR',error_msg='上次运行中断，请核对目标数据后重新提交',result='运行中断',updated_at=? WHERE state IN ('WAITING','PROCESSING','CANCELING')",System.currentTimeMillis());
        return ds;
    }
    @PreDestroy public void close() throws IOException {
        if(lock!=null && lock.isValid()) lock.release();
        if(channel!=null) channel.close();
    }
}
