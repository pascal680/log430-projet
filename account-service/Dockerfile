# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw package -pl account-service -am -DskipTests -B

# ── Run stage ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/account-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
