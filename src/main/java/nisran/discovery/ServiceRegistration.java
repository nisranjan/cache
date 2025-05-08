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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    @Value("${aws.servicediscovery.namespace.name}")
    private String namespaceName;

    @Value("${aws.servicediscovery.namespace.vpc-id}")
    private String vpcId; // Required for creating Private DNS namespaces

    private final ServiceDiscoveryClient serviceDiscoveryClient;
    private final EcsClient ecsClient;
    private String instanceId;
    private String privateIp;

    public ServiceRegistration(ServiceDiscoveryClient serviceDiscoveryClient, EcsClient ecsClient) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        this.ecsClient = ecsClient;
        this.instanceId = UUID.randomUUID().toString();
        this.privateIp = getPrivateIp();
        logger.debug("Initialized ServiceRegistration with instanceId: {}, privateIp: {}", instanceId, privateIp);
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
            // Ensure namespace exists
            String namespaceId = findOrCreateNamespace();

            // Create service if it doesn't exist
            String serviceId = findOrCreateService(namespaceId);

            // Build attributes map safely, handling potential nulls from environment variables
            Map<String, String> attributes = new HashMap<>();
            attributes.put("AWS_INSTANCE_IPV4", privateIp); // privateIp should be non-null due to constructor logic
            attributes.put("AWS_INSTANCE_PORT", String.valueOf(port));

            String taskArn = System.getenv("ECS_TASK_ARN");
            if (taskArn != null) {
                attributes.put("ECS_TASK_ARN", taskArn);
            }
            String clusterName = System.getenv("ECS_CLUSTER");
            if (clusterName != null) {
                attributes.put("ECS_CLUSTER", clusterName);
            }

            // Register instance
            RegisterInstanceRequest request = RegisterInstanceRequest.builder()
                    .serviceId(serviceId)
                    .instanceId(instanceId)
                    .attributes(attributes) // Use the safely built map
                    .build();

            serviceDiscoveryClient.registerInstance(request);
            logger.info("Successfully registered service instance with ID: {}", instanceId);
        } catch (Exception e) {
            logger.error("Failed to register service", e);
            throw new RuntimeException("Failed to register service", e);
        }
    }

    private String findOrCreateNamespace(){
        try {
            logger.debug("Looking for existing namespace with name: {}", namespaceName);
            // Try to find existing namespace
            ListNamespacesRequest listNamespacesRequest = ListNamespacesRequest.builder()
                    .filters(NamespaceFilter.builder()      
                            .name(NamespaceFilterName.NAME)
                            .values(namespaceName)
                            .condition(FilterCondition.EQ)
                            .build(),
                             NamespaceFilter.builder() // Filter by type as well
                            .name(NamespaceFilterName.TYPE)
                            .values(NamespaceType.DNS_PRIVATE.toString())
                            .condition(FilterCondition.EQ)
                            .build())
                    .build();

            ListNamespacesResponse listNamespacesResponse = serviceDiscoveryClient.listNamespaces(listNamespacesRequest);

            Optional<NamespaceSummary> existingNamespace = listNamespacesResponse.namespaces().stream().findFirst();

            if (existingNamespace.isPresent()) {
                String namespaceId = existingNamespace.get().id();
                logger.debug("Found existing namespace with ID: {}", namespaceId);
                return namespaceId;
            }

            logger.info("Creating new private DNS namespace with name: {} in VPC: {}", namespaceName, vpcId);
            CreatePrivateDnsNamespaceRequest createNamespaceRequest = CreatePrivateDnsNamespaceRequest.builder()
                    .name(namespaceName)
                    .vpc(vpcId)
                    .description("Namespace for " + serviceName + " discovery")
                    .build();
            // Note: Namespace creation is asynchronous. We might need to wait or handle the operation ID.
            // For simplicity here, we assume immediate availability for subsequent calls, which might not be robust.
            CreatePrivateDnsNamespaceResponse createNamespaceResponse = serviceDiscoveryClient.createPrivateDnsNamespace(createNamespaceRequest);
            // It's better to poll using the OperationId from createNamespaceResponse until the namespace is ready.
            // However, finding the namespace ID immediately after creation isn't directly available in the response.
            // We might need to list again or use GetOperation. For now, we'll re-list as a simple approach.
            logger.info("Namespace creation initiated (Operation ID: {}). Re-querying for ID.", createNamespaceResponse.operationId());
            // Re-query after a short delay or use GetOperation API for robustness
            Thread.sleep(5000); // Simple delay, replace with proper polling
            return findOrCreateNamespace(); // Recursive call to get the ID after creation attempt

        } catch (Exception e) {
            logger.error("Failed to find or create namespace '{}'", namespaceName, e);
            throw new RuntimeException("Failed to find or create namespace: " + namespaceName, e);
        }
    }

    private String findOrCreateService(String namespaceId){
        try {
            logger.debug("Looking for existing service with name: {}", serviceName);
            // Try to find existing service
            ListServicesRequest listRequest = ListServicesRequest.builder()
                    .filters(ServiceFilter.builder()
                    .name(ServiceFilterName.NAMESPACE_ID)
                    .values(namespaceId)
                    .condition(FilterCondition.EQ)
                    .build()) // Only filter by NAMESPACE_ID
                    .build();

            ListServicesResponse listResponse = serviceDiscoveryClient.listServices(listRequest);
            // Filter client-side as ListServices might not support filtering by service name directly
            Optional<ServiceSummary> existingService = listResponse.services().stream()
                    .filter(s -> s.name().equals(serviceName))
                    .findFirst();

            if (existingService.isPresent()) {
                String serviceId = existingService.get().id();
                logger.debug("Found existing service '{}' with ID: {}", serviceName, serviceId);
                return serviceId;
            }

            logger.info("Creating new service with name: {} in namespace: {}", serviceName, namespaceId);
            // Create new service if not found
            CreateServiceRequest createRequest = CreateServiceRequest.builder()
                    .name(serviceName)
                    .namespaceId(namespaceId) // Associate with the namespace
                    .description("Service for " + serviceName)
                    .dnsConfig(DnsConfig.builder()
                            .dnsRecords(DnsRecord.builder()
                                    .type(RecordType.A) // Use Enum RecordType.A
                                    .ttl(60L)
                                    .build())
                            .routingPolicy(RoutingPolicy.WEIGHTED) // Example: Use WEIGHTED or MULTIVALUE
                            .build())
                    // Add health check config if needed
                    // .healthCheckCustomConfig(HealthCheckCustomConfig.builder().failureThreshold(1).build()) // Example for custom health
                    .build();

            CreateServiceResponse createResponse = serviceDiscoveryClient.createService(createRequest);
            String serviceId = createResponse.service().id();
            logger.info("Created new service '{}' with ID: {}", serviceName, serviceId);
            return serviceId;
        } catch (Exception e) {
            logger.error("Failed to find or create service '{}'", serviceName, e);
            throw new RuntimeException("Failed to find or create service: " + serviceName, e);
        }
    }

    @Scheduled(fixedRateString = "${cache.service.discovery.heartbeat-interval}")
    public void sendHeartbeat() {
        try {
            // Need serviceId to update health status
            String namespaceId = findOrCreateNamespace(); // Ensure namespace exists
            String serviceId = findOrCreateService(namespaceId); // Ensure service exists and get ID

            logger.debug("Sending heartbeat for instance: {} in service: {}", instanceId, serviceId);
            // Send heartbeat to keep instance registered
            // Note: UpdateInstanceCustomHealthStatus is used when HealthCheckCustomConfig is enabled for the service.
            // If not using custom health checks, Cloud Map relies on the health of the underlying resource (e.g., ECS task).
            // If the service uses custom health checks, this call is necessary.
            serviceDiscoveryClient.updateInstanceCustomHealthStatus(
                    UpdateInstanceCustomHealthStatusRequest.builder()
                            .serviceId(serviceId)
                            .instanceId(instanceId)
                            .status(CustomHealthStatus.HEALTHY)
                            .build()
            );
            logger.debug("Heartbeat sent successfully for instance: {}", instanceId);
        } catch (InstanceNotFoundException e) {
            logger.warn("Instance {} not found during heartbeat, attempting re-registration.", instanceId, e);
            // Instance might have been deregistered, try registering again
            registerService();
        } catch (ServiceNotFoundException e) {
             logger.warn("Service not found during heartbeat for instance {}, attempting re-registration.", instanceId, e);
             // Service might have been deleted, try registering again (which will recreate the service)
             registerService();
        } catch (Exception e) {
            // Catch broader exceptions to prevent scheduler termination
            logger.error("Failed to send heartbeat for instance: {}", instanceId, e);
        }
    }
} 