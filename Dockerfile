# ---- Stage 1: Build ----
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B 2>/dev/null || true

COPY src ./src
RUN mvn clean package -DskipTests -B && \
    java -Djarmode=layertools -jar target/*.jar extract

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S sattrack && adduser -S sattrack -G sattrack
RUN apk add --no-cache curl

WORKDIR /app

COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

USER sattrack
EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
