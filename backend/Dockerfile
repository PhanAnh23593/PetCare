FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /app
ARG SERVICE=identity-service
COPY pom.xml eclipse-java-google-style.xml ./
COPY .mvn ./.mvn
COPY identity-service ./identity-service
COPY clinic-service ./clinic-service
COPY social-service ./social-service
COPY media-service ./media-service
COPY api-gateway ./api-gateway
# Gateway transport tests reuse the original public contract fixtures.
COPY pet-platform-service/src/test/resources/compatibility ./pet-platform-service/src/test/resources/compatibility
# Clinic/social authentication tests share the old-backend status matrix.
COPY compatibility/auth-errors.csv ./compatibility/auth-errors.csv
RUN mvn -B -ntp -pl "${SERVICE}" -am clean package
RUN cp "/app/${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar" /app/service.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/service.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
