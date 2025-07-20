package nisran.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.CustomHealthStatus;
import software.amazon.awssdk.services.servicediscovery.model.InstanceNotFoundException;
import software.amazon.awssdk.services.servicediscovery.model.ServiceNotFoundException;
import software.amazon.awssdk.services.servicediscovery.model.UpdateInstanceCustomHealthStatusRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * Manages sending heartbeats to Cloud Map for service instances.
 * This service depends on ServiceRegistration to complete initialization first.
 */
@Service
@Profile("cluster")
@DependsOn("serviceRegistration")
public class HeartbeatService {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);

    private final ServiceDiscoveryClient serviceDiscoveryClient;
    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> heartbeatTask;

    @Autowired
    private EcsMetadata ecsMetadata;

    @Value("${cache.service.discovery.heartbeat-interval}")
    private String heartbeatInterval;

    public HeartbeatService(ServiceDiscoveryClient serviceDiscoveryClient,
                            TaskScheduler taskScheduler) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Starts sending heartbeats at a fixed interval.
     * This method should be called after ServiceRegistration has completed.
     */
    @PostConstruct
    public void start() {
        if (heartbeatTask == null || heartbeatTask.isCancelled()) {
            Duration interval = parseDuration(heartbeatInterval);
            PeriodicTrigger trigger = new PeriodicTrigger(interval);
            trigger.setInitialDelay(interval); // Start immediately

            heartbeatTask = taskScheduler.schedule(this::sendHeartbeat, trigger);
            logger.info("Started sending heartbeats every {} seconds.", interval.getSeconds());
        }
    }

    /**
     * Stops sending heartbeats.
     */
    @PreDestroy
    public void stop() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(false);
            logger.info("Stopped sending heartbeats.");
        }
    }

    private Duration parseDuration(String intervalString) {
        // Assuming the interval is provided in seconds (e.g., "PT10S" for 10 seconds)
        return Duration.ofSeconds(Long.parseLong(intervalString.replaceAll("[^\\d]", "")));
    }

    /**
     * Sends a single heartbeat to Cloud Map.
     */
    private void sendHeartbeat() {
        String instanceId = ecsMetadata.getTaskId();
        String serviceId = ecsMetadata.getServiceId();

        if (instanceId == null || serviceId == null) {
            logger.warn("Cannot send heartbeat because instanceId or serviceId is null. This may be temporary during startup.");
            return;
        }

        try {
            logger.debug("Sending heartbeat for instance: {} in service: {}", instanceId, serviceId);
            UpdateInstanceCustomHealthStatusRequest request = UpdateInstanceCustomHealthStatusRequest.builder()
                    .serviceId(serviceId)
                    .instanceId(instanceId)
                    .status(CustomHealthStatus.HEALTHY)
                    .build();

            serviceDiscoveryClient.updateInstanceCustomHealthStatus(request);
            logger.debug("Heartbeat sent successfully for instance: {}", instanceId);
        } catch (InstanceNotFoundException | ServiceNotFoundException e) {
            logger.warn("Instance or Service not found during heartbeat for instanceId: {}. It might have been deregistered. Stopping heartbeats.", instanceId, e);
            stop();
        } catch (Exception e) {
            logger.error("Failed to send heartbeat for instance: {}", instanceId, e);
        }
    }
}