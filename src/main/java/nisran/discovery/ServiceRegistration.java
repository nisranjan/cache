package nisran.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.*;
import software.amazon.awssdk.core.waiters.WaiterResponse;
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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import nisran.config.AWS_SDKConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import java.io.IOException;


@Component("serviceRegistration")
@EnableScheduling
@Profile("cluster")
public class ServiceRegistration{

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistration.class);

    private final ServiceDiscoveryClient serviceDiscoveryClient;
    private final EcsClient ecsClient;

    private final HttpClient httpClient; // For metadata endpoint calls
    private final ObjectMapper objectMapper; // For parsing metadata JSON
    
    private String namespaceName; // Will be injected from AWS_SDKConfig 
    private String namespaceId; // Will be injected from AWS_SDKConfig   
    private String vpcId; // Will be injected from AWS_SDKConfig    

    private String serviceName; // Will be injected from AWS_SDKConfig
    @Getter
    private String serviceId; // The ID used for CloudMap registration
    @Getter
    private String ip;
    @Getter
    private int port; // Will be injected from AWS_SDKConfig

    private String awsTaskARN; // The ID used for CloudMap registration, typically the task ARN.
    private String awsClusterARN;
    private String awsTaskId; 

    private boolean isRegistered; // Flag to track registration status


    public ServiceRegistration(ServiceDiscoveryClient serviceDiscoveryClient, EcsClient ecsClient, 
        ObjectMapper objectMapper, AWS_SDKConfig awsConfig, HttpClient httpClient) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        this.ecsClient = ecsClient;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient; // Or use a shared instance if managed by Spring

        // Initialize values from AWS_SDKConfig 
        this.namespaceName = awsConfig.getNamespaceName();
        this.serviceName = awsConfig.getServiceName();
        this.port = awsConfig.getPort();
        this.vpcId = awsConfig.getVpcId(); // Required for creating Private DNS namespaces

        initialize();
        logger.debug("ServiceRegistration initialized with serviceName: {}, port: {}, namespaceName: {}", 
                     serviceName, port, namespaceName);
        
        registerService();
    }

    protected void initialize() {
        logger.debug("ServiceRegistration initialize() called.");

        //Getting Task ARN and Cluster ARN from environment variables
        this.awsTaskARN = System.getenv("ECS_TASK_ARN");
        this.awsClusterARN = System.getenv("ECS_CLUSTER");

        if (awsTaskARN == null || awsClusterARN == null) {
            logger.debug("ECS_TASK_ARN or ECS_CLUSTER environment variables are not set. " +
                        "Attempting to retrieve from metadata endpoint.");
            
            getTaskAndClusterARN();
        }

        //Getting TaskId from Task ARN
        assert(awsTaskARN != null && !awsTaskARN.isEmpty());
        this.awsTaskId = awsTaskARN.substring(awsTaskARN.lastIndexOf("/") + 1);
        logger.debug("Found taskId {} from Task ARN {}", awsTaskId, awsTaskARN);

        // Fetch private IP from ECS task metadata
        addPrivateIp(); 
        
    }

    private void addPrivateIp() {
        logger.debug("Adding private IP from ECS task metadata.");

        
        DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
                .cluster(awsClusterARN) // Use the determined cluster ARN/name
                .tasks(awsTaskARN)
                .build();

        int delayMillis = 1000; // Initial delay for retry

        while (ip == null) {

            DescribeTasksResponse describeTasksResponse = ecsClient.describeTasks(describeTasksRequest);
            Task task = describeTasksResponse.tasks().get(0);
            
            // Now you can access details from the 'task' object
            // For example, to get the private IP:
            ip = task.attachments().stream()
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
                //this.ip = ip; // Set the private IP for registration attributes
                return;
            }

            try{
                TimeUnit.MILLISECONDS.sleep(delayMillis);
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }
            delayMillis *= 2; // Exponential Backoff

            logger.info("Retrying to get Private IP from ECS task metadata " +
                        "for Task ARN {} in Cluster {} in {} millis", 
                        awsTaskARN, awsClusterARN,delayMillis);
        }
        
    }

    /**
     * Fetches the ECS Task ARN by querying the ECS container metadata endpoint (v4).
     * This is the recommended way to get task and cluster metadata in Fargate.
     * Requires the ECS_CONTAINER_METADATA_URI_V4 environment variable to be set.
     * @return An EcsTaskMetadata object containing Task ARN and Cluster ARN, or null if not found.
     */
    private List<String> getTaskAndClusterARN() {

        List<String> result = new ArrayList<String>();


        String metadataUri = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
        
        if (metadataUri == null || metadataUri.trim().isEmpty()) {
            logger.debug("ECS_CONTAINER_METADATA_URI_V4 environment variable not found.");
            return result;
        }

        String taskMetadataUrl = metadataUri + "/task";
        //logger.debug("Attempting to fetch Task and Cluster ARN from metadata endpoint: {}", taskMetadataUrl);

        HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(taskMetadataUrl))
                    .header("Accept", "application/json")
                    .build();


        while(awsClusterARN == null || awsTaskARN == null) {
            int delayMillis = 1000; // Initial delay for retry
            try {
                //logger.debug("Sending request to ECS metadata endpoint: {}", taskMetadataUrl);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                JsonNode root = objectMapper.readTree(response.body());
                //TODO: check if this is actually needed
                String taskArn = root.path("TaskARN").asText(null);
                String clusterArn = root.path("Cluster").asText(null); // The 'Cluster' field usually contains the cluster ARN
                if (taskArn != null && clusterArn != null) {
                    logger.debug("Found ECS_TASK_ARN : {} and ECS_CLUSTER : {} from metadata endpoint", taskArn,clusterArn);

                    //Add taskArn
                    result.add(taskArn);
                    this.awsTaskARN = taskArn; // Set the awsTaskARN for later use
                    //Add clusterArn
                    result.add(clusterArn);
                    this.awsClusterARN = clusterArn; // Set the awsClusterARN for later use

                    return result; // Return the task and cluster ARNs
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Error fetching task metadata from {}", taskMetadataUrl, e);
            }

            logger.info("Retrying to fetch Task and Cluster ARN ... in {} millis", delayMillis);

            try{
                TimeUnit.MILLISECONDS.sleep(delayMillis);
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }
            delayMillis *= 2; // Exponential Backoff
        }
        
        return result; 
    }

    
    
    //@Bean("registerService")
    public void registerService() {
        
        try {
            logger.info("Starting service registration for service: {}", serviceName);
            
            this.namespaceId = findOrCreateNamespace(namespaceName); // Ensure namespace exists and get ID

            // Create service if it doesn't exist
            //if(serviceId == null) {
            serviceId = findOrCreateService(); // This will set serviceId
            //}
            
            findOrCreateInstance();

            //isRegistered = true; // Set registration status to true after successful registration
        } catch (Exception e) {
            logger.error("Failed to register service", e);
            throw new RuntimeException("Failed to register service", e);
        }
    } 

    private String findOrCreateNamespace(String namespaceName) {
        logger.debug("Finding or creating namespace with name: {}", namespaceName);
        
        // Ensure namespace exists
        if(namespaceName.isEmpty()) {
            throw new IllegalStateException("Namespace name must be set in AWS_SDKConfig");
        }
        String namespaceId = null;

        if(namespaceId == null) {
            namespaceId = findNamespace(namespaceName);

            if(namespaceId == null) {
                // Asynchronously create namespace if not found
                namespaceId = createNamespace(namespaceName);
            }

            int maxRetries = 0; // Maximum retries to wait for namespace creation
            while(maxRetries < 10) {
                // Wait for namespace creation to complete
                logger.info("Waiting for namespace creation to complete...");
                try {
                    TimeUnit.SECONDS.sleep(5); // Wait for 5 seconds before checking again
                    namespaceId = findNamespace(namespaceName); // Re-check for namespace ID
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Thread interrupted while waiting for namespace creation", e);
                }
                maxRetries++;
            }

            if(namespaceId == null) {
                throw new RuntimeException("Failed to find or create namespace: " + namespaceName);
            } else {
                logger.info("Namespace found or created with ID: {}", namespaceId);
            }
        }
        return namespaceId; // Return the found or created namespace ID
    }

    private String findNamespace(String namespaceName) {
        String namespaceId = null;
        try {
            logger.debug("Looking for existing namespace with name: {}", namespaceName);

            // Create ListNamespacesRequest with the given namespace name
            ListNamespacesRequest listNamespacesRequest = ListNamespacesRequest.builder()
                    .filters(NamespaceFilter.builder()
                            .name(NamespaceFilterName.NAME)
                            .values(namespaceName)
                            .condition(FilterCondition.EQ)
                            .build())
                    .build();

            // Create ListNamespacesResponse with above request
            ListNamespacesResponse listNamespacesResponse = serviceDiscoveryClient.listNamespaces(listNamespacesRequest);

            // Use Optional<NamespaceSummary> to find namespaceId
            Optional<NamespaceSummary> existingNamespace = listNamespacesResponse.namespaces().stream().findFirst();
            if (existingNamespace.isPresent()) {
                namespaceId = existingNamespace.get().id();
                logger.debug("Found existing namespace with ID: {}", namespaceId);
            } else {
                logger.debug("Namespace '{}' not found.", namespaceName);
            }
        } catch (Exception e) {
            logger.error("Failed to find namespace '{}'", namespaceName, e);
            //throw new RuntimeException("Failed to find namespace: " + namespaceName, e);
        }
        return namespaceId;
    }

    private String createNamespace(String namespaceName) {
        logger.debug("Creating new public DNS namespace with IPv6 for name: {}", namespaceName);

        CreatePublicDnsNamespaceRequest createNamespaceRequest = CreatePublicDnsNamespaceRequest.builder()
                .name(namespaceName)
                .description("Public DNS Namespace with IPv6")
                .properties(PublicDnsNamespaceProperties.builder()
                    .dnsProperties(PublicDnsPropertiesMutable.builder()
                        .soa(SOA.builder()
                            .ttl(60L)
                            .build())
                        .build())
                    .build())
                .build();

        CreatePublicDnsNamespaceResponse createNamespaceResponse =
                serviceDiscoveryClient.createPublicDnsNamespace(createNamespaceRequest);

        logger.debug("CreatePublicDnsNamespaceResponse received for namespace '{}', operationId: {}",
                namespaceName, createNamespaceResponse.operationId());

        return createNamespaceResponse.operationId(); // Return the created namespace ID 
    }

    //@Bean("findOrCreateService")
    private String findOrCreateService(){
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
                     .healthCheckCustomConfig(HealthCheckCustomConfig.builder().build()) // Example for custom health
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

    /**
     * Finds an existing instance by instanceId (which can be the serviceId or taskId), or registers a new one if not found.
     * @param instanceId The instanceId to use for lookup and registration (can be taskId or serviceId).
     * @return The instanceId that was found or registered.
     */
    public String findOrCreateInstance() {
        try {
            logger.debug("Looking for existing instance with instanceId: {} in service: {}", awsTaskId, serviceId);

            // Discover all instances for this service
            DiscoverInstancesRequest discoverRequest = DiscoverInstancesRequest.builder()
                    .namespaceName(namespaceName)
                    .serviceName(serviceName)
                    .build();

            DiscoverInstancesResponse discoverResponse = serviceDiscoveryClient.discoverInstances(discoverRequest);

            boolean exists = discoverResponse.instances().stream()
                    .anyMatch(instance -> instance.instanceId().equals(awsTaskId));

            if (exists) {
                logger.info("Found existing instance with instanceId: {}", awsTaskId);
                return awsTaskId;
            }

            logger.info("No existing instance found with instanceId: {}. Registering new instance.", awsTaskId);

            // Build attributes for registration
            Map<String, String> attributes = new HashMap<>();
            attributes.put("AWS_INSTANCE_IPV4", ip);
            attributes.put("AWS_INSTANCE_PORT", String.valueOf(port));
            attributes.put("ECS_TASK_ARN", awsTaskARN);
            attributes.put("ECS_TASK_ID", awsTaskId);

            RegisterInstanceRequest registerRequest = RegisterInstanceRequest.builder()
                    .serviceId(serviceId)
                    .instanceId(awsTaskId) // Use the provided instanceId (can be taskId)
                    .attributes(attributes)
                    .build();

            serviceDiscoveryClient.registerInstance(registerRequest);
            logger.info("Successfully registered new instance with instanceId: {}", awsTaskId);
            return awsTaskId;

        } catch (Exception e) {
            logger.error("Failed to find or create instance with instanceId: {}", awsTaskId, e);
            throw new RuntimeException("Failed to find or create instance: " + awsTaskId, e);
        }
    }


    @PreDestroy
    public void unregisterService() {
        if (awsTaskId != null && serviceId != null) {
            logger.info("Deregistering service instance: {} in service: {}", awsTaskId, serviceId);
            try {
            
            DeregisterInstanceRequest request = DeregisterInstanceRequest.builder()
                    .serviceId(serviceId)
                    .instanceId(awsTaskId)
                    .build();
            serviceDiscoveryClient.deregisterInstance(request);
            logger.info("Successfully deregistered service instance: {}", this.serviceId);
            
            } catch (Exception e) {
                logger.error("Failed to deregister service instance: {}", this.serviceId, e);
            }
        } else {
                logger.warn("Cannot deregister service instance: namespaceId or serviceId is null");
        }
    }

    @Scheduled(fixedRateString = "${cache.service.discovery.heartbeat-interval}")
    public void sendHeartbeat() {
        try {
            // Need serviceId to update health status
            //String namespaceId = findOrCreateNamespace(); // Ensure namespace exists
            //String serviceId = findOrCreateService(namespaceId); // Ensure service exists and get ID

            logger.debug("Sending heartbeat for instance: {} in service: {}", awsTaskId, serviceId);
            // Send heartbeat to keep instance registered
            // Note: UpdateInstanceCustomHealthStatus is used when HealthCheckCustomConfig is enabled for the service.
            // If not using custom health checks, Cloud Map relies on the health of the underlying resource (e.g., ECS task).
            // If the service uses custom health checks, this call is necessary.
            serviceDiscoveryClient.updateInstanceCustomHealthStatus(
                    UpdateInstanceCustomHealthStatusRequest.builder()
                            .serviceId(serviceId)
                            .instanceId(awsTaskId)
                            .status(CustomHealthStatus.HEALTHY)
                            .build()
            );
            logger.debug("Heartbeat sent successfully for instance: {}", awsTaskId);
        } catch (InstanceNotFoundException e) {
            logger.warn("Instance {} not found during heartbeat, attempting re-registration.", awsTaskId, e);
            // Instance might have been deregistered, try registering again
            //registerService();
        } catch (ServiceNotFoundException e) {
             logger.warn("Service not found during heartbeat for instance {}, attempting re-registration.", awsTaskId, e);
             // Service might have been deleted, try registering again (which will recreate the service)
             //registerService();
        } catch (Exception e) {
            // Catch broader exceptions to prevent scheduler termination
            logger.error("Failed to send heartbeat for instance: {}", awsTaskId, e);
        }
    }
    
}