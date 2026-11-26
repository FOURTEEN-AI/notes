# The jar is built and tested by CI (./gradlew build); the image only runs it.
FROM eclipse-temurin:25-jre@sha256:4ed132f6832e9804b123e89a849a7b4502756ae64a4ee08ebab939d9d5d0a9fa
WORKDIR /app
COPY build/libs/notes.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
