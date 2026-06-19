# ==========================================
# Stage 1: Build the application
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and project files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src ./src

# Build the JAR file
RUN ./mvnw clean package -DskipTests

# ==========================================
# Stage 2: Run the application
# ==========================================
# Use a lightweight JRE for the final image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy ONLY the built JAR from the 'builder' stage
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]