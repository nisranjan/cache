package nisran;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration;

@SpringBootApplication(exclude = {SqsAutoConfiguration.class})
public class CacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(CacheApplication.class, args);
    }
} 