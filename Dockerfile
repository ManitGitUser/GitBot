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

# Limit glibc memory arena fragmentation (critical for memory-constrained containers <= 512MB)
ENV MALLOC_ARENA_MAX=2

# Install curl for reliable container health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

RUN groupadd -r gitbot && useradd -r -g gitbot gitbot

COPY --from=builder /workspace/target/*.jar /app/app.jar

USER gitbot:gitbot

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD curl -f -s http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xms160m", "-Xmx200m", "-XX:MaxMetaspaceSize=96m", "-XX:ReservedCodeCacheSize=32m", "-Xss384k", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]