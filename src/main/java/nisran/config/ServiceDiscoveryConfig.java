package nisran.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import nisran.cache.LRUCache;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClientBuilder;
import software.amazon.awssdk.services.ecs.EcsClientBuilder;

@Configuration
@Profile("cluster")
public class ServiceDiscoveryConfig {

    private final AWS_SDKConfig awsSdkConfig;

    public ServiceDiscoveryConfig(AWS_SDKConfig awsSdkConfig) {
        this.awsSdkConfig = awsSdkConfig;
    }

    @Bean
    public ServiceDiscoveryClient serviceDiscoveryClient() {
        ServiceDiscoveryClientBuilder builder = ServiceDiscoveryClient.builder();
        return builder
            .region(Region.AP_SOUTH_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    public EcsClient ecsClient() {
        EcsClientBuilder builder = EcsClient.builder();
        return builder
            .region(Region.AP_SOUTH_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    public LRUCache<String, Object> lruCache() {
        return new LRUCache<>(awsSdkConfig.getCacheCapacity());
    }

    @Bean
    public RestTemplate restTemplate() {
         return new RestTemplate();
    }
} 