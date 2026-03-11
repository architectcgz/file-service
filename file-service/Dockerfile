# ===========================================
# File Service Dockerfile
# ===========================================

FROM alpine:3.20 AS jre-builder

LABEL maintainer="Blog Team"
LABEL service="file-service"

ARG TEMURIN_JRE_URL=https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.18%2B8/OpenJDK17U-jre_x64_linux_hotspot_17.0.18_8.tar.gz

# 直接下载 Temurin JRE，避免依赖当前 Docker 源中不可用的 JDK 基础镜像或 Alpine JRE 包。
ENV TZ=Asia/Shanghai
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

RUN mkdir -p /opt/java && \
    wget -O /tmp/jre.tar.gz "${TEMURIN_JRE_URL}" && \
    tar -xzf /tmp/jre.tar.gz -C /opt/java && \
    mv /opt/java/jdk-17.0.18+8-jre /opt/java/openjdk && \
    rm -f /tmp/jre.tar.gz

FROM python:3.12-slim

LABEL maintainer="Blog Team"
LABEL service="file-service"

ENV TZ=Asia/Shanghai
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

COPY --from=jre-builder /opt/java/openjdk /opt/java/openjdk

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
    CMD python3 -c "import sys, urllib.request; sys.exit(0 if urllib.request.urlopen('http://localhost:8008/actuator/health').status == 200 else 1)" || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar app.jar"]
