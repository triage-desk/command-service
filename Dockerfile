FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as non-root user for security hardening
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the pre-built fat JAR from target/
COPY --chown=appuser:appgroup target/command-service.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
