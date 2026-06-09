#!/bin/bash
# 从容应对 ECS 自动部署脚本
# 用法：每 5 分钟执行一次，检测到新代码就自动部署
set -e

REPO_DIR="/root/SGHR"
DEPLOY_JAR="/opt/sghr/app/cryd-1.0.0.jar"
FRONTEND_DIR="/opt/sghr/frontend"
LOCKFILE="/tmp/sghr-deploy.lock"

# 防止并发
exec 200>"$LOCKFILE"
flock -n 200 || { echo "部署已在运行，跳过"; exit 0; }

cd "$REPO_DIR"

# 记录当前 commit
OLD_HASH=$(git rev-parse HEAD 2>/dev/null || echo "none")

# 拉取新代码
echo "[$(date)] 检查更新..."
git fetch origin master 2>/dev/null || { echo "fetch 失败，跳过"; exit 1; }
NEW_HASH=$(git rev-parse origin/master)

if [ "$OLD_HASH" = "$NEW_HASH" ]; then
    echo "[$(date)] 无更新，跳过"
    exit 0
fi

echo "[$(date)] 检测到新代码，开始部署..."
git reset --hard origin/master

# 编译前端
echo "[1/4] 构建前端..."
cd "$REPO_DIR/frontend"
npm install --silent 2>/dev/null || true
npm run build 2>/dev/null || true
if [ -d "dist" ]; then
    rm -rf "$FRONTEND_DIR"/*
    cp -r dist/* "$FRONTEND_DIR"/
    echo "前端部署完成"
else
    echo "警告：前端构建失败或 dist 不存在"
fi

# 编译后端
echo "[2/4] 构建后端..."
cd "$REPO_DIR"
rm -rf 后端/target
docker run --rm \
    -v /root/SGHR/后端:/app \
    -v /root/.m2:/root/.m2 \
    -w /app \
    maven:3.9-eclipse-temurin-21 \
    mvn package -DskipTests -B -q || {
    echo "Maven 构建失败"
    exit 1
}

# 部署 jar
echo "[3/4] 部署后端..."
JAR=$(ls 后端/target/cryd-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    echo "错误：未找到 JAR 文件"
    exit 1
fi
cp -f "$JAR" "$DEPLOY_JAR"

# 重启服务
echo "[4/4] 重启服务..."
systemctl restart sghr
sleep 5

# 健康检查
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ || echo "000")
echo "[$(date)] 部署完成，健康检查: HTTP $HTTP_CODE"