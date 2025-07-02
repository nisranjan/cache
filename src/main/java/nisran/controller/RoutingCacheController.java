package nisran.controller;

import nisran.ServerInstance;
import nisran.cache.LRUCache;
import nisran.discovery.ServiceDiscoveryOperations;
import nisran.router.CacheRouter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@RestController
@Profile("cluster") // Active when 'cluster' profile is active
@RequestMapping("/api/cache")
public class RoutingCacheController {

    private static final Logger logger = LoggerFactory.getLogger(RoutingCacheController.class);

    private final LRUCache<String, Object> localCache;
    private final CacheRouter router;
    private final ServiceDiscoveryOperations serviceDiscovery;
    private final RestTemplate restTemplate;

    @Autowired
    public RoutingCacheController(LRUCache<String, Object> localCache,
                                  CacheRouter router,
                                  ServiceDiscoveryOperations serviceDiscovery,
                                  RestTemplate restTemplate) {
        this.localCache = localCache;
        this.router = router;
        this.serviceDiscovery = serviceDiscovery;
        this.restTemplate = restTemplate;
    }

    private static class NodeNetworkDetails {
        final String ip;
        final int port;

        NodeNetworkDetails(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }

    private Optional<NodeNetworkDetails> getRemoteNodeDetails(String targetNodeIdentifier) {
        if (targetNodeIdentifier == null) {
            logger.warn("Target node identifier is null, cannot find remote node details.");
            return Optional.empty();
        }

        List<ServerInstance> allInstances = serviceDiscovery.discoverInstances();
        Optional<ServerInstance> remoteInstanceOpt = allInstances.stream()
                .filter(instance -> targetNodeIdentifier.equals(instance.getNodeIdentifier()))
                .findFirst();

        if (remoteInstanceOpt.isPresent()) {
            ServerInstance remoteInstance = remoteInstanceOpt.get();
            return Optional.of(new NodeNetworkDetails(remoteInstance.getIpAddress(), remoteInstance.getPort()));
        } else {
            logger.warn("Node with identifier '{}' (from CacheRouter) not found in discovered instances. Discovery list size: {}",
                    targetNodeIdentifier, allInstances.size());
            return Optional.empty();
        }
    }

    @GetMapping("/{key}")
    public ResponseEntity<Object> get(@PathVariable String key) {
        String targetNodeIdentifier = router.getServerNodeForKey(key);

        if (targetNodeIdentifier == null) {
            logger.warn("No suitable cache node found by router for key: {}", key);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("No suitable cache node found.");
        }

        if (router.isLocalServerNode(key)) {
            logger.debug("Key '{}' maps to local node. Getting from local cache.", key);
            Object value = localCache.get(key);
            if (value == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(value);
        } else {
            logger.debug("Key '{}' maps to remote node: {}. Forwarding GET request.", key, targetNodeIdentifier);
            Optional<NodeNetworkDetails> remoteDetailsOpt = getRemoteNodeDetails(targetNodeIdentifier);
            if (remoteDetailsOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Target cache node (" + targetNodeIdentifier + ") details not found in service discovery.");
            }
            NodeNetworkDetails remoteDetails = remoteDetailsOpt.get();
            String remoteUrl = String.format("http://%s:%d/api/cache/%s", remoteDetails.ip, remoteDetails.port, key);
            try {
                return restTemplate.getForEntity(remoteUrl, Object.class);
            } catch (HttpClientErrorException e) {
                logger.warn("Client error from remote node {} for key '{}': {} {}", targetNodeIdentifier, key, e.getStatusCode(), e.getResponseBodyAsString());
                return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsByteArray());
            } catch (HttpServerErrorException e) {
                logger.error("Server error from remote node {} for key '{}': {} {}", targetNodeIdentifier, key, e.getStatusCode(), e.getResponseBodyAsString());
                return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsByteArray());
            } catch (ResourceAccessException e) {
                logger.error("Could not access remote node {} for key '{}': {}", targetNodeIdentifier, key, e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Remote cache node unavailable.");
            }
        }
    }

    @PostMapping("/{key}")
    public ResponseEntity<Void> set(@PathVariable String key, @RequestBody Object value) {
        String targetNodeIdentifier = router.getServerNodeForKey(key);

        if (targetNodeIdentifier == null) {
            logger.warn("No suitable cache node found by router for key: {}", key);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (router.isLocalServerNode(key)) {
            logger.debug("Key '{}' maps to local node. Setting in local cache.", key);
            localCache.set(key, value);
            return ResponseEntity.ok().build();
        } else {
            logger.debug("Key '{}' maps to remote node: {}. Forwarding SET request.", key, targetNodeIdentifier);
            Optional<NodeNetworkDetails> remoteDetailsOpt = getRemoteNodeDetails(targetNodeIdentifier);
            if (remoteDetailsOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            NodeNetworkDetails remoteDetails = remoteDetailsOpt.get();
            String remoteUrl = String.format("http://%s:%d/api/cache/%s", remoteDetails.ip, remoteDetails.port, key);
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON); // Assuming JSON, adjust if necessary
                HttpEntity<Object> requestEntity = new HttpEntity<>(value, headers);
                return restTemplate.exchange(remoteUrl, HttpMethod.POST, requestEntity, Void.class);
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                logger.error("Error forwarding SET request to remote node {} for key '{}': {} {}", targetNodeIdentifier, key, e.getStatusCode(), e.getResponseBodyAsString());
                return ResponseEntity.status(e.getStatusCode()).build();
            } catch (ResourceAccessException e) {
                logger.error("Could not access remote node {} for key '{}' during SET: {}", targetNodeIdentifier, key, e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
        }
    }
}