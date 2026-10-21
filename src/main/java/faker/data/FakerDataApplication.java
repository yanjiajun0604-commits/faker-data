package faker.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"pers.bbs.asynctask","faker.data"})
public class FakerDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(FakerDataApplication.class, args);
    }

}
