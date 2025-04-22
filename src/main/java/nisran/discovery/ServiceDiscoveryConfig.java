package nisran.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.regions.Region;

@Configuration
@Profile("!dev")
public class ServiceDiscoveryConfig {

    @Value("${spring.cloud.aws.region.static:ap-south-1}")
    private String region;

    @Bean
    public ServiceDiscoveryClient serviceDiscoveryClient() {
        return ServiceDiscoveryClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public EcsClient ecsClient() {
        return EcsClient.builder()
                .region(Region.of(region))
                .build();
    }
} 