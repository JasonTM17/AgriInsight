# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk@sha256:da9d3a4f7650db39b918fc5a2c3da76556fb8cc8e5f3767cdea0bb409286951a AS build

RUN apt-get update \
    && apt-get install --yes --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -Dmaven.repo.local=/tmp/m2-repository -DskipTests dependency:go-offline
COPY src/ src/
RUN ./mvnw -Dmaven.repo.local=/tmp/m2-repository -DskipTests package

FROM eclipse-temurin:21-jre@sha256:273396ed5998598ed1091e8d72711c2d36980a0e65103859c55a4e977a41ffd3

ARG OCI_SOURCE="https://github.com/JasonTM17/AgriInsight"
ARG OCI_REVISION="unknown"
ARG OCI_VERSION="0.1.0"

LABEL org.opencontainers.image.title="AgriInsight Backend" \
      org.opencontainers.image.description="Tenant-safe operational agriculture API" \
      org.opencontainers.image.source=$OCI_SOURCE \
      org.opencontainers.image.revision=$OCI_REVISION \
      org.opencontainers.image.version=$OCI_VERSION \
      org.opencontainers.image.licenses="MIT"

WORKDIR /app
ENV AGRIINSIGHT_SERVER_ADDRESS=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
RUN groupadd --system --gid 10001 agriinsight \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin agriinsight
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/app.jar
USER 10001:10001

EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
