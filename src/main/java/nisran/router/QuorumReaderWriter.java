package nisran.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import nisran.ServerInstance;
import nisran.cache.LRUCache;
import nisran.config.AWS_SDKConfig;
import nisran.discovery.ServiceRegistration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import ch.qos.logback.classic.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.ArrayList;

@Service("quorumReaderWriter")
@Profile("cluster") // This service is only active when the 'cluster' profile is active
@DependsOn("cacheRouter") // Ensure cacheRouter is initialized before this service
public class QuorumReaderWriter implements QuorumRWService {

    private static Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(QuorumReaderWriter.class);

    @Autowired
    private LRUCache<String, Object> localCache;

    @Autowired
    private ServiceRegistration svcRegistration;

    @Autowired
    private CacheRouter cacheRouter;

    @Autowired
    private HttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AWS_SDKConfig awsConfig;

    private ServerInstance localInstance;

    @Override
    public Object quorumRead(String key) {

        Object response = null;

        int port = awsConfig.getPort(); //TODO : optimize this code
        ServerInstance primaryInstance = cacheRouter.getServerInstanceForKey(key);

        if (isLocalInstance(primaryInstance)) {
            // Read from local cache
            response = remoteReadOnce(primaryInstance, key, port);
        } else {
            // Send a single remote read request 
            // Method is a blocking method
            response = remoteReadOnce(primaryInstance, key, port);
        }

        return response;
    }

    @Override
    public List<String> quorumWrite(String key, Object value) {

        List<String> response = new ArrayList<String>();
        
        int quorumWrite = awsConfig.getQuorumWrite();
        int port = awsConfig.getPort();
        logger.debug("Quorum write for key: {}, value: {}, quorumWrite: {}", key, value, quorumWrite);

        ServerInstance primaryInstance = cacheRouter.getServerInstanceForKey(key);

        if (primaryInstance == null) {
            response.add("Failure: No server instance found for key " + key);
            logger.error("Could not find a server instance for key: {}", key);
            return response;
        }
        logger.debug("Primary instance is {} for key {}",primaryInstance.getServiceId(),key);

        

        if (isLocalInstance(primaryInstance)) {
            //This node is the primary
            // Write first copy to local cache, the replicate
            localWrite(key, value);

            response.add(String.format("Success: Wrote to primary %s (local)", primaryInstance.getNodeIdentifier()));

            // Write to next (quorumWrite - 1) nodes
            ServerInstance current = primaryInstance;
            int activeSvr = cacheRouter.getActiveServerCount();

            for (int i = 1; i < quorumWrite && i < activeSvr; i++) {
                current = nextServerInstance(current);

                if (isLocalInstance(current)) {
                    continue; // Skip writing to self again if cluster is small
                }

                HttpResponse<String> res = remoteWrite(current, key, value, port, false);
                response.add(String.format("Response to server{} is {}",current.getNodeIdentifier(),res.statusCode()));
            }
        } else {
            // Forward quorumWrite to relevant node (not including local)
            logger.debug("Forwarding Write to instance {} for key {}",primaryInstance.getServiceId(),key);
            ServerInstance current = primaryInstance;
            HttpResponse<String> res = remoteWrite(primaryInstance, key, value, port, true);
            response.add(String.format("Response to server{} is {}",current.getNodeIdentifier(),res.statusCode()));
           
        }

        return response;
    }

    public Object localRead(String key) {
        logger.debug("Reading key{} from local instance",key);
        return localCache.get(key);
    }

    public void localWrite(String key, Object value) {
        logger.debug("Writing key{} value{} to local instance",key,value);
        localCache.set(key, value);
    }

    private Object remoteReadOnce(ServerInstance instance, String key, int port) {
        logger.debug("Reading key{} from nodeIdentifier{}",
                            key,instance.getNodeIdentifier());    
        // Read from remote server via HTTP
        try {
            // Corrected URL to use path variable instead of query param
            String url = String.format("http://%s:%d/api/cache/local/%s", instance.getIpAddress(), port, key);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Deserialize the JSON response body back to an Object
                return objectMapper.readValue(response.body(), Object.class);
            } else {
                logger.warn("Failed to read key {} from remote server {}:{}. Status: {}", key, instance.getIpAddress(), port, response.statusCode());
                return null;
            }
        } catch (Exception e) {
            logger.error("Exception while reading key {} from remote server {}:{}", key, instance.getIpAddress(), port, e);
            return null;
        }
    }

    private HttpResponse<String> remoteWrite(ServerInstance instance, String key, Object value, int port, boolean isForward) {
        
        HttpResponse<String> response = null;
        logger.debug("Writing key {} to instance {}",key,instance.getNodeIdentifier());
        try {
            String url = null;
            if(!isForward){
                // Corrected URL to use path variable instead of query param
                url = String.format("http://%s:%d/api/cache/local/%s", instance.getIpAddress(), port, key);
            }else{
                url = String.format("http://%s:%d/api/cache/%s", instance.getIpAddress(), port, key);
            }
            

            // Serialize the value object to a JSON string using ObjectMapper
            String jsonBody = objectMapper.writeValueAsString(value);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Check for successful status codes (e.g., 2xx)
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.debug("Successfully wrote key {} to instance {}", key, instance.getNodeIdentifier());
            } else {
                logger.warn("Failed to write key {} to instance {}. Status: {}, Body: {}",
                        key, instance.getNodeIdentifier(), response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("Exception while sending write request for key {} to instance {}", key, instance.getNodeIdentifier(), e);
        }

        return response;
    }


    /**
     * Returns the next server instance in the ring using a virtual node key.
     * @param instance The current server instance.
     * @return The next server instance.
     */
    public ServerInstance nextServerInstance(ServerInstance instance) {
        List<ServerInstance> activeInstances = cacheRouter.getActiveServerInstances();
        if (activeInstances.size() < 2) {
            return instance; // Not enough instances to find a "next" one.
        }

        int currentIndex = -1;
        for (int i = 0; i < activeInstances.size(); i++) {
            if (activeInstances.get(i).getServiceId().equals(instance.getServiceId())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            logger.warn("Current instance {} not found in active list. Falling back to first instance.", instance.getNodeIdentifier());
            return activeInstances.get(0);
        }

        int nextIndex = (currentIndex + 1) % activeInstances.size();
        ServerInstance nextInstance = activeInstances.get(nextIndex);
        
        logger.debug("Current instance: {}, Next server instance: {}", instance.getNodeIdentifier(), nextInstance.getNodeIdentifier());

        return nextInstance;
    }

    private boolean isLocalInstance(ServerInstance instance) {

        logger.debug("Instance instance id {} and IP {}",
                instance.getServiceId(),instance.getIpAddress());

        logger.debug("SvcRegistration instance id {} and IP {}",
                svcRegistration.getAwsTaskId(),svcRegistration.getIp());

        boolean result = instance.getServiceId().equalsIgnoreCase(svcRegistration.getAwsTaskId())
                && instance.getIpAddress().equalsIgnoreCase(svcRegistration.getIp());
        return result;
    }
}
