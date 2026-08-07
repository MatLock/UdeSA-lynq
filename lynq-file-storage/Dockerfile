FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/lynq-file-storage.jar lynq-file-storage.jar
# Run as a non-root user (image defaults to root otherwise).
RUN addgroup -S lynq && adduser -S lynq -G lynq
USER lynq
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "lynq-file-storage.jar"]
