FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

# Module name (e.g. api-gateway, product-service, eureka-server, ...)
ARG SERVICE

COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY pom.xml pom.xml
COPY common-lib/ common-lib/
COPY eureka-server/ eureka-server/
COPY api-gateway/ api-gateway/
COPY auth-service/ auth-service/
COPY product-service/ product-service/
COPY warehouse-service/ warehouse-service/
COPY inbound-service/ inbound-service/
COPY outbound-service/ outbound-service/

RUN chmod +x mvnw \
  && ./mvnw -q -DskipTests -pl "${SERVICE}" -am package


FROM eclipse-temurin:17-jre

WORKDIR /app

ARG SERVICE
COPY --from=build /workspace/${SERVICE}/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
