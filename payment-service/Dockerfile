# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw package -pl payment-service -am -DskipTests -B

# ── Run stage ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/payment-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
