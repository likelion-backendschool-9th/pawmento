# 백엔드 이미지. backend/ 폴더를 빌드 컨텍스트로 쓴다: docker build -t pawmento-backend backend
# 실행할 때 DB 접속 정보(SPRING_DATASOURCE_*)와 파일 저장 위치(PAWMENTO_MEDIA_ROOT)를 환경 변수로 넘긴다 (compose.yaml).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# 의존성 정의만 먼저 복사해, 소스만 바뀌었을 때는 의존성 다운로드 층을 캐시로 재사용한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
# 테스트는 CI의 Backend test에서 돌리므로 이미지 빌드에서는 건너뛴다
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
# 사진·음성 파일 저장 폴더를 실행 사용자(app) 소유로 만든다. 처음 연결되는 Docker 볼륨이 이 소유권을 이어받아,
# root가 아닌 백엔드가 볼륨에 쓸 수 있다 (#47)
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /var/lib/pawmento/media \
    && chown app:app /var/lib/pawmento/media
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
