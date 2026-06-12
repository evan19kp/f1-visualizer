# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY f1-core/pom.xml f1-core/pom.xml
COPY f1-persistence/pom.xml f1-persistence/pom.xml
COPY f1-ingestion/pom.xml f1-ingestion/pom.xml
COPY f1-ai/pom.xml f1-ai/pom.xml
COPY f1-api/pom.xml f1-api/pom.xml

RUN ./mvnw dependency:go-offline -pl f1-api -am -B

COPY f1-core/src f1-core/src
COPY f1-persistence/src f1-persistence/src
COPY f1-ingestion/src f1-ingestion/src
COPY f1-ai/src f1-ai/src
COPY f1-api/src f1-api/src

RUN ./mvnw package -pl f1-api -am -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S f1 && adduser -S f1 -G f1
USER f1

COPY --from=build /workspace/f1-api/target/f1-api-*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
