# syntax=docker/dockerfile:1

# --------------------------------------------------
# 1. Build stage
# --------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Gradle Wrapper와 빌드 설정을 먼저 복사해
# 애플리케이션 소스 변경 시 의존성 레이어를 재사용한다.
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon

# 소스 코드 복사 및 실행 가능한 Spring Boot JAR 생성
COPY src src

RUN ./gradlew clean bootJar --no-daemon


# --------------------------------------------------
# 2. Runtime stage
# --------------------------------------------------
FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder \
    --chown=10001:10001 \
    /workspace/build/libs/app.jar \
    /app/app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]