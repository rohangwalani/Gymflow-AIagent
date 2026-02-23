# Stage 1: Build using Maven and Java 21 (Debian-based for better compatibility)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom and fetch dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime using Java 21 JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render dynamic port binding
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=${PORT:8085}"]