# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml and cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/linkedin-searcher-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
