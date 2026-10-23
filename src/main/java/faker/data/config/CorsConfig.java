package faker.data.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override public void addCorsMappings(CorsRegistry registry){
        registry.addMapping("/**").allowedOrigins("http://localhost:8080","http://127.0.0.1:8080")
            .allowedMethods("GET","POST","DELETE","OPTIONS").allowedHeaders("*").allowCredentials(false);
    }
}
