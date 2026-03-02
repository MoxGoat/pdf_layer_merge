# ---- Stage 1: Build ----
# Use a full JDK + Maven image to compile and package the app
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml first so Maven can download dependencies (cached unless pom changes)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source code and build the JAR
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- Stage 2: Run ----
# Use a slim JRE-only image — no Maven, no source code, just the JAR
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/pdf-layer-merge-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
