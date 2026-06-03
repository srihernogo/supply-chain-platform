FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/
COPY common/src common/src/
COPY blockchain-ledger-service/pom.xml blockchain-ledger-service/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml dependency:go-offline -pl common,blockchain-ledger-service -am -q
COPY blockchain-ledger-service/src blockchain-ledger-service/src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml package -pl common,blockchain-ledger-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /app/blockchain-ledger-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
