# The jar is built and tested by CI (./gradlew build); the image only runs it.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/libs/notes.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
