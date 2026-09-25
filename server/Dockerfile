# Production image for the Spring API. Build from the repository root with
# `docker compose build server`.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --create-home --uid 10001 ayan
COPY --from=build /workspace/target/*.jar /app/ayan-salon-server.jar
USER ayan
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/ayan-salon-server.jar"]
