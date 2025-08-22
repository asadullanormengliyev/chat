# 1. Build bosqichi: gradlew bilan loyihani build qilamiz
FROM gradle:8.2-jdk17 AS build
WORKDIR /app

# Avval faqat gradle wrapper fayllarini ko‘chir (cache samaradorligi uchun)
COPY gradlew .
COPY gradle ./gradle

# Keyin butun projectni ko‘chir
COPY . .

# gradlew faylga ruxsat beramiz
RUN chmod +x gradlew

# jar fayl build qilamiz (testlarni o‘tkazmaslik uchun)
RUN ./gradlew clean build -x test


# 2. Run bosqichi: engil JDK image
FROM eclipse-temurin:17-jdk
WORKDIR /app

# build bosqichidan tayyor jar'ni ko‘chir
COPY --from=build /app/build/libs/*.jar app.jar

# App'ni ishga tushiramiz
ENTRYPOINT ["java", "-jar", "app.jar"]
