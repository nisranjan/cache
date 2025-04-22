package nisran.discovery;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.ecs.EcsClient;

@Configuration
@Profile("cluster")
public class ServiceDiscoveryConfig {

    @Bean
    public ServiceDiscoveryClient serviceDiscoveryClient() {
        return ServiceDiscoveryClient.builder().build();
    }

    @Bean
    public EcsClient ecsClient() {
        return EcsClient.builder().build();
    }
} 