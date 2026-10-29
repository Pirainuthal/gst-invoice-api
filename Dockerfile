# syntax=docker/dockerfile:1

# ---- build stage -----------------------------------------------------------
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first so they are cached between source changes.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src/ src/
RUN mvn -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/gst-invoice-api-*.jar \
        extract --layers --launcher --destination target/extracted

# ---- runtime stage ---------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home /app app
USER app

COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
