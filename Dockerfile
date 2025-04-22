# Use a multi-stage build to reduce the final image size
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /svr
COPY pom.xml .
COPY src ./src
RUN mvn clean package -P cluster -DskipTests

# Use a lightweight JRE image for the final stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /svr
COPY --from=build /svr/target/*.jar cache-server.jar

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=cluster

# Expose the port the app runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar cache-server.jar"] 