package nisran.router;

import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;

import nisran.ServerInstance;

import java.util.List;

import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;



// This class is a placeholder for the router implementation.
// It can be extended to include methods for routing requests, managing server instances, 
// and implementing consistent hashing algorithms.

//@Component("router") // Make CacheRouter a Spring-managed bean
//@DependsOn("serviceRegistration") // Ensure serviceDiscoveryOperations is initialized before CacheRoute
//@Profile("cluster") // Activate this bean only when 'cluster' profile is active
public class RouterImpl implements CHRoutingService, QuorumRWService {

    // This is the AWS service discovery client that will be used to discover services in the cluster.
    private final ServiceDiscoveryClient serviceDiscoveryClient;

    // This is a placeholder for the consistent hash ring.
    // It can be implemented using a suitable data structure.
    // private ConsistentHashRing consistentHashRing;

    // This is a placeholder for the server dictionary.
    // It can be implemented using a suitable data structure.
    // private Map<String, ServerInstance> serverDictionary;

   //Constructor will argument AWS service discovery
    public RouterImpl(ServiceDiscoveryClient serviceDiscoveryClient) {
        // Initialize the router with the provided service discovery client
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        // Initialize the consistent hash ring and server dictionary here if needed
    }

    //Post constructor initialization of scheduler for 
    // periodic tasks like updating server dictionary and building consistent hash ring
    public void init() {
        // Initialize the router, e.g., by creating or updating the server dictionary
        //createOrUpdateServerDictionary();
        buildConsistentHashRing();
    }
    @Override
    public void createOrUpdateServerDictionary(List<ServerInstance> serverInstances) {
        // Implementation for creating or updating the server dictionary
        // This typically involves service discovery to find all active server instances
        // and populating the serverDictionary map.
        return;
    }
    @Override
    public void buildConsistentHashRing() {
        // Implementation for building or rebuilding the consistent hash ring
        // based on the current server dictionary.
        // This allows for mapping keys to specific server nodes.
    }
    @Override
    public ServerInstance getServerInstanceForKey(String key) {
        // Implementation for routing requests to the appropriate server instance
        // based on the consistent hashing algorithm.
        // It takes a key and returns the server instance that should handle the request.
        return null; // Placeholder return statement
    }
    @Override
    public boolean isLocalServerNode(String key) {
        // Implementation for checking if the key is mapped to the local server instance
        // to which this router service is attached.
        return false; // Placeholder return statement
    }
    @Override
    public void addServerInstance(ServerInstance serverInstance) {
        // Implementation for adding a new server instance to the consistent hash ring
        // This allows the router to include new nodes in the routing logic.
    }
    
    @Override
    public void rebalanceKeys() {
        // Implementation for rebalancing the keys across the server instances
        // in the consistent hash ring. This is useful when nodes are added or removed.
    }
    // Additional methods for the QuorumRWService can be implemented here
    // For example, methods for quorum reads and writes in a distributed system can be added.   
    @Override
    public Object quorumRead(String key) {
        // Implementation for performing a quorum read operation
        // It should ensure that the read operation is performed on a majority of nodes
        // in the distributed system to ensure consistency.
        return null; // Placeholder return statement
    }
    @Override
    public void quorumWrite(String key, Object value) {
        // Implementation for performing a quorum write operation
        // It should ensure that the write operation is performed on a majority of nodes
        // in the distributed system to ensure consistency.
    }
    @Override
    public String toString() {
        return "RouterImpl{" +
                "serviceDiscoveryClient=" + serviceDiscoveryClient +
                // Add other fields if necessary
                '}';
    }
    @Override
    public int getActiveServerCount(){
        return 0;
    }

}
