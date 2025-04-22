package nisran.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.*;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.Task;

import java.net.InetAddress;
import java.util.UUID;

@Configuration
@EnableScheduling
@Profile("cluster")
public class ServiceRegistration {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistration.class);

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${server.port}")
    private int port;

    private final ServiceDiscoveryClient serviceDiscoveryClient;
    private final EcsClient ecsClient;
    private String instanceId;
    private String privateIp;

    public ServiceRegistration(ServiceDiscoveryClient serviceDiscoveryClient, EcsClient ecsClient) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        this.ecsClient = ecsClient;
        this.instanceId = UUID.randomUUID().toString();
        this.privateIp = getPrivateIp();
        logger.debug("Initialized ServiceRegistration with instanceId: {} and privateIp: {}", instanceId, privateIp);
    }

    private String getPrivateIp() {
        try {
            // Try to get private IP from ECS task metadata
            String taskArn = System.getenv("ECS_TASK_ARN");
            if (taskArn != null) {
                logger.debug("Found ECS_TASK_ARN: {}", taskArn);
                DescribeTasksRequest request = DescribeTasksRequest.builder()
                    .cluster(System.getenv("ECS_CLUSTER"))
                    .tasks(taskArn)
                    .build();
                
                DescribeTasksResponse response = ecsClient.describeTasks(request);
                if (!response.tasks().isEmpty()) {
                    Task task = response.tasks().get(0);
                    String ip = task.attachments().stream()
                        .filter(att -> att.type().equals("ElasticNetworkInterface"))
                        .findFirst()
                        .map(att -> att.details().stream()
                            .filter(det -> det.name().equals("privateIPv4Address"))
                            .findFirst()
                            .map(det -> det.value())
                            .orElse(null))
                        .orElse(null);
                    if (ip != null) {
                        logger.debug("Retrieved private IP from ECS task metadata: {}", ip);
                        return ip;
                    }
                }
            }
            
            // Fallback to local network interface
            String localIp = InetAddress.getLocalHost().getHostAddress();
            logger.debug("Using fallback local IP: {}", localIp);
            return localIp;
        } catch (Exception e) {
            logger.error("Failed to get private IP", e);
            throw new RuntimeException("Failed to get private IP", e);
        }
    }

    @Bean
    public void registerService() {
        try {
            logger.info("Starting service registration for service: {}", serviceName);
            // Create service if it doesn't exist
            String serviceId = findOrCreateService();

            // Register instance
            RegisterInstanceRequest request = RegisterInstanceRequest.builder()
                    .serviceId(serviceId)
                    .instanceId(instanceId)
                    .attributes(java.util.Map.of(
                            "AWS_INSTANCE_IPV4", privateIp,
                            "AWS_INSTANCE_PORT", String.valueOf(port),
                            "ECS_TASK_ARN", System.getenv("ECS_TASK_ARN"),
                            "ECS_CLUSTER", System.getenv("ECS_CLUSTER")
                    ))
                    .build();

            serviceDiscoveryClient.registerInstance(request);
            logger.info("Successfully registered service instance with ID: {}", instanceId);
        } catch (Exception e) {
            logger.error("Failed to register service", e);
            throw new RuntimeException("Failed to register service", e);
        }
    }

    private String findOrCreateService() {
        try {
            logger.debug("Looking for existing service with name: {}", serviceName);
            // Try to find existing service
            ListServicesRequest listRequest = ListServicesRequest.builder()
                    .filters(ServiceFilter.builder()
                            .name("NAME")
                            .values(serviceName)
                            .build())
                    .build();

            ListServicesResponse listResponse = serviceDiscoveryClient.listServices(listRequest);
            if (!listResponse.services().isEmpty()) {
                String serviceId = listResponse.services().get(0).id();
                logger.debug("Found existing service with ID: {}", serviceId);
                return serviceId;
            }

            logger.info("Creating new service with name: {}", serviceName);
            // Create new service if not found
            CreateServiceRequest createRequest = CreateServiceRequest.builder()
                    .name(serviceName)
                    .description("Cache Service")
                    .dnsConfig(DnsConfig.builder()
                            .dnsRecords(DnsRecord.builder()
                                    .type("A")
                                    .ttl(60L)
                                    .build())
                            .build())
                    .build();

            CreateServiceResponse createResponse = serviceDiscoveryClient.createService(createRequest);
            String serviceId = createResponse.service().id();
            logger.info("Created new service with ID: {}", serviceId);
            return serviceId;
        } catch (Exception e) {
            logger.error("Failed to find or create service", e);
            throw new RuntimeException("Failed to find or create service", e);
        }
    }

    @Scheduled(fixedRateString = "${cache.service.discovery.heartbeat-interval}")
    public void sendHeartbeat() {
        try {
            logger.debug("Sending heartbeat for instance: {}", instanceId);
            // Send heartbeat to keep instance registered
            serviceDiscoveryClient.updateInstanceCustomHealthStatus(
                    UpdateInstanceCustomHealthStatusRequest.builder()
                            .serviceId(findOrCreateService())
                            .instanceId(instanceId)
                            .status(CustomHealthStatus.HEALTHY)
                            .build()
            );
            logger.debug("Heartbeat sent successfully for instance: {}", instanceId);
        } catch (Exception e) {
            logger.error("Failed to send heartbeat for instance: {}", instanceId, e);
        }
    }
} 