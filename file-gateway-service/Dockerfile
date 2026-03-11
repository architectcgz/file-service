# ===========================================
# File Gateway Service Dockerfile
# ===========================================

FROM eclipse-temurin:17-jdk-alpine

LABEL maintainer="Blog Team"
LABEL service="file-gateway-service"

ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata curl && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

RUN mkdir -p /app /app/logs

WORKDIR /app

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

ENV SERVER_PORT=8090
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -sf http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar app.jar"]
