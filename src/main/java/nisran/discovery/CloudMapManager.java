package nisran.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.*;
import software.amazon.awssdk.services.servicediscovery.model.OperationStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Profile("cluster")
public class CloudMapManager {
    private static final Logger logger = LoggerFactory.getLogger(CloudMapManager.class);

    private final ServiceDiscoveryClient serviceDiscoveryClient;
    private static final int MAX_RETRIES = 10;
    private static final int RETRY_DELAY_SECONDS = 100;

    @Value("${cache.service.discovery.ip-version}")
    private String ipVersion;

    public CloudMapManager(ServiceDiscoveryClient serviceDiscoveryClient) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
    }

    public String findOrCreateNamespace(String namespaceName) {
        logger.debug("Finding or creating namespace with name: {}", namespaceName);

        if (namespaceName == null || namespaceName.isEmpty()) {
            throw new IllegalStateException("Namespace name must be set in AWS_SDKConfig");
        }
        
        String namespaceId = findNamespace(namespaceName);

        if (namespaceId == null) {
            logger.info("Namespace '{}' not found. Creating new namespace...", namespaceName);
            // Create namespace only once
            namespaceId = createNamespace(namespaceName);

            // Retry finding the namespace until it's available
            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                logger.info("Waiting for namespace creation to complete... (Attempt {}/{})", retryCount + 1, MAX_RETRIES);
                try {
                    TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
                    namespaceId = findNamespace(namespaceName);
                    if (namespaceId != null) {
                        logger.info("Namespace '{}' successfully created with ID: {}", namespaceName, namespaceId);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Thread interrupted while waiting for namespace creation", e);
                    break;
                }
                retryCount++;
            }

            if (namespaceId == null) {
                throw new RuntimeException("Failed to find or create namespace: " + namespaceName + " after " + MAX_RETRIES + " retries");
            }
        } else {
            logger.info("Found existing namespace '{}' with ID: {}", namespaceName, namespaceId);
        }
        return namespaceId;
    }

    public String findOrCreateService(String serviceName, String namespaceId) {
        logger.debug("Finding or creating service with name: {} in namespace: {}", serviceName, namespaceId);

        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
        }
        if (namespaceId == null || namespaceId.isEmpty()) {
            throw new IllegalArgumentException("Namespace ID cannot be null or empty");
        }

        String serviceId = findService(serviceName, namespaceId);

        if (serviceId == null) {
            logger.info("Service '{}' not found in namespace '{}'. Creating new service...", serviceName, namespaceId);
            // Create service only once
            serviceId = createService(serviceName, namespaceId);

            // Retry finding the service until it's available
            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                logger.info("Waiting for service creation to complete... (Attempt {}/{})", retryCount + 1, MAX_RETRIES);
                try {
                    TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
                    serviceId = findService(serviceName, namespaceId);
                    if (serviceId != null) {
                        logger.info("Service '{}' successfully created with ID: {}", serviceName, serviceId);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Thread interrupted while waiting for service creation", e);
                    break;
                }
                retryCount++;
            }

            if (serviceId == null) {
                throw new RuntimeException("Failed to find or create service: " + serviceName + " after " + MAX_RETRIES + " retries");
            }
        } else {
            logger.info("Found existing service '{}' with ID: {}", serviceName, serviceId);
        }
        return serviceId;
    }

    public String findOrCreateInstance(String serviceId, String awsTaskId, String ip, int port, String awsTaskARN) {
        logger.debug("Finding or creating instance with instanceId: {} in service: {}", awsTaskId, serviceId);

        // Validate required parameters
        if (serviceId == null || serviceId.isEmpty()) {
            throw new IllegalArgumentException("Service ID cannot be null or empty");
        }
        if (awsTaskId == null || awsTaskId.isEmpty()) {
            throw new IllegalArgumentException("Task ID cannot be null or empty");
        }

        String instanceId = findInstance(serviceId, awsTaskId);

        if (instanceId == null) {
            logger.info("Instance '{}' not found in service '{}'. Creating new instance...", awsTaskId, serviceId);
            // Create instance only once
            instanceId = createInstance(serviceId, awsTaskId, ip, port, awsTaskARN);

            // Retry finding the instance until it's available
            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                logger.info("Waiting for instance creation to complete... (Attempt {}/{})", retryCount + 1, MAX_RETRIES);
                try {
                    TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
                    instanceId = findInstance(serviceId, awsTaskId);
                    if (instanceId != null) {
                        logger.info("Instance '{}' successfully created", awsTaskId);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Thread interrupted while waiting for instance creation", e);
                    break;
                }
                retryCount++;
            }

            if (instanceId == null) {
                throw new RuntimeException("Failed to find or create instance: " + awsTaskId + " after " + MAX_RETRIES + " retries");
            }
        } else {
            logger.info("Found existing instance with instanceId: {}", awsTaskId);
        }
        return instanceId;
    }

    private String findNamespace(String namespaceName) {
        String namespaceId = null;
        try {
            logger.debug("Looking for existing namespace with name: {}", namespaceName);

            ListNamespacesRequest listNamespacesRequest = ListNamespacesRequest.builder()
                    .filters(NamespaceFilter.builder()
                            .name(NamespaceFilterName.NAME)
                            .values(namespaceName)
                            .condition(FilterCondition.EQ)
                            .build())
                    .build();

            ListNamespacesResponse listNamespacesResponse = serviceDiscoveryClient.listNamespaces(listNamespacesRequest);

            Optional<NamespaceSummary> existingNamespace = listNamespacesResponse.namespaces().stream().findFirst();
            if (existingNamespace.isPresent()) {
                namespaceId = existingNamespace.get().id();
                logger.debug("Found existing namespace with ID: {}", namespaceId);
            } else {
                logger.debug("Namespace '{}' not found.", namespaceName);
            }
        } catch (Exception e) {
            logger.error("Failed to find namespace '{}'", namespaceName, e);
        }
        return namespaceId;
    }

    private String createNamespace(String namespaceName) {
        logger.info("Creating new public DNS namespace with name: {}", namespaceName);

        try {
            CreatePublicDnsNamespaceRequest createNamespaceRequest = CreatePublicDnsNamespaceRequest.builder()
                    .name(namespaceName)
                    .description("Public DNS Namespace for " + namespaceName)
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

            logger.info("CreatePublicDnsNamespaceResponse received for namespace '{}', operationId: {}",
                    namespaceName, createNamespaceResponse.operationId());

            return createNamespaceResponse.operationId();
        } catch (Exception e) {
            logger.error("Failed to create namespace '{}'", namespaceName, e);
            throw new RuntimeException("Failed to create namespace: " + namespaceName, e);
        }
    }

    private String findService(String serviceName, String namespaceId) {
        String serviceId = null;
        try {
            logger.debug("Looking for existing service with name: {} in namespace: {}", serviceName, namespaceId);

            ListServicesRequest listRequest = ListServicesRequest.builder()
                    .filters(ServiceFilter.builder()
                            .name(ServiceFilterName.NAMESPACE_ID)
                            .values(namespaceId)
                            .condition(FilterCondition.EQ)
                            .build())
                    .build();

            ListServicesResponse listResponse = serviceDiscoveryClient.listServices(listRequest);
            Optional<ServiceSummary> existingService = listResponse.services().stream()
                    .filter(s -> s.name().equals(serviceName))
                    .findFirst();

            if (existingService.isPresent()) {
                serviceId = existingService.get().id();
                logger.debug("Found existing service with ID: {}", serviceId);
            } else {
                logger.debug("Service '{}' not found in namespace '{}'.", serviceName, namespaceId);
            }
        } catch (Exception e) {
            logger.error("Failed to find service '{}' in namespace '{}'", serviceName, namespaceId, e);
        }
        return serviceId;
    }

    private String createService(String serviceName, String namespaceId) {
        logger.info("Creating new service with name: {} in namespace: {}", serviceName, namespaceId);
        RecordType recordType = "ipv6".equalsIgnoreCase(ipVersion) ? RecordType.AAAA : RecordType.A;

        try {
            CreateServiceRequest createRequest = CreateServiceRequest.builder()
                    .name(serviceName)
                    .namespaceId(namespaceId)
                    .description("Service for " + serviceName)
                    .dnsConfig(DnsConfig.builder()
                            .dnsRecords(DnsRecord.builder()
                                    .type(recordType)
                                    .ttl(60L)
                                    .build())
                            .routingPolicy(RoutingPolicy.WEIGHTED)
                            .build())
                    .healthCheckCustomConfig(HealthCheckCustomConfig.builder().build())
                    .build();

            CreateServiceResponse createResponse = serviceDiscoveryClient.createService(createRequest);
            String serviceId = createResponse.service().id();
            logger.info("Created new service with ID: {}", serviceId);
            return serviceId;
        } catch (Exception e) {
            logger.error("Failed to create service '{}' in namespace '{}'", serviceName, namespaceId, e);
            throw new RuntimeException("Failed to create service: " + serviceName, e);
        }
    }

    private String findInstance(String serviceId, String awsTaskId) {
        String instanceId = null;
        try {
            logger.debug("Looking for existing instance with instanceId: {} in service: {}", awsTaskId, serviceId);

            GetInstanceRequest getRequest = GetInstanceRequest.builder()
                .serviceId(serviceId)
                .instanceId(awsTaskId)
                .build();

            try {
                GetInstanceResponse getResponse = serviceDiscoveryClient.getInstance(getRequest);
                if (getResponse != null) {
                    instanceId = awsTaskId;
                    logger.debug("Found existing instance with instanceId: {}", awsTaskId);
                }
            } catch (InstanceNotFoundException e) {
                logger.debug("Instance '{}' not found in service '{}'.", awsTaskId, serviceId);
            }
        } catch (Exception e) {
            logger.debug("Failed to find instance '{}' in service '{}'", awsTaskId, serviceId, e);
        }
        return instanceId;
    }

    private String createInstance(String serviceId, String awsTaskId, String ip, int port, String awsTaskARN) {
        logger.info("Creating new instance with instanceId: {} in service: {}", awsTaskId, serviceId);

        String ipAttributeKey = "ipv6".equalsIgnoreCase(ipVersion) ? "AWS_INSTANCE_IPV6" : "AWS_INSTANCE_IPV4";

        try {
            Map<String, String> attributes = new HashMap<>();
            attributes.put(ipAttributeKey, ip != null ? ip : "");
            attributes.put("AWS_INSTANCE_PORT", String.valueOf(port));
            attributes.put("ECS_TASK_ARN", awsTaskARN != null ? awsTaskARN : "");
            attributes.put("ECS_TASK_ID", awsTaskId);

            RegisterInstanceRequest registerRequest = RegisterInstanceRequest.builder()
                    .serviceId(serviceId)
                    .instanceId(awsTaskId)
                    .attributes(attributes)
                    .build();

            RegisterInstanceResponse response = serviceDiscoveryClient.registerInstance(registerRequest);
            String operationId = response.operationId();
            logger.info("Instance registration initiated - OperationId: {}, ServiceId: {}, InstanceId: {}", 
                        operationId, serviceId, awsTaskId);

            // Wait for operation to complete
            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                GetOperationRequest getOperationRequest = GetOperationRequest.builder()
                        .operationId(operationId)
                        .build();
                
                GetOperationResponse operation = serviceDiscoveryClient.getOperation(getOperationRequest);
                OperationStatus status = operation.operation().status();
                
                // Log operation details
                logger.info("Operation status check - OperationId: {}, Status: {}, Attempt: {}/{}", 
                            operationId, status, retryCount + 1, MAX_RETRIES);

                if (status == OperationStatus.SUCCESS) {
                    logger.info("Instance registration succeeded - OperationId: {}, ServiceId: {}, InstanceId: {}", 
                                operationId, serviceId, awsTaskId);
                    return awsTaskId;
                } else if (status == OperationStatus.FAIL) {
                    String errorMessage = operation.operation().errorMessage();
                    logger.error("Instance registration failed - OperationId: {}, ServiceId: {}, InstanceId: {}, Error: {}", 
                                operationId, serviceId, awsTaskId, errorMessage);
                    throw new RuntimeException("Failed to register instance: " + errorMessage);
                } else if (status == OperationStatus.PENDING) {
                    logger.debug("Operation pending - OperationId: {}, ServiceId: {}, InstanceId: {}", 
                                operationId, serviceId, awsTaskId);
                }

                try {
                    TimeUnit.SECONDS.sleep(RETRY_DELAY_SECONDS);
                } catch (InterruptedException e) {
                    logger.warn("Operation monitoring interrupted - OperationId: {}", operationId);
                    Thread.currentThread().interrupt();
                    break;
                }
                retryCount++;
            }

            logger.warn("Operation timed out - OperationId: {}, ServiceId: {}, InstanceId: {}", 
                        operationId, serviceId, awsTaskId);
            throw new RuntimeException("Instance registration operation timed out after " + MAX_RETRIES + " retries");
        } catch (Exception e) {
            logger.warn("Instance registration failed with exception - ServiceId: {}, InstanceId: {}, Error: {}", 
                        serviceId, awsTaskId, e.getMessage(), e);
            throw new RuntimeException("Failed to create instance: " + awsTaskId, e);
        }
    }
} 