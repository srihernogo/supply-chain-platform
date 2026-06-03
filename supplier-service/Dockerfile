FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/
COPY common/src common/src/
COPY supplier-service/pom.xml supplier-service/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml dependency:go-offline -pl common,supplier-service -am -q
COPY supplier-service/src supplier-service/src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml package -pl common,supplier-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /app/supplier-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
