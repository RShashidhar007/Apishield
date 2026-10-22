# Shared multi-stage build for every Java module. Build context = repository root.
#   docker build -f infra/Dockerfile.java --build-arg MODULE=backend -t apishield-backend .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY common common
COPY backend backend
COPY gateway gateway
COPY examples examples
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG MODULE
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /src/${MODULE}/target/*.jar /app/app.jar
USER 10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
