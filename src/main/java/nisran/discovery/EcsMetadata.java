package nisran.discovery;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.context.annotation.ApplicationScope;

/**
 * Represents essential ECS task metadata as a Spring-managed bean.
 * This bean is populated during service initialization and shared across components.
 */
@Component
@Profile("cluster")
@ApplicationScope
public class EcsMetadata {

    private String taskArn;
    private String clusterArn;
    private String privateIpv4Address;
    private String taskId;
    private String serviceId;
    private String namespaceId;
    private String namespaceName;
    private String serviceName;
    private int port;

    // Default constructor for Spring
    public EcsMetadata() {}

    public EcsMetadata(String taskArn, String clusterArn, String privateIpv4Address) {
        this.taskArn = taskArn;
        this.clusterArn = clusterArn;
        this.privateIpv4Address = privateIpv4Address;
        if (taskArn != null) {
            this.taskId = taskArn.substring(taskArn.lastIndexOf("/") + 1);
        }
    }

    // Getters
    public String getTaskArn() { return taskArn; }
    public String getClusterArn() { return clusterArn; }
    public String getPrivateIpv4Address() { return privateIpv4Address; }
    public String getTaskId() { return taskId; }
    public String getServiceId() { return serviceId; }
    public String getNamespaceId() { return namespaceId; }
    public String getNamespaceName() { return namespaceName; }
    public String getServiceName() { return serviceName; }
    public int getPort() { return port; }

    // Setters for updating metadata as it becomes available
    public void setTaskArn(String taskArn) { 
        this.taskArn = taskArn; 
        if (taskArn != null) {
            this.taskId = taskArn.substring(taskArn.lastIndexOf("/") + 1);
        }
    }
    public void setClusterArn(String clusterArn) { this.clusterArn = clusterArn; }
    public void setPrivateIpv4Address(String privateIpv4Address) { this.privateIpv4Address = privateIpv4Address; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public void setNamespaceId(String namespaceId) { this.namespaceId = namespaceId; }
    public void setNamespaceName(String namespaceName) { this.namespaceName = namespaceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public void setPort(int port) { this.port = port; }

    public boolean isComplete() {
        return taskArn != null && clusterArn != null && privateIpv4Address != null && 
               serviceId != null && namespaceId != null;
    }

    @Override
    public String toString() {
        return "EcsMetadata{" +
                "taskArn='" + taskArn + '\'' +
                ", clusterArn='" + clusterArn + '\'' +
                ", privateIpv4Address='" + privateIpv4Address + '\'' +
                ", taskId='" + taskId + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", namespaceId='" + namespaceId + '\'' +
                ", namespaceName='" + namespaceName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", port=" + port +
                '}';
    }
}