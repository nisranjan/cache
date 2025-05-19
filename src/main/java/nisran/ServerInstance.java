package nisran;

public class ServerInstance {
    private final String taskId;
    private final String ipAddress;
    private final int port;

    public ServerInstance(String taskId, String ipAddress, int port) {
        this.taskId = taskId;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    /**
     * Provides a unique identifier for the node on the hash ring, typically "ip:port".
     * @return String representation of the node (e.g., "192.168.1.10:8080").
     */
    public String getNodeIdentifier() {
        return ipAddress + ":" + port;
    }

    @Override
    public String toString() {
        return "ServerInstance{" + "taskId='" + taskId + '\'' + ", ipAddress='" + ipAddress + '\'' + ", port=" + port + '}';
    }
}