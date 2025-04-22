package nisran.discovery;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("single-server")
public class SingleServerConfig {
    // No service discovery configuration needed for single server mode
} 