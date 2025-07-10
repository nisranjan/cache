package nisran;

/* This represents the service instance that is associated with this Cloud Native library
// It is used to identify the server node in the consistent hash ring.
// It contains the service ID, IP address, and port number of the server instance.

/* Ideally the Set containing all keys associate with the KV pairs should reside in this
 * 
 * Controller, (or caller) should be able to check if a key is present in the local cache
 * or not. This is to avoid unnecessary calls to the remote server.
 */

public class ServerInstance {

    //TODO: Change this field to instance id
    private  String serviceId;
    private  String ipAddress;
    private  int port;

    public ServerInstance(String serviceId, String ipAddress, int port) {
        this.serviceId = serviceId;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public String getServiceId() {
        return serviceId;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerInstance)) return false;

        ServerInstance that = (ServerInstance) o;

        if (port != that.port) return false;
        if (!serviceId.equals(that.serviceId)) return false;
        return ipAddress.equals(that.ipAddress);
    }

    @Override
    public String toString() {
        return "ServerInstance{" + "serviceId='" + serviceId + '\'' + ", ipAddress='" + ipAddress + '\'' + ", port=" + port + '}';
    }
}