# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Dependencies first so a code change does not re-download the internet.
COPY server/pom.xml server/pom.xml
RUN mvn -B -q -f server/pom.xml dependency:go-offline

COPY server/src server/src

# The salon web app is served by the same server as the API, so the phone talks
# to one https origin. These are the same files ops/sync-server-web.ps1 copies.
COPY index.html api-client.js app.js styles.css manifest.webmanifest service-worker.js icon.svg server/src/main/resources/static/

RUN mvn -B -q -f server/pom.xml -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 salon
WORKDIR /app
COPY --from=build /src/server/target/ayan-salon-server-0.1.0-SNAPSHOT.jar app.jar
USER salon
EXPOSE 8080
ENV SERVER_PORT=8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
