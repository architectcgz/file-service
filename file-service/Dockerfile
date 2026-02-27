# ===========================================
# File Service Dockerfile
# ===========================================

FROM eclipse-temurin:17-jdk-alpine

LABEL maintainer="Blog Team"
LABEL service="file-service"

# 设置时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata curl && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 创建应用目录
RUN mkdir -p /app /app/logs /app/uploads

WORKDIR /app

# 复制 JAR 文件
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# 环境变量
ENV SERVER_PORT=8008
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV SPRING_PROFILES_ACTIVE=prod
ENV SW_AGENT_NAME=file-service
ENV SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800

# 暴露端口
EXPOSE 8008

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8008/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar app.jar"]
