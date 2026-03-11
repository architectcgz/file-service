# ===========================================
# File Gateway Service Dockerfile
# ===========================================

FROM alpine:3.20 AS jre-builder

LABEL maintainer="Blog Team"
LABEL service="file-gateway-service"

ARG TEMURIN_JRE_URL=https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.18%2B8/OpenJDK17U-jre_x64_linux_hotspot_17.0.18_8.tar.gz

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
LABEL service="file-gateway-service"

ENV TZ=Asia/Shanghai
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

COPY --from=jre-builder /opt/java/openjdk /opt/java/openjdk

RUN mkdir -p /app /app/logs

WORKDIR /app

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

ENV SERVER_PORT=8090
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD python3 -c "import sys, urllib.request; sys.exit(0 if urllib.request.urlopen('http://localhost:8090/actuator/health').status == 200 else 1)" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar app.jar"]
