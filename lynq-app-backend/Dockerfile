FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/lynq-app-backend.jar lynq-app-backend.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "lynq-app-backend.jar"]