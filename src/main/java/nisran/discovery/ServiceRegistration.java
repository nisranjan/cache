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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
    
import nisran.ServerInstance;
import nisran.router.ServiceDiscoveryOperations; // Added import

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.io.IOException;
import jakarta.annotation.PostConstruct;

@Configuration
@EnableScheduling
@Profile("cluster")
public class ServiceRegistration implements ServiceDiscoveryOperations{

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
    private ServerInstance serverInstance;
    private String localTaskArn; // The ID used for CloudMap registration, typically the task ARN.
    private String taskId; // The ID used for CloudMap registration
    private String ip;

    private final HttpClient httpClient; // For metadata endpoint calls
    private final ObjectMapper objectMapper; // For parsing metadata JSON

    public ServiceRegistration(ServiceDiscoveryClient serviceDiscoveryClient, EcsClient ecsClient, ObjectMapper objectMapper) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        this.ecsClient = ecsClient;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient(); // Or use a shared instance if managed by Spring
    }


    private ServerInstance getInstance(){
        // This method should be called after @Value fields are injected.
        // It attempts to get the local instance details (Task ARN, IP).

        String localTaskArn = null;
        int maxRetries = 5;
        long delayMillis = 1000;
        String clusterArn = null;

        for (int i = 0; i < maxRetries; i++) {
            localTaskArn = System.getenv("ECS_TASK_ARN");
            String clusterEnv = System.getenv("ECS_CLUSTER"); // Check env var for cluster too

            if (localTaskArn != null && clusterEnv != null) {
                logger.debug("Found ECS_TASK_ARN from environment variable: ECS_TASK_ARN {} ; ECS_CLUSTER {}", 
                                        localTaskArn,clusterEnv);
                //logger.debug("Found ECS_CLUSTER from environment variable: {}", clusterEnv);
                clusterArn = clusterEnv; // Use env var if both are present
                break;
            }else{
                // If ECS_TASK_ARN env var is not set, try the metadata endpoint (more reliable for Fargate)
                List<String> metadata = getInstanceMetadata();
                if (metadata.size() == 2 && metadata.get(0) != null && metadata.get(1) != null) {
                    localTaskArn = metadata.get(0);
                    clusterArn = metadata.get(1);

                    logger.debug("Found ECS_CLUSTER from metadata endpoint: ECS_TASK_ARN {} ; ECS_CLUSTER {}", 
                                        localTaskArn,clusterArn);
                    //logger.debug("Found ECS_CLUSTER from metadata endpoint: {}", clusterArn);
                    break; // Found it from metadata, no need to retry env var
                }
                logger.info("Local Task ARN not yet set (from env or metadata). Retrying in {} ms...", delayMillis);
                try{
                    TimeUnit.MILLISECONDS.sleep(delayMillis);
                }catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                }
                delayMillis *= 2; // Exponential Backoff
            }
        }

        if(localTaskArn == null || clusterArn == null){
            logger.warn("ECS_TASK_ARN and / or ECS_CLUSTER could not be determined from environment " +
                        "variables or metadata endpoint.");
            return null;
        }

        //Finding TaskId from ARN
        this.taskId = localTaskArn.substring(localTaskArn.lastIndexOf("/") + 1);

        logger.debug("Found TaskID {} from Task ARN {}", taskId, localTaskArn);

        DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
                .cluster(clusterArn) // Use the determined cluster ARN/name
                .tasks(localTaskArn)
                .build();

        DescribeTasksResponse describeTasksResponse = ecsClient.describeTasks(describeTasksRequest);
        if (!describeTasksResponse.tasks().isEmpty()) {
            Task task = describeTasksResponse.tasks().get(0);
            
            // Now you can access details from the 'task' object
            // For example, to get the private IP:
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
                return new ServerInstance(this.taskId, ip, port);
            }
        }

        return null;
    }

    /**
     * Fetches the ECS Task ARN by querying the ECS container metadata endpoint (v4).
     * This is the recommended way to get task and cluster metadata in Fargate.
     * Requires the ECS_CONTAINER_METADATA_URI_V4 environment variable to be set.
     * @return An EcsTaskMetadata object containing Task ARN and Cluster ARN, or null if not found.
     */
    private List<String> getInstanceMetadata() {

        List<String> result = new ArrayList<String>();


        String metadataUri = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
        if (metadataUri == null || metadataUri.trim().isEmpty()) {
            logger.debug("ECS_CONTAINER_METADATA_URI_V4 environment variable not found.");
            return result;
        }

        String taskMetadataUrl = metadataUri + "/task";
        logger.debug("Attempting to fetch Task and Cluster ARN from metadata endpoint: {}", taskMetadataUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(taskMetadataUrl))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String taskArn = root.path("TaskARN").asText(null);
                String clusterArn = root.path("Cluster").asText(null); // The 'Cluster' field usually contains the cluster ARN
                if (taskArn != null && clusterArn != null) {
                    logger.debug("Found ECS_TASK_ARN : {} and ECS_CLUSTER : {} from metadata endpoint", taskArn,clusterArn);

                    //Add taskArn
                    result.add(taskArn);
                    //Add clusterArn
                    result.add(clusterArn);


                    return result;
                } else {
                    logger.warn("TaskARN and/or Cluster field not found in metadata response from {}. Response body: {}", taskMetadataUrl, response.body());
                }
            } else {
                logger.warn("Failed to fetch task metadata from {}. Status code: {}. Response body: {}", taskMetadataUrl, response.statusCode(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error fetching task metadata from {}", taskMetadataUrl, e);
        }
        return result; 
    }

    /*
    private String getPrivateIp() {
        try {
            // Try to get private IP from ECS task metadata
            if (localTaskArn != null) {
                logger.debug("Found ECS_TASK_ARN: {}", localTaskArn);
                DescribeTasksRequest request = DescribeTasksRequest.builder()
                    .cluster(System.getenv("ECS_CLUSTER"))
                    .tasks(localTaskArn)
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
    } */

    @PostConstruct
    public void initialize() {
        logger.debug("ServiceRegistration @PostConstruct called.");
        // Initialize serverInstance and registrationId after @Value fields are set
        this.serverInstance = getInstance();
        if (this.serverInstance == null) {
            logger.error("Failed to determine local server instance details during initialization. ServiceRegistration cannot proceed in cluster mode.");
            throw new IllegalStateException("Failed to initialize ServiceRegistration: Local instance details (IP, Task ARN) could not be determined. Ensure ECS_TASK_ARN and ECS_CLUSTER are available when running with 'cluster' profile.");
        }
        this.localTaskArn = this.serverInstance.getTaskId(); // Use the task ARN as the unique registration ID
        this.ip = this.serverInstance.getIpAddress(); // IP is also needed for registration attributes
        logger.info("ServiceRegistration initialized for instance ID: {}, privateIp: {}", localTaskArn, ip);
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
            attributes.put("AWS_INSTANCE_IPV4",ip ); // privateIp should be non-null due to constructor logic
            attributes.put("AWS_INSTANCE_PORT", String.valueOf(port));

            // Use the task ARN from the serverInstance object
            if (this.serverInstance != null && this.serverInstance.getTaskId() != null) {
                attributes.put("ECS_TASK_ARN", this.serverInstance.getTaskId());
            }
            String clusterName = System.getenv("ECS_CLUSTER");
            if (clusterName != null) {
                attributes.put("ECS_CLUSTER", clusterName);
            }

            // Register instance
            RegisterInstanceRequest request = RegisterInstanceRequest.builder()
                    .serviceId(serviceId)
                    .instanceId(this.taskId) // Use the task ARN for CloudMap instanceId
                    .attributes(attributes) // Use the safely built map
                    .build();

            serviceDiscoveryClient.registerInstance(request);
            logger.info("Successfully registered service instance with ID: {}", this.localTaskArn);
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

            logger.debug("Sending heartbeat for instance: {} in service: {}", localTaskArn, serviceId);
            // Send heartbeat to keep instance registered
            // Note: UpdateInstanceCustomHealthStatus is used when HealthCheckCustomConfig is enabled for the service.
            // If not using custom health checks, Cloud Map relies on the health of the underlying resource (e.g., ECS task).
            // If the service uses custom health checks, this call is necessary.
            serviceDiscoveryClient.updateInstanceCustomHealthStatus(
                    UpdateInstanceCustomHealthStatusRequest.builder()
                            .serviceId(serviceId)
                            .instanceId(localTaskArn)
                            .status(CustomHealthStatus.HEALTHY)
                            .build()
            );
            logger.debug("Heartbeat sent successfully for instance: {}", localTaskArn);
        } catch (InstanceNotFoundException e) {
            logger.warn("Instance {} not found during heartbeat, attempting re-registration.", localTaskArn, e);
            // Instance might have been deregistered, try registering again
            registerService();
        } catch (ServiceNotFoundException e) {
             logger.warn("Service not found during heartbeat for instance {}, attempting re-registration.", localTaskArn, e);
             // Service might have been deleted, try registering again (which will recreate the service)
             registerService();
        } catch (Exception e) {
            // Catch broader exceptions to prevent scheduler termination
            logger.error("Failed to send heartbeat for instance: {}", localTaskArn, e);
        }
    }

    // Implementation of ServiceDiscoveryOperations

    // @Override // Uncomment if ServiceRegistration directly implements ServiceDiscoveryOperations
    public List<ServerInstance> discoverInstances() {
        logger.debug("Discovering instances for service: {} in namespace: {}", serviceName, namespaceName);
        try {
            DiscoverInstancesRequest request = DiscoverInstancesRequest.builder()
                    .namespaceName(namespaceName)
                    .serviceName(serviceName)
                    .maxResults(100) // Adjust as needed
                    .healthStatus(HealthStatusFilter.HEALTHY) // Discover only healthy instances
                    .build();

            DiscoverInstancesResponse response = serviceDiscoveryClient.discoverInstances(request);
            return response.instances().stream()
                    .map(httpInstanceSummary -> {
                        Map<String, String> attributes = httpInstanceSummary.attributes();
                        String ip = attributes.get("AWS_INSTANCE_IPV4");
                        String portStr = attributes.get("AWS_INSTANCE_PORT");
                        // Prefer ECS_TASK_ARN as taskId if available, otherwise use CloudMap's instanceId
                        String taskId = attributes.getOrDefault("ECS_TASK_ARN", httpInstanceSummary.instanceId());

                        if (ip != null && portStr != null && taskId != null) {
                            try {
                                int discoveredPort = Integer.parseInt(portStr);
                                return new ServerInstance(taskId, ip, discoveredPort);
                            } catch (NumberFormatException e) {
                                logger.warn("Failed to parse port for instance {}: {}. Attributes: {}", taskId, portStr, attributes, e);
                                return null;
                            }
                        }
                        logger.warn("Instance {} (CloudMap ID: {}) missing required attributes (IP, Port, or determined TaskId). Attributes: {}",
                                taskId, httpInstanceSummary.instanceId(), attributes);
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to discover instances for service {} in namespace {}", serviceName, namespaceName, e);
            return Collections.emptyList(); // Return empty list on error
        }
    }

    // @Override // Uncomment if ServiceRegistration directly implements ServiceDiscoveryOperations
    public int getActiveServerCount() {
        return discoverInstances().size();
    }

    // @Override // Uncomment if ServiceRegistration directly implements ServiceDiscoveryOperations
    public String getTaskForLocalServer() {
        // Return the Task ARN stored in the serverInstance field, which is populated by getInstance().
        if (this.serverInstance != null) {
            return this.serverInstance.getTaskId(); // TaskId is the ARN obtained in getInstance
        }
        logger.warn("getTaskForLocalServer() called but local serverInstance is null. This indicates an initialization issue.");
        return null;
    }
} 