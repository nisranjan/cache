# --- Stage 1: Build dependencies ---
FROM maven:3.9.6-eclipse-temurin-17 AS build-deps
WORKDIR /svr
COPY pom.xml .

# Download all dependencies. This layer will be cached if poms don't change.
# Use -B (batch mode) for non-interactive builds
RUN mvn -B dependency:go-offline

# --- Stage 2: Build the application ---
# Use a multi-stage build to reduce the final image size
# FROM maven:3.9.6-eclipse-temurin-17 AS build
FROM build-deps AS build-app
COPY src ./src
RUN mvn clean package -P cluster -DskipTests

# --- Stage 3: Final production image ---
# Use a lightweight JRE image for the final stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /svr
COPY --from=build-app /svr/target/*.jar cache-server.jar

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=cluster

# Expose the port the app runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "cache-server.jar"]