# =============================================================================
# Trackr@Uni-Ace backend
#
# Multi-stage: dependencies are cached in their own layer so ordinary code
# changes rebuild in seconds, and the runtime image carries only a JRE.
# =============================================================================

# ---- Build ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first; this layer is reused whenever pom.xml is unchanged.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime ----------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user.
RUN addgroup -S trackr && adduser -S trackr -G trackr

COPY --from=build /build/target/*.jar app.jar

# Photo storage for local development (STORAGE_PROVIDER=local). Production uses
# object storage instead - a container filesystem does not survive a redeploy.
RUN mkdir -p /app/data/photos && chown -R trackr:trackr /app

USER trackr

# Render and similar hosts inject PORT; default to 8080 elsewhere.
ENV PORT=8080
EXPOSE 8080

# Container-aware heap sizing keeps this inside a 512 MB free instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
