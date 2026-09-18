# -----------------------------------------------------------------------------
# Stage 1: Build backend JAR
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /workspace

# Copy maven wrapper and pom.xml
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x ./mvnw

# Copy source and package application
COPY backend/src ./src
RUN ./mvnw clean package -DskipTests -B

# -----------------------------------------------------------------------------
# Stage 2: Runtime Image
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

RUN groupadd -r gitbot && useradd -r -g gitbot gitbot

COPY --from=builder /workspace/target/*.jar /app/app.jar

USER gitbot:gitbot

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]