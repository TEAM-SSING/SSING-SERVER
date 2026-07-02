# syntax=docker/dockerfile:1.7

# 빌드 전용 JDK 21 이미지
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Gradle 의존성 캐시 경계
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && (./gradlew --no-daemon dependencies >/tmp/gradle-dependencies.log \
        || (cat /tmp/gradle-dependencies.log && false))

# 애플리케이션 소스 복사와 Spring Boot 실행 JAR 생성
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# 실행 전용 JRE 21 이미지
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 컨테이너 내부 non-root 실행 계정
RUN addgroup --system ssing && adduser --system --ingroup ssing ssing

# 빌드 산출물만 포함하는 런타임 이미지
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

# 최소 권한 실행과 Spring Boot 기본 포트
USER ssing:ssing
EXPOSE 8080

# EC2 메모리 옵션 주입 지점과 애플리케이션 시작 명령
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
