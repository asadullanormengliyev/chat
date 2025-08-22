# 1. Build bosqichi (gradle bilan jar fayl yasaymiz)
FROM gradle:8.2-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle clean build -x test

# 2. Run bosqichi (engil JDK image)
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
