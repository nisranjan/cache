package nisran.router;

/**
 * Interface defining the contract for routing services.
 */
public interface RouterService {

    /**
     * Creates or updates an internal dictionary/list of all active server instances
     * in the cluster. This typically involves service discovery.
     */
    void createOrUpdateServerDictionary();

    /**
     * Builds or rebuilds the consistent hash ring based on the current server dictionary.
     * This allows for mapping keys to specific server nodes.
     */
    void buildConsistentHashRing();

    /**
     * Given a key for a data item, determines and returns the network identifier
     * (e.g., "ip:port") of the server node where this key should reside according
     * to the consistent hashing algorithm.
     *
     * @param key The key of the data item.
     * @return The network identifier of the target server node, or null if no suitable node can be found.
     */
    String getServerNodeForKey(String key);

    /**
     * Given a key for a data item, determines if the key is mapped to the local server
     * instance to which this router service is attached.
     *
     * @param key The key of the data item.
     * @return True if the key maps to the local server instance, false otherwise.
     */
    boolean isLocalServerNode(String key);
}