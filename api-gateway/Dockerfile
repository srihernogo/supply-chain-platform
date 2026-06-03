# ─────────────────────────────────────────────────
# Multi-stage Docker build — API Gateway
# ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy parent pom and common module first (layer caching)
COPY pom.xml .
COPY common/pom.xml common/
COPY common/src common/src/
COPY api-gateway/pom.xml api-gateway/

# Download dependencies (cached if pom unchanged)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml dependency:go-offline -pl common,api-gateway -am -q

# Copy source and build
COPY api-gateway/src api-gateway/src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml package -pl common,api-gateway -am -DskipTests -q

# ─────────────────────────────────────────────────
# Runtime image
# ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/api-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
