package nisran.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // Added import
import org.springframework.stereotype.Component; // Added import

import nisran.ServerInstance;
import nisran.discovery.ServiceDiscoveryOperations;

import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile; // Added import

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component("cacheRouter") // Make CacheRouter a Spring-managed bean
@DependsOn("registerService") // Ensure serviceDiscoveryOperations is initialized before CacheRoute
@Profile("cluster") // Activate this bean only when 'cluster' profile is active
public class CacheRouter implements RouterService{

    private static final Logger logger = LoggerFactory.getLogger(CacheRouter.class);
    private static final String HASH_ALGORITHM = "MD5";

    @Value("${router.virtual-nodes-per-server}")
    private int virtualNodesPerServer;

    @Value("${router.router-refresh-interval-seconds}")
    private long discoveryIntervalSeconds;

    private final ServiceDiscoveryOperations serviceDiscovery;
    private  String localNodeIdentifier;

    // Maintains a map of taskId to its full node identifier (ip:port)
    private final ConcurrentHashMap<String, String> taskToServerNodeIdentifierMap;
    private volatile SortedMap<Integer, String> consistentHashRing; // hash -> nodeIdentifier (ip:port)
    private volatile List<ServerInstance> currentServerInstancesForRingBuilding; // Stores instances for buildConsistentHashRing

    private final MessageDigest md5Digest;
    private final ScheduledExecutorService discoveryScheduler;

    public CacheRouter(ServiceDiscoveryOperations serviceDiscovery) {  // Typically injected via @Value in Spring
        if (serviceDiscovery == null) {
            throw new IllegalArgumentException("ServiceDiscoveryOperations cannot be null.");
        }
        if (virtualNodesPerServer <= 0) {
            virtualNodesPerServer = 1;
            //throw new IllegalArgumentException("Number of virtual nodes per server must be positive.");
        }

        this.serviceDiscovery = serviceDiscovery;

        //this.virtualNodesPerServer = virtualNodesPerServer;
        //this.discoveryIntervalSeconds = discoveryIntervalSeconds;
        
 
        /* 
        if (this.discoveryIntervalSeconds <= 0) {
            throw new IllegalArgumentException("Discovery interval must be positive.");
        }*/

        this.taskToServerNodeIdentifierMap = new ConcurrentHashMap<>();
        this.consistentHashRing = Collections.unmodifiableSortedMap(new TreeMap<>()); // Initial empty, immutable ring
        this.currentServerInstancesForRingBuilding = Collections.emptyList();

        try {
            this.md5Digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            logger.error("{} algorithm not found, which is required for hashing.", HASH_ALGORITHM, e);
            throw new RuntimeException(HASH_ALGORITHM + " algorithm not found", e);
        }

        // Initial discovery and ring setup
        createOrUpdateServerDictionary();
        buildConsistentHashRing();

        //TODO: fix this code
        //Now get the localNodeidentifier
        String tempLocalNodeIdentifier = null; // Use a temporary variable
        if(!taskToServerNodeIdentifierMap.isEmpty()){
            String localTaskKey = serviceDiscovery.getTaskForLocalServer();
            logger.debug("getTaskForLocalServer() returned: {}", localTaskKey);
            if (localTaskKey != null) {
                tempLocalNodeIdentifier = taskToServerNodeIdentifierMap.get(localTaskKey);
            } else {
                logger.warn("getTaskForLocalServer() returned null. Cannot determine local node identifier.");
            }
        }
        this.localNodeIdentifier = tempLocalNodeIdentifier; // Assign to the final field

        if(this.localNodeIdentifier == null){
            logger.error("localNodeIdentifier could not be determined and is NULL. This instance cannot identify itself in the cluster. Task map: {}", taskToServerNodeIdentifierMap);
            throw new RuntimeException("localNodeIdentifier is NULL. Check service discovery and task ID mapping for the local server.");
        }

        // Schedule periodic updates
        this.discoveryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CacheRouter-DiscoveryThread");
            t.setDaemon(true); // Allow JVM to exit if this is the only thread running
            return t;
        });
        this.discoveryScheduler.scheduleAtFixedRate(() -> {
            createOrUpdateServerDictionary();
            buildConsistentHashRing();
        }, this.discoveryIntervalSeconds, this.discoveryIntervalSeconds, TimeUnit.SECONDS);
        logger.info("CacheRouter initialized. Server discovery scheduled every {} seconds with {} virtual nodes per server.", discoveryIntervalSeconds, this.virtualNodesPerServer);
    }

    @Override
    public synchronized void createOrUpdateServerDictionary() {
        logger.debug("Attempting to create or update server dictionary.");
        List<ServerInstance> currentInstances;
        try {
            currentInstances = serviceDiscovery.discoverInstances();
        } catch (Exception e) {
            logger.error("Error during service discovery while updating dictionary. Old dictionary and instances retained.", e);
            // Do not clear currentServerInstancesForRingBuilding on discovery error,
            // so buildConsistentHashRing can use the last known good state if it's called.
            return;
        }

        if (currentInstances == null) {
            logger.warn("Service discovery returned null. Assuming no instances.");
            currentInstances = Collections.emptyList();
        }

        // Store for buildConsistentHashRing and for comparison
        this.currentServerInstancesForRingBuilding = currentInstances;

        Map<String, String> latestTaskToServerNodeMap = currentInstances.stream()
                .filter(instance -> instance.getTaskId() != null && instance.getNodeIdentifier() != null)
                .collect(Collectors.toMap(ServerInstance::getTaskId, ServerInstance::getNodeIdentifier, (oldValue, newValue) -> newValue)); // In case of duplicate taskIds, take the new one

        // Check if the set of active server nodes has changed
        // A more robust check would compare the actual node identifiers if task IDs could be reused by different IPs
        if (!this.taskToServerNodeIdentifierMap.keySet().equals(latestTaskToServerNodeMap.keySet()) ||
            !this.taskToServerNodeIdentifierMap.entrySet().stream().allMatch(entry -> latestTaskToServerNodeMap.getOrDefault(entry.getKey(), "").equals(entry.getValue()))) {
            logger.info("Server dictionary changed. Old tasks: {}, New tasks: {}.", this.taskToServerNodeIdentifierMap.keySet(), latestTaskToServerNodeMap.keySet());
            this.taskToServerNodeIdentifierMap.clear();
            this.taskToServerNodeIdentifierMap.putAll(latestTaskToServerNodeMap);
        } else {
            logger.debug("Server dictionary has not significantly changed.");
        }
    }

    @Override
    public synchronized void buildConsistentHashRing() {
        logger.debug("Attempting to build consistent hash ring.");
        List<ServerInstance> instancesToUse = this.currentServerInstancesForRingBuilding;

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
        SortedMap<Integer, String> newRing = new TreeMap<>();
        for (ServerInstance instance : instances) {
            String nodeIdentifier = instance.getNodeIdentifier(); // ip:port
            if (nodeIdentifier == null) {
                logger.warn("Skipping instance with null node identifier: {}", instance);
                continue;
            }
            for (int i = 0; i < this.virtualNodesPerServer; i++) {
                String virtualNodeName = nodeIdentifier + "-VN" + i;
                int hash = calculateHash(virtualNodeName);
                newRing.put(hash, nodeIdentifier);
                logger.trace("Added virtual node {} with hash {} for server {}", virtualNodeName, hash, nodeIdentifier);
            }
        }
        this.consistentHashRing = Collections.unmodifiableSortedMap(newRing); // Atomically update the ring to an immutable version
        // Use serviceDiscovery.getActiveServerCount() for a potentially more up-to-date count if instances list could be stale
        // or if getActiveServerCount() has more complex logic. For simplicity, instances.size() is fine here.
        int activeServerCount = serviceDiscovery.getActiveServerCount(); // Example of using the new method
        logger.info("Consistent hash ring rebuilt with {} total virtual nodes from {} physical instances (reported active: {}). Current tasks: {}", newRing.size(), instances.size(), activeServerCount, taskToServerNodeIdentifierMap.keySet());

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
    public String getServerNodeForKey(String key) {
        SortedMap<Integer, String> currentRing = this.consistentHashRing; // Use local reference for thread safety
        if (currentRing.isEmpty()) {
            logger.warn("Consistent hash ring is empty. Cannot route key: {}", key);
            return null;
        }

        int keyHash = calculateHash(key);
        logger.debug("Routing key '{}' with hash {}", key, keyHash);

        SortedMap<Integer, String> tailMap = currentRing.tailMap(keyHash);

        String nodeIdentifier;
        if (tailMap.isEmpty()) {
            // If no such node, wrap around to the first node in the ring
            nodeIdentifier = currentRing.get(currentRing.firstKey());
            logger.debug("Key hash {} is beyond the last node, wrapping around to first node: {} (hash: {})", keyHash, nodeIdentifier, currentRing.firstKey());
        } else {
            nodeIdentifier = tailMap.get(tailMap.firstKey());
            logger.debug("Key hash {} mapped to node {} (hash: {})", keyHash, nodeIdentifier, tailMap.firstKey());
        }
        return nodeIdentifier;
    }

    @Override
    public boolean isLocalServerNode(String key) {
        String targetNodeIdentifier = getServerNodeForKey(key);
        if (targetNodeIdentifier == null) {
            logger.warn("Cannot determine if key '{}' is local; no target node found.", key);
            return false; // Or throw an exception, depending on desired behavior
        }
        boolean isLocal = this.localNodeIdentifier.equals(targetNodeIdentifier);
        logger.debug("Key '{}' maps to node {}. Local node is {}. Is local: {}", key, targetNodeIdentifier, this.localNodeIdentifier, isLocal);
        return isLocal;
    }
    /**
     * Returns a copy of the current mapping from task ID to server node identifier (ip:port).
     * @return A map of task IDs to their corresponding node identifiers.
     */
    public Map<String, String> getTaskToServerNodeMap() {
        return new ConcurrentHashMap<>(this.taskToServerNodeIdentifierMap);
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
}