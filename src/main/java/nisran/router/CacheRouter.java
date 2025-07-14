package nisran.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component; // Added import

import jakarta.annotation.PostConstruct;
import nisran.ServerInstance;
import nisran.config.AWS_SDKConfig;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.DiscoverInstancesRequest;
import software.amazon.awssdk.services.servicediscovery.model.DiscoverInstancesResponse;
import software.amazon.awssdk.services.servicediscovery.model.HealthStatusFilter;

import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile; // Added import

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component("cacheRouter") // Make CacheRouter a Spring-managed bean
@DependsOn("serviceRegistration") // Ensure serviceDiscoveryOperations is initialized before CacheRoute
@Profile("cluster") // Activate this bean only when 'cluster' profile is active
public class CacheRouter implements CHRoutingService{

    private static final Logger logger = LoggerFactory.getLogger(CacheRouter.class);
    private static final String HASH_ALGORITHM = "MD5";

    private final ServiceDiscoveryClient awsSDKClient; // Added for AWS Service Discovery
    private final AWS_SDKConfig awsSDKConfig; // Added for AWS SDK configuration

    private  String localNodeIdentifier;
    private int virtualNodes; // Default to 1 if not specified, can be set via AWS_SDKConfig

    // Maintains a map of taskId to its full node identifier (ip:port)
    private final ConcurrentHashMap<String, String> svrDictionary;
    private volatile SortedMap<Integer, ServerInstance> consistentHashRing; // hash -> nodeIdentifier (ip:port)
    private volatile List<ServerInstance> currentServerInstances; // Stores instances for buildConsistentHashRing

    private final MessageDigest md5Digest;
    private final ScheduledExecutorService discoveryScheduler;

    public CacheRouter(ServiceDiscoveryClient awsClient, AWS_SDKConfig config) {  // Typically injected via @Value in Spring
        
        this.virtualNodes = config.getVirtualNodes(); // Get virtual nodes from configuration

        this.awsSDKClient = awsClient; // Use AWS Service Discovery client
        this.awsSDKConfig = config; // Use AWS SDK configuration

        this.svrDictionary = new ConcurrentHashMap<>();
        this.consistentHashRing = Collections.unmodifiableSortedMap(new TreeMap<>()); // Initial empty, immutable ring
        this.currentServerInstances = Collections.emptyList();

        try {
            this.md5Digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            logger.error("{} algorithm not found, which is required for hashing.", HASH_ALGORITHM, e);
            throw new RuntimeException(HASH_ALGORITHM + " algorithm not found", e);
        }

        // Initial discovery and ring setup
        createOrUpdateServerDictionary(currentServerInstances);
        buildConsistentHashRing();

        this.discoveryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CacheRouter-DiscoveryThread");
            t.setDaemon(true); // Allow JVM to exit if this is the only thread running
            return t;
        });
        
        logger.info("CacheRouter initialized."+ 
            "Server discovery scheduled every {} seconds with {} virtual nodes per server.", 
            awsSDKConfig.getDiscoveryIntervalSeconds(), this.virtualNodes);
    }

    @PostConstruct
    private void scheduleUpdates() {
        // This method is now handled in the constructor with a ScheduledExecutorService
        // to periodically update the server dictionary and rebuild the consistent hash ring.
        // Keeping this method for potential future use or overrides.
        // Schedule periodic updates
        
        this.discoveryScheduler.scheduleAtFixedRate(() -> {
            createOrUpdateServerDictionary(currentServerInstances);
            buildConsistentHashRing();
        }, awsSDKConfig.getDiscoveryIntervalSeconds(), 
            awsSDKConfig.getDiscoveryIntervalSeconds(), 
            TimeUnit.SECONDS);
    }

    /**
     * Creates or updates the server dictionary by discovering active server instances.
     * This method is synchronized to ensure thread safety when updating the internal state.
     */

    @Override
    public synchronized void createOrUpdateServerDictionary(List<ServerInstance> serverInstances) {
        logger.debug("Attempting to create or update server dictionary.");
        List<ServerInstance> currentInstances;
        try {
            currentInstances = discoverInstances();
        } catch (Exception e) {
            logger.error("Error during service discovery while updating dictionary. Old dictionary and instances retained.", e);
            // Do not clear currentServerInstancesForRingBuilding on discovery error,
            // so buildConsistentHashRing can use the last known good state if it's called.
            return;
        }

        this.currentServerInstances = currentInstances; // Update the current server instances

        Map<String, String> latestTaskToServerNodeMap = currentServerInstances.stream()
                .filter(instance -> instance.getServiceId() != null && instance.getNodeIdentifier() != null)
                .collect(Collectors.toMap(ServerInstance::getServiceId, ServerInstance::getNodeIdentifier, (oldValue, newValue) -> newValue)); // In case of duplicate taskIds, take the new one

        // Check if the set of active server nodes has changed
        // A more robust check would compare the actual node identifiers if task IDs could be reused by different IPs
        if (!this.svrDictionary.keySet().equals(latestTaskToServerNodeMap.keySet()) ||
            !this.svrDictionary.entrySet().stream().allMatch(entry -> latestTaskToServerNodeMap.getOrDefault(entry.getKey(), "").equals(entry.getValue()))) {
            logger.info("Server dictionary changed. Old tasks: {}, New tasks: {}.", this.svrDictionary.keySet(), latestTaskToServerNodeMap.keySet());
            this.svrDictionary.clear();
            this.svrDictionary.putAll(latestTaskToServerNodeMap);
        } else {
            logger.debug("Server dictionary has not significantly changed.");
        }
    }

    @Override
    public synchronized void buildConsistentHashRing() {
        logger.debug("Attempting to build consistent hash ring.");
        List<ServerInstance> instancesToUse = this.currentServerInstances;

        if (instancesToUse == null) { // Should not happen if createOrUpdateServerDictionary ran
            logger.warn("currentServerInstancesForRingBuilding is null. Using empty list for ring construction.");
            instancesToUse = Collections.emptyList();
        }

        if (instancesToUse.isEmpty()) {
            logger.warn("No server instances available for ring construction. Clearing hash ring.");
            this.consistentHashRing = Collections.unmodifiableSortedMap(new TreeMap<>());
            return;
        }
        doRebuildConsistentHashRing(instancesToUse);
    }

    private void doRebuildConsistentHashRing(List<ServerInstance> instances) {
        SortedMap<Integer, ServerInstance> newRing = new TreeMap<>();
        for (ServerInstance instance : instances) {
            //TODO : review this code
            String nodeIdentifier = instance.getNodeIdentifier(); // ip:port
            if (nodeIdentifier == null) {
                logger.warn("Skipping instance with null node identifier: {}", instance);
                continue;
            }
            for (int i = 0; i < this.virtualNodes; i++) {
                String virtualNodeName = nodeIdentifier + "-VN" + i;
                int hash = calculateHash(virtualNodeName);
                newRing.put(hash, instance);
                logger.trace("Added virtual node {} with hash {} for server {}", virtualNodeName, hash, nodeIdentifier);
            }
        }
        this.consistentHashRing = Collections.unmodifiableSortedMap(newRing); // Atomically update the ring to an immutable version
        // Use serviceDiscovery.getActiveServerCount() for a potentially more up-to-date count if instances list could be stale
        // or if getActiveServerCount() has more complex logic. For simplicity, instances.size() is fine here.
        int activeServerCount =  currentServerInstances.size();// Example of using the new method
        logger.info("Consistent hash ring rebuilt with {} total virtual nodes from {} physical instances (reported active: {}). Current tasks: {}", 
            newRing.size(), instances.size(), activeServerCount, svrDictionary.keySet());

        if (logger.isTraceEnabled()){
             logger.trace("Current ring state: {}", newRing);
        }
    }

    /**
     * Gets the server address (node identifier, e.g., "ip:port") for the given key using consistent hashing.
     * @param key The key to route.
     * @return The node identifier (e.g., "ip:port") of the server responsible for the key, or null if no servers are available.
     */
    @Override
    public ServerInstance getServerInstanceForKey(String key) {
        SortedMap<Integer, ServerInstance> currentRing = this.consistentHashRing; // Use local reference for thread safety
        if (currentRing.isEmpty()) {
            logger.warn("Consistent hash ring is empty. Cannot route key: {}", key);
            return null;
        }

        int keyHash = calculateHash(key);
        logger.debug("Routing key '{}' with hash {}", key, keyHash);

        SortedMap<Integer, ServerInstance> tailMap = currentRing.tailMap(keyHash);

        ServerInstance svrInstance;
        if (tailMap.isEmpty()) {
            // If no such node, wrap around to the first node in the ring
            svrInstance = currentRing.get(currentRing.firstKey());
            logger.debug("Key hash {} is beyond the last node, wrapping around to first node: {} (hash: {})", keyHash, svrInstance, currentRing.firstKey());
        } else {
            svrInstance = tailMap.get(tailMap.firstKey());
            logger.debug("Key hash {} mapped to node {} (hash: {})", keyHash, svrInstance, tailMap.firstKey());
        }
        return svrInstance;
    }

    @Override
    public boolean isLocalServerNode(String key) {
        ServerInstance targetNodeIdentifier = getServerInstanceForKey(key);
        if (targetNodeIdentifier == null) {
            logger.warn("Cannot determine if key '{}' is local; no target node found.", key);
            return false; // Or throw an exception, depending on desired behavior
        }
        boolean isLocal = this.localNodeIdentifier.equals(targetNodeIdentifier);
        logger.debug("Key '{}' maps to node {}. Local node is {}. Is local: {}", key, targetNodeIdentifier, this.localNodeIdentifier, isLocal);
        return isLocal;
    }

    @Override
    public void addServerInstance(ServerInstance serverInstance) {
        // Will need to be implemented if dynamic addition of servers is required
        // We will need to add logic to move keys from the old server to the new one
        
    }


    @Override
    public void rebalanceKeys() {
        logger.info("Rebalancing keys across server instances.");
        // This method can be used to redistribute keys if the ring changes significantly
        // or if the number of active servers changes.
        // For simplicity, we will just rebuild the ring, which will automatically rebalance keys.
        //buildConsistentHashRing();
    }  
    

    /**
     * Returns a copy of the current mapping from task ID to server node identifier (ip:port).
     * @return A map of task IDs to their corresponding node identifiers.
     */
    public Map<String, String> getTaskToServerNodeMap() {
        return new ConcurrentHashMap<>(this.svrDictionary);
    }

    private int calculateHash(String input) {
        byte[] hashBytes;
        synchronized (md5Digest) { // MessageDigest is not thread-safe
            md5Digest.reset();
            hashBytes = md5Digest.digest(input.getBytes(StandardCharsets.UTF_8));
        }
        // Convert first 4 bytes of MD5 to an int.
        return ((hashBytes[0] & 0xFF) << 24) |
               ((hashBytes[1] & 0xFF) << 16) |
               ((hashBytes[2] & 0xFF) << 8)  |
               ((hashBytes[3] & 0xFF));
    }

    public void shutdown() {
        logger.info("Shutting down CacheRouter discovery scheduler.");
        discoveryScheduler.shutdown();
        try {
            if (!discoveryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                discoveryScheduler.shutdownNow();
                if (!discoveryScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("Discovery scheduler did not terminate.");
                }
            }
        } catch (InterruptedException e) {
            discoveryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("CacheRouter discovery scheduler shut down.");
    }

    // @Override // Uncomment if ServiceRegistration directly implements ServiceDiscoveryOperations
    public List<ServerInstance> discoverInstances() {

        String serviceName = awsSDKConfig.getServiceName();
        String namespaceName = awsSDKConfig.getNamespaceName();

        logger.debug("Discovering instances for service: {} in namespace: {}", serviceName, namespaceName);
        try {
            DiscoverInstancesRequest request = DiscoverInstancesRequest.builder()
                    .namespaceName(namespaceName)
                    .serviceName(serviceName)
                    .maxResults(100) // Adjust as needed
                    .healthStatus(HealthStatusFilter.HEALTHY) // Discover only healthy instances
                    .build();

            DiscoverInstancesResponse response = awsSDKClient.discoverInstances(request);
            return response.instances().stream()
                    .map(httpInstanceSummary -> {
                        Map<String, String> attributes = httpInstanceSummary.attributes();
                        String ip = attributes.get("AWS_INSTANCE_IPV4");
                        String portStr = attributes.get("AWS_INSTANCE_PORT");
                        // Prefer ECS_TASK_ARN as serviceId if available, otherwise use CloudMap's instanceId
                        String awsTaskARN = attributes.getOrDefault("ECS_TASK_ARN", httpInstanceSummary.instanceId());

                        String instanceId = awsTaskARN.substring(awsTaskARN.lastIndexOf("/") + 1);
                        //TODO: Add logic to convert task ARN to a serviceId if needed
                        if (ip != null && portStr != null && instanceId != null) {
                            try {
                                int discoveredPort = Integer.parseInt(portStr);
                                return new ServerInstance(instanceId, ip, discoveredPort);
                            } catch (NumberFormatException e) {
                                logger.warn("Failed to parse port for instance {}: {}. Attributes: {}", instanceId, portStr, attributes, e);
                                return null;
                            }
                        }
                        logger.warn("Instance {} (CloudMap ID: {}) missing required attributes (IP, Port, or determined serviceId). Attributes: {}",
                                instanceId, httpInstanceSummary.instanceId(), attributes);
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to discover instances for service {} in namespace {}", serviceName, namespaceName, e);
            return Collections.emptyList(); // Return empty list on error
        }
    }

    @Override
    public int getActiveServerCount(){
        int result = 0;

        if(!currentServerInstances.isEmpty()){
            result = currentServerInstances.size();
        }

        return result;
    }

    /**
     * Returns an immutable list of the currently known active server instances.
     * @return A list of active server instances.
     */
    public List<ServerInstance> getActiveServerInstances() {
        return Collections.unmodifiableList(this.currentServerInstances);
    }

    // @Override // Uncomment if ServiceRegistration directly implements ServiceDiscoveryOperations
    /* 
    public String getTaskForLocalServer() {
        // Return the Task ARN stored in the serverInstance field, which is populated by getInstance().
        if (this.serverInstance != null) {
            return this.serverInstance.getServiceId(); // serviceId is the ARN obtained in getInstance
        }
        logger.warn("getTaskForLocalServer() called but local serverInstance is null. This indicates an initialization issue.");
        return null;
    }*/
}