package nisran.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import nisran.cache.LRUCache;

@Configuration
@Profile("single-server")
public class SingleServerConfig {
    
    @Value("${cache.capacity:100}")
    private int cacheCapacity;
    
    @Bean
    public LRUCache<String, Object> lruCache() {
        return new LRUCache<>(cacheCapacity);
    }
} 