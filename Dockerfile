FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -Dmaven.test.skip=true dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/java-backend-performance-lab-*.jar application.jar

USER app
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=12 \
    CMD wget -q -O - http://localhost:8080/actuator/health | grep -q UP || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
