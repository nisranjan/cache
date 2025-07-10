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
        int port = awsConfig.getPort();

        ServerInstance primaryInstance = cacheRouter.getServerInstanceForKey(key);

        if (isLocalInstance(primaryInstance)) {
            // Read from local cache
            logger.debug("Reading key{} from local instance",key);
            return localCache.get(key);
        } else {
            logger.debug("Reading key{} from nodeIdentifier{}",
                            key,primaryInstance.getNodeIdentifier());    
            // Read from remote server via HTTP
            try {
                // Corrected URL to use path variable instead of query param
                String url = String.format("http://%s:%d/api/cache/%s", primaryInstance.getIpAddress(), port, key);
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
                    logger.warn("Failed to read key {} from remote server {}:{}. Status: {}", key, primaryInstance.getIpAddress(), port, response.statusCode());
                    return null;
                }
            } catch (Exception e) {
                logger.error("Exception while reading key {} from remote server {}:{}", key, primaryInstance.getIpAddress(), port, e);
                return null;
            }
        }
    }

    @Override
    public void quorumWrite(String key, Object value) {
        int quorumWrite = awsConfig.getQuorumWrite();
        int port = awsConfig.getPort();
        logger.debug("Quorum write for key: {}, value: {}, quorumWrite: {}", key, value, quorumWrite);

        ServerInstance primaryInstance = 
                    cacheRouter.getServerInstanceForKey(key);

        logger.debug("Primary instance is {} for key {}",
                primaryInstance.getServiceId(),key);

        int activeSvr = cacheRouter.getActiveServerCount();

        if (isLocalInstance(primaryInstance)) {
            // Write first copy to local cache
            localCache.set(key, value);

            logger.debug("Primary instance is local instance, "
                    + "writing key {} & value {}",key,value);

            // Write to next (quorumWrite - 1) nodes
            ServerInstance current = primaryInstance;
            for (int i = 1; i < quorumWrite && i < activeSvr; i++) {
                current = nextServerInstance(current);
                sendWriteRequest(current, key, value, port);
            }
        } else {
            // Write to quorumWrite nodes (not including local)
            ServerInstance current = primaryInstance;
            for (int i = 0; i < quorumWrite && i < activeSvr; i++) {
                sendWriteRequest(current, key, value, port);
                current = nextServerInstance(current);
            }
        }
    }

    private void sendWriteRequest(ServerInstance instance, String key, Object value, int port) {
        logger.debug("Writing key {} to instance {}",key,instance.getNodeIdentifier());
        try {
            // Corrected URL to use path variable instead of query param
            String url = String.format("http://%s:%d/api/cache/%s", instance.getIpAddress(), port, key);

            // Serialize the value object to a JSON string using ObjectMapper
            String jsonBody = objectMapper.writeValueAsString(value);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

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
    }

    /**
     * Returns the next server instance in the ring using a virtual node key.
     * @param instance The current server instance.
     * @return The next server instance.
     */
    public ServerInstance nextServerInstance(ServerInstance instance) {
        // Create a key from serverInstance using nodeIdentifier + "VN1"
        String key = instance.getNodeIdentifier() + "VN1";
        // Use the cacheRouter to get the next server instance for this key
        ServerInstance nextInstance = cacheRouter.getServerInstanceForKey(key);
        //Log a debug message
        logger.debug("Next server instance for key {}: {}", key, nextInstance);

        return nextInstance;
    }

    private boolean isLocalInstance(ServerInstance instance) {

        boolean result = instance.getServiceId().equals(svcRegistration.getServiceId())
                && instance.getIpAddress().equals(svcRegistration.getIp());
        return result;
    }
}
