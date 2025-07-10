package nisran.router;

import java.util.List;

import nisran.ServerInstance;

public interface CHRoutingService {
    /**
     * This method is used to route requests to the appropriate server instance based on the
     * consistent hashing algorithm. It takes a key and returns the server instance that should
     * handle the request.
     *
     * @param key The key to be hashed for routing.
     * @return The server instance that should handle the request.
     */ 
    ServerInstance getServerInstanceForKey(String key);
    // Implementation of consistent hashing for routing requests
    // This method creates or updates the internal dictionary/list of all active server instances
    // in the cluster. This typically involves service discovery. 
    void createOrUpdateServerDictionary(List<ServerInstance> dictionary);
    // This method builds or rebuilds the consistent hash ring based on the current server dictionary.
    // This allows for mapping keys to specific server nodes.   
    void buildConsistentHashRing();
    // This method checks if the key is mapped to the local server instance to which this router service is attached.
    boolean isLocalServerNode(String key);
    // This method allows the router to add a new server instance to the consistent hash ring.
    void addServerInstance(ServerInstance serverInstance);
    // This method allows the router to remove a server instance from the consistent hash ring.
    //void removeServerInstance(ServerInstance serverInstance);
    // This method allows the router to update the server instance in the consistent hash ring.
    //void updateServerInstance(ServerInstance serverInstance);
    // This method will rebalance the keys across the server instances in the consistent hash ring.
    void rebalanceKeys();
    // This method will show the number of physical services in cluste
    int getActiveServerCount();
    
}
  