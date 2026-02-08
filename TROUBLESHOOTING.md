# WGC 系统集成常见问题解决指南

## 📌 问题速查表

| 问题 | 症状 | 原因 | 解决方案 |
|------|------|------|--------|
| 前端无法访问 | 连接被拒绝 (9000 端口) | Gateway 容器未启动或崩溃 | [查看问题1](#问题1-前端无法访问网关9000端口) |
| 网关返回 503 | 服务不可用 | 后端服务未就绪 | [查看问题2](#问题2-网关返回503-service-unavailable) |
| Java 后端启动失败 | 容器立即退出 | 依赖缺失/配置错误 | [查看问题3](#问题3-java-后端启动失败) |
| 数据库连接错误 | psycopg2 错误/连接超时 | 数据库未启动 | [查看问题4](#问题4-数据库连接失败) |
| 网关健康检查失败 | Gateway 容器频繁重启 | 后端服务响应缓慢 | [查看问题5](#问题5-网关健康检查失败) |

---

## 问题1: 前端无法访问网关（9000 端口）

### ❌ 症状
```
连接被拒绝 (Connection refused)
curl: (7) Failed to connect to localhost port 9000
```

### 🔍 诊断步骤

```bash
# 1. 检查容器是否运行
docker-compose ps | grep gateway

# 2. 查看网关日志
docker-compose logs gateway --tail=50

# 3. 检查端口是否开放
netstat -an | grep 9000  # Windows: netstat -ano | findstr :9000
```

### ✅ 解决方案

**方案 A: 容器未启动**
```bash
# 重新启动网关
docker-compose up -d gateway

# 等待 30 秒让网关完全启动
sleep 30

# 尝试访问
curl http://localhost:9000/health
```

**方案 B: 网关容器因依赖缺失而退出**
```bash
# 1. 查看详细错误日志
docker-compose logs gateway

# 2. 查看后端服务是否启动
docker-compose logs python-engine | tail -20
docker-compose logs java-backend | tail -20

# 3. 确保所有服务都已启动
docker-compose up -d

# 4. 等待 60 秒，让所有服务完全启动
sleep 60

# 5. 检查网关健康状态
curl http://localhost:9000/health
```

**方案 C: 防火墙阻止访问**
```bash
# Windows Firewall: 允许通过防火墙
netsh advfirewall firewall add rule name="Allow Docker Port 9000" dir=in action=allow protocol=tcp localport=9000

# Linux iptables
sudo ufw allow 9000

# macOS: 检查防火墙设置
# System Preferences > Security & Privacy > Firewall Options
```

### 📝 预期结果
```json
{
  "gateway": "healthy",
  "services": {
    "match": {
      "status": "healthy",
      "url": "http://python-engine:8000"
    },
    "v1": {
      "status": "healthy",
      "url": "http://java-backend:8080"
    }
  },
  "overall_status": "healthy"
}
```

---

## 问题2: 网关返回 503 Service Unavailable

### ❌ 症状
```bash
curl http://localhost:9000/api/v1/drivers
# 返回: 503 Service Unavailable

# 或看到这样的错误消息
{
  "error": "无法连接到后端服务 'v1'",
  "detail": "请确保服务在 http://java-backend:8080 运行"
}
```

### 🔍 诊断步骤

```bash
# 1. 检查网关能否访问后端服务
docker exec api-gateway curl -f http://python-engine:8000/health
docker exec api-gateway curl -f http://java-backend:8080/actuator/health

# 2. 查看网关日志
docker-compose logs gateway | grep "Connection\|Error\|cannot"

# 3. 检查后端服务状态
docker-compose ps java-backend python-engine

# 4. 查看后端日志
docker-compose logs java-backend --tail=100
docker-compose logs python-engine --tail=30
```

### ✅ 解决方案

**方案 A: 后端服务未就绪（最常见）**
```bash
# 1. 增加等待时间让后端完全启动
# 编辑 docker-compose.yml，找到 Gateway 的 depends_on 部分
# 确保有 condition: service_healthy

# 2. 重启所有服务（强制重新启动）
docker-compose down
docker system prune -f
docker volume prune -f
docker-compose up -d

# 3. 等待 2 分钟让所有服务启动
sleep 120

# 4. 检查状态
docker-compose ps
curl http://localhost:9000/health
```

**方案 B: Java 后端启动缓慢**
```bash
# 如果 Java 后端需要很长时间启动（如需要初始化数据库）
# 增加 java-backend 的 start_period

# 编辑 docker-compose.yml
java-backend:
  healthcheck:
    start_period: 60s  # 改为 60 秒或更长

# 重启
docker-compose down
docker-compose up -d
```

**方案 C: 容器网络问题**
```bash
# 1. 检查网络
docker network ls | grep wgc-network

# 2. 如果网络不存在，重新创建
docker-compose down
docker network prune -f
docker-compose up -d

# 3. 验证容器可以互相通信
docker exec api-gateway ping -c 2 python-engine
docker exec api-gateway ping -c 2 java-backend
```

---

## 问题3: Java 后端启动失败

### ❌ 症状
```bash
docker-compose logs java-backend
# 看到错误如：
# - Exception in thread
# - ClassNotFoundException
# - Connection refused
# 容器状态为 (Exited with code 1)
```

### 🔍 诊断步骤

```bash
# 1. 查看具体错误
docker-compose logs java-backend

# 2. 检查数据库是否可访问
docker exec java-backend curl -f http://db:5432 2>&1 || true

# 3. 查看 Java 进程是否启动
docker exec java-backend ps -ef | grep java || echo "Java 进程未启动"

# 4. 检查文件是否存在
docker exec java-backend ls -la /app/target/ 2>/dev/null || echo "JAR 文件不存在"
```

### ✅ 解决方案

**方案 A: 缺少依赖（最常见的错误）**
```bash
# 1. 确保 spring-boot-starter-actuator 已添加到 pom.xml
# 文件位置: wgc-backend/pom.xml

# 应该包含以下依赖：
# <dependency>
#   <groupId>org.springframework.boot</groupId>
#   <artifactId>spring-boot-starter-actuator</artifactId>
# </dependency>

# 2. 重新构建 Docker 镜像
docker-compose build --no-cache java-backend

# 3. 重启
docker-compose up -d java-backend
```

**方案 B: 数据库未启动或无法连接**
```bash
# 1. 启动数据库
docker-compose up -d db

# 2. 等待数据库就绪（约 20 秒）
sleep 20

# 3. 验证数据库可访问
docker exec db psql -U postgres -d wgc_db -c "SELECT 1;"

# 4. 然后启动 Java 后端
docker-compose up -d java-backend
```

**方案 C: Java 版本或 Spring Boot 配置问题**
```bash
# 1. 检查使用的 Java 版本
docker exec java-backend java -version

# 2. 查看完整错误日志
docker-compose logs java-backend --follow

# 3. 如果是配置问题，检查 application-docker.yml
docker exec java-backend cat /app/application-docker.yml

# 4. 验证内存分配（如果 OOM）
docker-compose ps java-backend
# 检查 container status，如果是 OOMKilled，增加内存

# 在 docker-compose.yml 中添加：
java-backend:
  deploy:
    resources:
      limits:
        memory: 1024M
      reservations:
        memory: 512M
```

**方案 D: JAR 文件构建失败**
```bash
# 1. 本地构建检查
cd wgc-backend
mvn clean package -DskipTests

# 2. 查看构建错误
# 如果本地构建失败，Docker 构建也会失败

# 3. 清理并重试
mvn clean
mvn package -DskipTests

# 4. 重新构建 Docker 镜像
cd ..
docker-compose build --no-cache java-backend
```

---

## 问题4: 数据库连接失败

### ❌ 症状
```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused
或
FATAL: remaining connection slots reserved for non-replication superuser connections
```

### 🔍 诊断步骤

```bash
# 1. 检查数据库容器状态
docker-compose ps db

# 2. 查看数据库日志
docker-compose logs db

# 3. 测试连接
docker exec db psql -U postgres -c "SELECT 1;"

# 4. 检查容器网络
docker network inspect wgc-network
```

### ✅ 解决方案

**方案 A: 数据库未启动**
```bash
# 1. 启动并等待
docker-compose up -d db

# 2. 等待初始化（约 30 秒）
sleep 30

# 3. 验证
docker exec db psql -h localhost -U postgres -d postgres -c "SELECT 1;"
```

**方案 B: 数据库初始化失败**
```bash
# 1. 删除旧数据卷
docker-compose down -v

# 2. 清理系统
docker system prune -f

# 3. 重新启动
docker-compose up -d db

# 4. 等待初始化
sleep 30

# 5. 创建必要的数据库（如果需要）
docker exec db psql -U postgres -c "CREATE DATABASE wgc_db;"
```

**方案 C: 连接参数不正确**
```bash
# 检查 docker-compose.yml 中的环境变量：
# - SPRING_DATASOURCE_URL 应该是: jdbc:postgresql://db:5432/wgc_db
# - SPRING_DATASOURCE_USERNAME: postgres
# - SPRING_DATASOURCE_PASSWORD: root

# 注意：在 Docker 中，使用容器名 'db' 而不是 'localhost'
```

---

## 问题5: 网关健康检查失败（容器频繁重启）

### ❌ 症状
```bash
docker-compose ps
# gateway 的状态显示 "Up X seconds" 但不断变化（频繁重启）

docker-compose logs gateway | head -100
# 看到很多 Service Unavailable 或 Connection Refused 错误
```

### 🔍 诊断步骤

```bash
# 1. 监控容器重启
docker-compose ps | watch docker-compose ps

# 2. 查看重启历史
docker inspect api-gateway | grep -A 5 RestartCount

# 3. 实时查看日志
docker-compose logs -f gateway

# 4. 检查后端服务启动时间
for svc in python-engine java-backend; do
  echo "=== $svc ==="; 
  docker inspect wgc-${svc/_/-} | grep StartedAt
done
```

### ✅ 解决方案

**方案 A: 服务启动时间太长**
```bash
# 1. 增加网关的等待时间
# 编辑 docker-compose.yml，修改 gateway 的 healthcheck：

gateway:
  healthcheck:
    start_period: 60s     # 改为 60 秒
    interval: 30s
    timeout: 10s
    retries: 5            # 增加重试次数

# 2. 也增加后端服务的 start_period
python-engine:
  healthcheck:
    start_period: 30s     # 改为 30 秒

java-backend:
  healthcheck:
    start_period: 60s     # 改为 60 秒，Java 启动较慢

# 3. 重启
docker-compose down
docker-compose up -d
```

**方案 B: 禁用网关的健康检查（临时调试使用）**
```bash
# 临时禁用网关的自动重启（用于调试）
docker-compose down
docker-compose up -d --no-restart gateway

# 这样 gateway 即使检查失败也不会自动重启
# 便于查看和诊断问题
```

**方案 C: 异步启动而不等待后端就绪**
```bash
# 如果要让网关在后端未就绪时仍然运行
# 编辑 docker-compose.yml，移除或修改 depends_on：

gateway:
  # 移除这部分或改为不需要条件
  depends_on:
    - python-engine
    - java-backend
  # depends_on 不需要 condition: service_healthy 的话
  # 网关会立即启动，而不等待后端就绪
```

---

## 问题6: 前端请求返回 CORS 错误

### ❌ 症状
```javascript
Access to XMLHttpRequest at 'http://localhost:9000/api/...' 
from origin 'http://localhost:3000' has been blocked by CORS policy
```

### ✅ 解决方案

**方案 A: 验证网关 CORS 配置**
```python
# 查看 gateway.py 是否配置了 CORS 中间件
# 应该包含：
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 或指定具体来源
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

**方案 B: 前端正确指向网关**
```javascript
// ❌ 错误: 直接访问后端
const API_URL = 'http://localhost:8080';

// ✅ 正确: 通过网关访问
const API_URL = 'http://localhost:9000';

// 所有请求都应该经过网关
fetch(`${API_URL}/api/v1/drivers`)
fetch(`${API_URL}/api/match/match`, { method: 'POST', ... })
```

---

## 🛠️ 完整重置步骤（核选）

如果上述方案都不能解决问题，执行完整重置：

```bash
# 1. 停止所有容器
docker-compose down

# 2. 删除所有容器和镜像
docker-compose down --rmi all

# 3. 清理未使用的资源
docker system prune -af
docker volume prune -f

# 4. 重新构建所有镜像
docker-compose build --no-cache

# 5. 启动服务
docker-compose up -d

# 6. 等待 2-3 分钟
sleep 120

# 7. 检查状态
docker-compose ps

# 8. 运行诊断
python diagnose.py
```

---

## 📞 获取更多帮助

### 收集诊断信息

当需要寻求帮助时，请收集以下信息：

```bash
# 保存完整日志
docker-compose logs > logs.txt

# 保存容器状态
docker-compose ps > services.txt

# 保存网络信息
docker network inspect wgc-network > network.txt
```

### 检查清单

- [ ] 所有容器都处于 "Up" 状态
- [ ] 网关返回 `overall_status: "healthy"`
- [ ] 可以访问 `http://localhost:9000/health`
- [ ] 可以访问 `http://localhost:9000/api/match/health`
- [ ] 可以访问 `http://localhost:9000/api/v1/actuator/health`
- [ ] 前端能够正确指向 `http://localhost:9000`
- [ ] 没有看到 CORS 错误

