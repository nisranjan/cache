package nisran.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.Task;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for retrieving ECS task metadata.
 */
@Service
@DependsOn("otherServices")
@Profile("cluster")
public class EcsMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(EcsMetadataService.class);
    private static final String ECS_METADATA_URI_ENV_VAR = "ECS_CONTAINER_METADATA_URI_V4";
    private static final int MAX_METADATA_RETRIES = 5;
    private static final int INITIAL_DELAY_MILLIS = 500;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EcsClient ecsClient;

    @Autowired
    public EcsMetadataService(HttpClient httpClient, ObjectMapper objectMapper, EcsClient ecsClient) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.ecsClient = ecsClient;
    }

    /**
     * Fetches the complete ECS task metadata, including ARNs and the private IP address.
     * It first calls the ECS metadata endpoint and then uses the returned ARNs to describe
     * the task and find its private IP. Both steps include retry logic.
     *
     * @return An Optional containing the EcsMetadata if all information is successfully retrieved, otherwise an empty Optional.
     */
    public Optional<EcsMetadata> fetchMetadata() {
        return fetchTaskAndClusterArn().flatMap(arns -> {
            String taskArn = arns.get("taskArn");
            String clusterArn = arns.get("clusterArn");
            return getPrivateIp(taskArn, clusterArn)
                    .map(ip -> new EcsMetadata(taskArn, clusterArn, ip));
        });
    }

    private Optional<Map<String, String>> fetchTaskAndClusterArn() {
        String metadataUri = System.getenv(ECS_METADATA_URI_ENV_VAR);
        if (metadataUri == null || metadataUri.trim().isEmpty()) {
            logger.error("{} environment variable not set.", ECS_METADATA_URI_ENV_VAR);
            return Optional.empty();
        }

        String taskMetadataUrl = metadataUri + "/task";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(taskMetadataUrl))
                .header("Accept", "application/json")
                .build();

        for (int attempt = 1; attempt <= MAX_METADATA_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    String taskArn = root.path("TaskARN").asText(null);
                    String clusterArn = root.path("Cluster").asText(null);
                    if (taskArn != null && clusterArn != null) {
                        logger.info("Successfully fetched Task ARN {} and Cluster ARN {}", taskArn, clusterArn);
                        return Optional.of(Map.of("taskArn", taskArn, "clusterArn", clusterArn));
                    }
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Error fetching task metadata from {}", taskMetadataUrl, e);
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }

            try {
                TimeUnit.MILLISECONDS.sleep(INITIAL_DELAY_MILLIS * (long) Math.pow(2, attempt - 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        logger.error("Failed to fetch task and cluster ARN after {} retries.", MAX_METADATA_RETRIES);
        return Optional.empty();
    }

    private Optional<String> getPrivateIp(String taskArn, String clusterArn) {
        DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
                .cluster(clusterArn)
                .tasks(taskArn)
                .build();

        for (int attempt = 1; attempt <= MAX_METADATA_RETRIES; attempt++) {
            try {
                DescribeTasksResponse describeTasksResponse = ecsClient.describeTasks(describeTasksRequest);
                if (!describeTasksResponse.tasks().isEmpty()) {
                    Task task = describeTasksResponse.tasks().get(0);
                    Optional<String> ip = task.attachments().stream()
                            .filter(att -> "ElasticNetworkInterface".equals(att.type()))
                            .flatMap(att -> att.details().stream())
                            .filter(det -> "privateIPv4Address".equals(det.name()))
                            .map(det -> det.value())
                            .findFirst();

                    if (ip.isPresent()) {
                        logger.info("Successfully fetched private IP {} for task {}", ip.get(), taskArn);
                        return ip;
                    }
                }
            } catch (Exception e) {
                logger.error("Exception while trying to describe ECS task {}", taskArn, e);
            }

            try {
                logger.warn("Could not find private IP for task {}. Retrying... (Attempt {}/{})", taskArn, attempt, MAX_METADATA_RETRIES);
                TimeUnit.MILLISECONDS.sleep(INITIAL_DELAY_MILLIS * (long) Math.pow(2, attempt - 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        logger.error("Failed to get private IP for task {} after {} retries.", taskArn, MAX_METADATA_RETRIES);
        return Optional.empty();
    }
}