# 먼저 ./gradlew bootJar로 build/libs에 jar를 만든 뒤 빌드한다
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
