# ECS 新版（v2）部署指南

在 ECS 上按顺序执行以下命令。

---

## 一、停止旧版自动部署 Cron

先停掉旧版的定时拉取，防止它意外覆盖比赛版本：

```bash
crontab -l | grep -v 'deploy.sh' | crontab -
crontab -l   # 确认已移除
```

---

## 二、克隆新版仓库

```bash
cd /root
git clone https://github.com/happymouse-commits/CRYD-v2.git SGHR-v2
cd /root/SGHR-v2
git checkout master
```

---

## 三、创建新版目录结构

```bash
mkdir -p /opt/sghr-v2/{frontend,app/backups}
```

---

## 四、上传并安装新版文件

在本地机器执行（把文件上传到 ECS）：

```bash
# 上传 deploy-v2.sh（替换为你的 ECS IP）
scp deploy-v2.sh root@<ECS_IP>:/root/

# 上传 systemd 服务文件
scp sghr-v2.service root@<ECS_IP>:/etc/systemd/system/
```

在 ECS 上执行：

```bash
# 给部署脚本执行权限
chmod +x /root/deploy-v2.sh

# 重载 systemd
systemctl daemon-reload
```

---

## 五、开放 8081 端口

**方法 A — 命令行（CentOS/RHEL）：**

```bash
firewall-cmd --add-port=8081/tcp --permanent
firewall-cmd --reload
```

**方法 B — 阿里云控制台：**

进入 ECS 实例 → 安全组 → 添加规则 → 入方向允许 TCP 8081

---

## 六、首次部署

```bash
bash /root/deploy-v2.sh
```

首次运行会报 "无旧 JAR" 是正常的，等待构建完成即可。

---

## 七、（可选）设置新版 Cron 自动部署

```bash
# 每 5 分钟检查一次更新
(crontab -l 2>/dev/null; echo "*/5 * * * * bash /root/deploy-v2.sh >> /opt/sghr-v2/app/cron.log 2>&1") | crontab -
```

---

## 八、验证

```bash
# 检查两个服务都在运行
systemctl status sghr     # 旧版 :8080
systemctl status sghr-v2  # 新版 :8081

# 测试接口
curl http://localhost:8080/   # 旧版
curl http://localhost:8081/   # 新版
```

浏览器访问：
- 旧版（比赛）：`http://<ECS_IP>:8080/`
- 新版（开发）：`http://<ECS_IP>:8081/`

---

## 回滚说明

如需回滚新版到之前的版本：

```bash
# 列出现有备份
ls -lt /opt/sghr-v2/app/backups/

# 手动回滚
cp /opt/sghr-v2/app/backups/cryd-1.0.0_<时间戳>.jar /opt/sghr-v2/app/cryd-1.0.0.jar
systemctl restart sghr-v2
```
