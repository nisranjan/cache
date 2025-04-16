package nisran.config;

import nisran.cache.LRUCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {
    
    @Value("${cache.capacity:100}")
    private int cacheCapacity;
    
    @Bean
    public LRUCache<String, Object> lruCache() {
        return new LRUCache<>(cacheCapacity);
    }
} 