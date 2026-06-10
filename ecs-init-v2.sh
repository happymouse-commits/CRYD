#!/bin/bash
# ============================================================
# ECS 一键初始化脚本 — 冻结旧版 8080 + 部署新版 8081
# 直接复制粘贴到 ECS 终端执行
# ============================================================
set -e

echo "=========================================="
echo "  CRYD 双版本隔离部署脚本"
echo "  旧版(比赛): 8080 → 冻结在 competition"
echo "  新版(开发): 8081 → master 持续更新"
echo "=========================================="

# ---- 1. 停掉旧版 cron ----
echo ""
echo "[1/6] 停止旧版自动部署 cron..."
crontab -l 2>/dev/null > /tmp/cron_backup.txt || true
crontab -l 2>/dev/null | grep -v 'deploy.sh' | crontab - 2>/dev/null || true
echo "  已停止。备份: /tmp/cron_backup.txt"
crontab -l 2>/dev/null || echo "  cron 已清空"

# ---- 2. 冻结旧版到 competition 分支 ----
echo ""
echo "[2/6] 冻结旧版仓库: master → competition..."
cd /root/SGHR
git fetch origin
git checkout competition
git branch
echo "  旧版已锁定在 competition 分支"

# ---- 3. 克隆新版仓库 ----
echo ""
echo "[3/6] 克隆新版仓库..."
cd /root
if [ -d "/root/SGHR-v2" ]; then
    echo "  SGHR-v2 已存在，跳过克隆"
else
    git clone https://github.com/happymouse-commits/SGHR.git SGHR-v2
fi
cd /root/SGHR-v2
git checkout master
git pull origin master
echo "  新版仓库就绪"

# ---- 4. 创建新版目录 ----
echo ""
echo "[4/6] 创建新版目录结构..."
mkdir -p /opt/sghr-v2/{frontend,app/backups}
chmod +x /root/SGHR-v2/deploy-v2.sh
cp /root/SGHR-v2/deploy-v2.sh /root/deploy-v2.sh
chmod +x /root/deploy-v2.sh

# 安装 systemd 服务
cp /root/SGHR-v2/sghr-v2.service /etc/systemd/system/
systemctl daemon-reload
echo "  目录和服务已就绪"

# ---- 5. 开放 8081 端口 ----
echo ""
echo "[5/6] 开放 8081 端口..."
if command -v firewall-cmd &> /dev/null; then
    firewall-cmd --add-port=8081/tcp --permanent 2>/dev/null && firewall-cmd --reload 2>/dev/null || echo "  firewalld 未运行，跳过（去阿里云安全组开放）"
else
    echo "  未检测到 firewalld，请在阿里云安全组中添加 TCP 8081 入站规则"
fi

# ---- 6. 首次部署 ----
echo ""
echo "[6/6] 首次部署新版..."
bash /root/deploy-v2.sh

# ---- 完成 ----
echo ""
echo "=========================================="
echo "  ✅ 部署完成！"
echo ""
echo "  旧版(比赛): http://$(hostname -I | awk '{print $1}'):8080/"
echo "  新版(开发): http://$(hostname -I | awk '{print $1}'):8081/"
echo ""
echo "  验证命令:"
echo "    systemctl status sghr     # 旧版"
echo "    systemctl status sghr-v2  # 新版"
echo "    curl http://localhost:8080/"
echo "    curl http://localhost:8081/"
echo "=========================================="
