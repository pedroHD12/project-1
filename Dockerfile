FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q test package

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home --uid 10001 mailflow
WORKDIR /app
COPY --from=build /workspace/target/mailflow-local-0.1.0-SNAPSHOT.jar app.jar
USER mailflow
ENV SPRING_PROFILES_ACTIVE=cloud \
    APP_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
