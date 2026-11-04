# 连接精灵链后端 — 生产容器（jar 由 deploy.ps1 本地构建后上传）
FROM eclipse-temurin:17-jre

# healthcheck 需要 curl
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
# jar 由 deploy.ps1 构建后与 Dockerfile 一起上传到部署目录（平面结构）
COPY linksprite-backend-1.0.0.jar app.jar

# 容器内存上限由 compose mem_limit 控制，JVM 按比例取堆
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
