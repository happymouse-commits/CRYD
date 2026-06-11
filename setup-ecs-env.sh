#!/bin/bash
# ECS 一次性环境配置脚本
# 复制到 ECS Workbench 粘贴执行即可
set -e

echo "=== 1. 创建环境变量文件 ==="
cat > /opt/sghr/app/cryd.env << 'ENVEOF'
XFYUN_IAT_APP_ID=99d50715
XFYUN_IAT_API_KEY=94563f2f6aee1cc7366625bc278b0b88
XFYUN_IAT_API_SECRET=NTA4ZmZiNWZiYjc2N2U5OGUyM2E1M2E3
LLM_API_KEY=sk-37eab57deb084a09b4a3d8bb44fe7c6c
LLM_API_URL=https://api.deepseek.com/v1/chat/completions
LLM_API_MODEL=deepseek-chat
ENVEOF
echo "✅ /opt/sghr/app/cryd.env 已创建"

echo ""
echo "=== 2. 更新 systemd 服务 ==="

# 查找服务文件
SERVICE_FILE=$(systemctl show sghr -p FragmentPath 2>/dev/null | cut -d= -f2)
if [ -z "$SERVICE_FILE" ]; then
    # 尝试 sghr-v2
    SERVICE_FILE=$(systemctl show sghr-v2 -p FragmentPath 2>/dev/null | cut -d= -f2)
fi

if [ -z "$SERVICE_FILE" ]; then
    echo "❌ 找不到 sghr 服务文件"
    exit 1
fi

echo "服务文件: $SERVICE_FILE"

# 检查是否已有 EnvironmentFile
if grep -q "EnvironmentFile" "$SERVICE_FILE"; then
    echo "⚠️ 已有 EnvironmentFile，用新值替换"
    sed -i 's|^EnvironmentFile=.*|EnvironmentFile=/opt/sghr/app/cryd.env|' "$SERVICE_FILE"
else
    echo "在 [Service] 段添加 EnvironmentFile"
    sed -i '/^\[Service\]/a EnvironmentFile=/opt/sghr/app/cryd.env' "$SERVICE_FILE"
fi

echo ""
echo "=== 3. 重载并重启服务 ==="
systemctl daemon-reload
systemctl restart sghr 2>/dev/null || systemctl restart sghr-v2 2>/dev/null

sleep 3
echo ""
echo "=== 4. 验证 ==="
systemctl status sghr sghr-v2 --no-pager -l 2>/dev/null | head -15

echo ""
echo "✅ 配置完成！"
