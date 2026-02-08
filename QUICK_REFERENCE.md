# 🚀 WGC 系统集成 - 快速参考卡片

## 📋 一句话总结问题

**原因**: Gateway 在后端服务未就绪时就以为它们准备好了，导致前端无法访问

---

## ⚡ 最快解决方案（5 分钟）

### Windows 用户
```command
start.bat
```

### Linux/Mac 用户
```bash
chmod +x start.sh
./start.sh
```

**就这样！脚本会自动：**
- ✅ 停止旧服务
- ✅ 构建镜像
- ✅ 启动所有容器
- ✅ 等待服务就绪
- ✅ 运行诊断测试

---

## 🔍 快速诊断

检查系统是否正常运行：

```bash
# 查看所有服务
docker-compose ps

# 运行完整诊断（需要 Python）
python diagnose.py

# 手动检查各服务
curl http://localhost:9000/health          # 网关
curl http://localhost:8000/health          # Python 引擎
curl http://localhost:8080/actuator/health # Java 后端
```

### ✅ 应该看到的结果
- 所有容器状态都是 `Up`
- 网关返回 `overall_status: "healthy"`
- 所有端点返回 200 状态码

---

## 🎯 核心改动清单

| 文件 | 改动 | 原因 |
|------|------|------|
| `docker-compose.yml` | 添加 `condition: service_healthy` | 确保顺序执行 |
| `docker-compose.yml` | 增加 `start_period` 时间 | 给 Java 足够启动时间 |
| `pom.xml` | 添加 `actuator` 依赖 | 启用健康检查端点 |
| 新建 `application-docker.yml` | Docker 环境配置 | Spring Profile 支持 |

---

## 📍 访问地址

| 服务 | 地址 | 用途 |
|------|------|------|
| 网关 | `http://localhost:9000` | 前端通过这里访问 |
| 文档 | `http://localhost:9000/docs` | Swagger API 文档 |
| 匹配引擎 | `http://localhost:8000` | 算法服务（直接访问用） |
| Java 后端 | `http://localhost:8080` | 业务服务（直接访问用） |

### 前端配置
```javascript
// ✅ 正确
const API = 'http://localhost:9000';  // 所有请求都用网关
fetch(`${API}/api/match/match`)
fetch(`${API}/api/v1/drivers`)

// ❌ 错误
const API = 'http://localhost:8080';  // 不经过网关
```

---

## 🆘 遇到问题

### 问题: 前端仍无法访问

1. **确保容器都在运行**
   ```bash
   docker-compose ps
   # 应该看到: gateway, python-engine, java-backend 都是 Up
   ```

2. **如果有容器 Exited**
   ```bash
   docker-compose logs 容器名
   # 查看错误日志
   ```

3. **重新启动**
   ```bash
   docker-compose down
   docker-compose up -d
   # 等待 2 分钟
   ```

4. **完全重置（核选）**
   ```bash
   docker-compose down -v
   docker system prune -f
   docker-compose build --no-cache
   docker-compose up -d
   ```

### 问题: 返回 503 错误

**原因**: 后端服务还在启动

**解决**: 等待 1-2 分钟，然后重试

```bash
# 监控启动进度
docker-compose logs -f

# 或定期检查
for i in {1..10}; do curl http://localhost:9000/health && break || sleep 10; done
```

---

## 📊 故障排查树

```
前端能访问 http://localhost:9000 ?
├─ 是
│  └─ 返回 healthy 状态 ?
│     ├─ 是  → ✅ 系统正常
│     └─ 否  → 等待 1 分钟后重试
│              或查看: TROUBLESHOOTING.md #问题2
└─ 否 → 查看: TROUBLESHOOTING.md #问题1
   
Java 后端启动失败 ?
├─ 是 → 查看: TROUBLESHOOTING.md #问题3
└─ 否 → 查看: TROUBLESHOOTING.md #问题4
```

---

## 📚 详细文档导航

| 文档 | 适用场景 |
|------|----------|
| [FIXES_SUMMARY.md](FIXES_SUMMARY.md) | 了解具体改动 |
| [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) | 架构/启动/验证 |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | 遇到问题时 |

---

## ⏱️ 预期时间表

| 步骤 | 耗时 |
|------|------|
| 构建镜像 | 2-3 分钟 |
| 启动容器 | 10-15 秒 |
| 服务就绪 | 30-60 秒 |
| 诊断测试 | 5-10 秒 |
| **总计** | **~5 分钟** |

---

## ✨ 一键验证

启动后立即运行这个命令，验证系统是否完全就绪：

```bash
# curl 方式（需要 curl）
for url in "http://localhost:9000/health" \
           "http://localhost:8000/health" \
           "http://localhost:8080/actuator/health"; do
  echo "检查: $url"
  curl -s "$url" | grep -q healthy && echo "✓ OK" || echo "✗ FAILED"
done

# Python 方式（推荐）
python diagnose.py
```

---

## 🎓 学习资源

- 网关架构: `/api/match/*` → Python | `/api/v1/*` → Java
- Docker Compose: 通过 `condition: service_healthy` 确保顺序
- Spring Boot: Actuator 提供运维端点
- FastAPI: CORS 中间件处理跨域

---

## 📞 需要更多帮助？

1. **查看日志获取错误信息**
   ```bash
   docker-compose logs gateway | tail -50
   ```

2. **检查具体问题**
   - 端口被占用? → `netstat -ano | findstr :9000` (Windows)
   - Docker 卷牛? → `docker volume prune`
   - 网络问题? → `docker network prune`

3. **参考文档**
   - [完整集成指南](INTEGRATION_GUIDE.md)
   - [故障排查指南](TROUBLESHOOTING.md)
   - [修复总结](FIXES_SUMMARY.md)

---

## 🎉 成功标志

看到以下输出说明系统正常运行：

```
❓ curl http://localhost:9000/health
✓ 返回:
{
  "gateway": "healthy",
  "services": {
    "match": {"status": "healthy", "url": "http://python-engine:8000"},
    "v1": {"status": "healthy", "url": "http://java-backend:8080"}
  },
  "overall_status": "healthy"
}
```

**现在前端可以访问 `http://localhost:9000` 了！** 🚀

