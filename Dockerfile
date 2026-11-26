# The jar is built and tested by CI (./gradlew build); the image only runs it.
FROM alpine:3.12
WORKDIR /app
COPY build/libs/notes.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
