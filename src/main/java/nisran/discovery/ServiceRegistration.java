package nisran.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.DeregisterInstanceRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import nisran.config.AWS_SDKConfig;

@Component("serviceRegistration")
@EnableScheduling
@Profile("cluster")
public class ServiceRegistration {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistration.class);

    @Autowired
    private EcsMetadataService ecsMetadataService;
    
    @Autowired
    private EcsMetadata ecsMetadata;

    @Autowired
    private CloudMapManager cloudMapManager;

    private final ServiceDiscoveryClient serviceDiscoveryClient;
    private final AWS_SDKConfig awsConfig;

    public ServiceRegistration(ServiceDiscoveryClient serviceDiscoveryClient, 
                             AWS_SDKConfig awsConfig) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        this.awsConfig = awsConfig;
    }

    private void fetchEcsMetadata() {
        // Getting Task ARN and Cluster ARN from environment variables
        String taskArn = System.getenv("ECS_TASK_ARN");
        String clusterArn = System.getenv("ECS_CLUSTER");

        if (taskArn == null || clusterArn == null) {
            logger.debug("ECS_TASK_ARN or ECS_CLUSTER environment variables are not set. " +
                        "Attempting to retrieve from metadata endpoint.");
            
            try {
                EcsMetadata metadata = ecsMetadataService.fetchMetadata().orElseThrow(() ->
                    new IllegalStateException("Failed to retrieve ECS task metadata."));
                
                ecsMetadata.setTaskArn(metadata.getTaskArn());
                ecsMetadata.setClusterArn(metadata.getClusterArn());
                ecsMetadata.setIpAddress(metadata.getIpAddress());
                
                logger.info("Fetched task metadata - Task ARN: {}, Cluster ARN: {}, Task ID: {}",
                            ecsMetadata.getTaskArn(), ecsMetadata.getClusterArn(), ecsMetadata.getTaskId());
            } catch (Exception e) {
                logger.error("Failed to fetch task metadata", e);
                throw new RuntimeException("Failed to fetch task metadata", e);
            }
        } else {
            ecsMetadata.setTaskArn(taskArn);
            ecsMetadata.setClusterArn(clusterArn);
            logger.debug("Found taskId {} from Task ARN {}", ecsMetadata.getTaskId(), taskArn);
        }
    }

    @PostConstruct
    public void registerService() {

        logger.debug("ServiceRegistration registerService() called.");
        
        // Set basic configuration
        ecsMetadata.setNamespaceName(awsConfig.getNamespaceName());
        ecsMetadata.setServiceName(awsConfig.getServiceName());
        ecsMetadata.setPort(awsConfig.getPort());

        fetchEcsMetadata();

        
        try {
            logger.info("Starting service registration for service: {}", ecsMetadata.getServiceName());
            
            ecsMetadata.setNamespaceId(cloudMapManager.findOrCreateNamespace(ecsMetadata.getNamespaceName()));
            ecsMetadata.setServiceId(cloudMapManager.findOrCreateService(ecsMetadata.getServiceName(), ecsMetadata.getNamespaceId()));
            
            cloudMapManager.findOrCreateInstance(
                ecsMetadata.getServiceId(),
                ecsMetadata.getTaskId(),
                ecsMetadata.getIpAddress(),
                ecsMetadata.getPort(),
                ecsMetadata.getTaskArn()
            );
            
            // Start heartbeat service after successful registration
            // heartbeatService.start();
            logger.info("Service registration completed successfully.");
        } catch (Exception e) {
            logger.error("Failed to register service", e);
            throw new RuntimeException("Failed to register service", e);
        }
    }

    @PreDestroy
    public void unregisterService() {
        if (ecsMetadata.getTaskId() != null && ecsMetadata.getServiceId() != null) {
            logger.info("Deregistering service instance: {} in service: {}", 
                       ecsMetadata.getTaskId(), ecsMetadata.getServiceId());
            try {
                DeregisterInstanceRequest request = DeregisterInstanceRequest.builder()
                        .serviceId(ecsMetadata.getServiceId())
                        .instanceId(ecsMetadata.getTaskId())
                        .build();
                serviceDiscoveryClient.deregisterInstance(request);
                logger.info("Successfully deregistered service instance: {}", ecsMetadata.getServiceId());
            } catch (Exception e) {
                logger.error("Failed to deregister service instance: {}", ecsMetadata.getServiceId(), e);
            }
        } else {
            logger.warn("Cannot deregister service instance: taskId or serviceId is null");
        }
    }

    // Getters for backward compatibility
    public String getServiceId() {
        return ecsMetadata.getServiceId();
    }

    public String getAwsTaskId() {
        return ecsMetadata.getTaskId();
    }

    public String getIp() {
        return ecsMetadata.getIpAddress();
    }
}