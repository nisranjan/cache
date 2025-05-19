package nisran.router;

import java.util.List;

import nisran.ServerInstance;

/**
 * Interface for discovering active server instances.
 * Your ServiceDiscoveryConfig class would implement this interface.
 */
public interface ServiceDiscoveryOperations {
    /**
     * Discovers and returns a list of active server instances.
     * @return A list of {@link ServerInstance} objects.
     */
    List<ServerInstance> discoverInstances();

    /**
     * Gets the current count of active server instances.
     * This could be implemented by calling {@code discoverInstances().size()}.
     * @return The number of active server instances.
     */
    int getActiveServerCount();

    /**
     * Gets the task_id for local server instance.
     * @return The task_id.
     */
    String getTaskForLocalServer();
}