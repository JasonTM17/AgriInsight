# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -Dmaven.repo.local=/tmp/m2-repository -DskipTests dependency:go-offline
COPY src/ src/
RUN ./mvnw -Dmaven.repo.local=/tmp/m2-repository -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
ENV AGRIINSIGHT_SERVER_ADDRESS=0.0.0.0
RUN groupadd --system --gid 10001 agriinsight \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin agriinsight
COPY --from=build /workspace/target/*.jar /app/app.jar
USER 10001:10001

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
