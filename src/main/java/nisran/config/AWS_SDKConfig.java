package nisran.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import lombok.Getter;

@Configuration
@Profile("cluster")
public class AWS_SDKConfig {

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${server.port}")
    private int port;

    @Value("${aws.servicediscovery.namespace.name}")
    private String namespaceName;

    @Value("${aws.servicediscovery.namespace.vpc-id}")
    private String vpcId; // Required for creating Private DNS namespaces
    
    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    @Value("${cache.capacity:100}")
    private int cacheCapacity;

    @Value("${server.virtual-nodes:1}") // Default to 1 if not specified
    @Getter
    private int virtualNodes; 

    @Value("${service.rediscovery.interval:60}") // Default to 60 seconds if not specified
    @Getter
    private int discoveryIntervalSeconds; 

    @Value("${service.replication.factor:2}") // Default to 2 if not specified
    @Getter
    private int replicationFactor;

    @Value("${service.quorum.read:2}") // Default to 2 if not specified
    @Getter
    private int quorumRead;
    
    @Value("${service.quorum.write:2}") // Default to 2 if not specified
    @Getter 
    private int quorumWrite;

    public String getServiceName() {
        return serviceName;
    }

    public int getPort() {
        return port;
    }

    public String getNamespaceName() {
        return namespaceName;
    }

    public String getVpcId() {
        return vpcId;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public int getCacheCapacity() {
        return cacheCapacity;
    }

}
