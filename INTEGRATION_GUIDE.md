# WGC 系统集成指南

## 📋 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端应用                              │
│              访问地址: http://localhost:9000             │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│              API 网关 (Gateway)                          │
│         端口: 9000 (容器内和宿主机相同)                  │
│    - /api/match/* → Python 匹配引擎 (8000)             │
│    - /api/v1/*    → Java 后端服务 (8080)               │
└─────────────────────────────────────────────────────────┘
       ↙                              ↘
┌──────────────────────┐    ┌──────────────────────────┐
│ Python 匹配引擎      │    │ Java Spring Boot 后端    │
│ (FastAPI)            │    │ (Spring Boot 3.2.2)      │
│ 端口: 8000           │    │ 端口: 8080               │
│ - /health            │    │ - /actuator/health       │
│ - /match (POST)      │    │ - /v1/* (各业务端点)     │
└──────────────────────┘    └──────────────────────────┘
       ↓                              ↓
    (K-NN 算法)            ┌─────────────────────────┐
                           │ PostgreSQL (5432)       │
                           │ Redis (6379)            │
                           │ RabbitMQ (5672)         │
                           └─────────────────────────┘
```

## 🚀 启动步骤

### 方式1: 使用 Docker Compose（推荐）

```bash
# 构建所有镜像
docker-compose build

# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f gateway

# 检查各服务状态
docker-compose ps
```

### 方式2: 本地开发（单独启动）

#### 启动后端依赖服务
```bash
# 创建并启动数据库、Redis、RabbitMQ
docker-compose up -d db redis rabbitmq

# 等待它们完全启动（约 20-30 秒）
```

#### 启动 Java 后端
```bash
cd wgc-backend
mvn clean package -DskipTests
java -jar target/wgc-backend-0.0.1-SNAPSHOT.jar
# 后端应在 http://localhost:8080 运行
```

#### 启动 Python 匹配引擎
```bash
cd wgc-python
pip install -r requirements.txt
python matching_algorithm.py 8000 0.0.0.0
# 引擎应在 http://localhost:8000 运行
```

#### 启动 API 网关
```bash
cd wgc-python
python gateway.py 9000 0.0.0.0
# 网关应在 http://localhost:9000 运行
```

## ✅ 验证步骤

### 1. 检查服务健康状态
```bash
# 通过网关检查所有服务
curl http://localhost:9000/health

# 直接检查各服务
curl http://localhost:8000/health          # Python 引擎
curl http://localhost:8080/actuator/health # Java 后端
```

### 2. 测试 API 端点

#### 测试匹配算法
```bash
curl -X POST http://localhost:9000/api/match/match \
  -H "Content-Type: application/json" \
  -d '{
    "drivers": {
      "driver_1": [1.35, 103.8],
      "driver_2": [1.36, 103.81]
    },
    "passengers": {
      "passenger_1": [1.351, 103.801],
      "passenger_2": [1.352, 103.802]
    },
    "k": 5,
    "max_dist": 1.0
  }'
```

#### 测试 Java 后端
```bash
curl http://localhost:9000/api/v1/drivers
curl http://localhost:9000/api/v1/trips
```

## 🔧 解决集成问题

### 问题1: 前端无法访问网关（9000 端口）

**原因**: 网关容器的健康检查失败导致容器频繁重启

**解决方案**:
```bash
# 1. 检查网关日志
docker-compose logs gateway

# 2. 验证后端服务是否都已启动
docker-compose ps

# 3. 手动检查后端可达性
docker exec api-gateway curl -f http://python-engine:8000/health
docker exec api-gateway curl -f http://java-backend:8080/actuator/health

# 4. 如果 Java 后端返回 502 或 503，需要等待其启动完成
```

### 问题2: Java 后端启动失败

**原因**: 
- 缺少数据库连接
- 缺少 Actuator 依赖
- Spring profiles 配置不正确

**解决方案**:
```bash
# 1. 检查 Java 后端日志
docker-compose logs java-backend

# 2. 验证依赖是否正确安装
# 确保 pom.xml 包含 spring-boot-starter-actuator

# 3. 手动测试健康检查端点
docker exec java-backend curl -f http://localhost:8080/actuator/health

# 4. 如果数据库连接失败，启用数据库容器
docker-compose logs db
```

### 问题3: 网关转发请求失败 (503 Service Unavailable)

**原因**: 后端服务还未启动或不可达

**解决方案**:
```bash
# 1. 检查容器健康状态
docker-compose ps | grep -E "python-engine|java-backend|gateway"

# 2. 增加重试次数和等待时间
# 编辑 docker-compose.yml，增加 start_period 的值

# 3. 查看详细日志
docker-compose logs -f --all
```

### 问题4: 网络连接问题

**原因**: Docker 容器间网络通信异常

**解决方案**:
```bash
# 1. 验证网络存在
docker network ls | grep wgc-network

# 2. 重建网络
docker-compose down
docker network prune
docker-compose up -d

# 3. 测试容器间通信
docker exec api-gateway ping -c 2 python-engine
docker exec api-gateway ping -c 2 java-backend
```

## 📊 监控和日志

### 查看实时日志
```bash
# 所有服务
docker-compose logs -f

# 特定服务
docker-compose logs -f gateway
docker-compose logs -f java-backend
docker-compose logs -f python-engine
```

### 性能指标
```bash
# Java 后端
curl http://localhost:9000/api/v1/actuator/metrics

# JVM 堆内存
curl http://localhost:9000/api/v1/actuator/metrics/jvm.memory.used
```

## 🛑 停止服务

```bash
# 停止所有容器
docker-compose stop

# 停止并删除容器
docker-compose down

# 完全清理（包括数据库卷）
docker-compose down -v
```

## 🌐 前端配置

前端需要配置正确的 API 入口点：

```javascript
// 在你的前端代码中
const API_BASE_URL = 'http://localhost:9000';

// 调用匹配 API
fetch(`${API_BASE_URL}/api/match/match`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    drivers: { ... },
    passengers: { ... }
  })
})

// 调用 Java 后端 API
fetch(`${API_BASE_URL}/api/v1/drivers`)
```

## 📝 重要配置更改

### docker-compose.yml 的改进
1. ✅ 添加了 `condition: service_healthy` 到依赖项
2. ✅ 改进了健康检查配置（更短的间隔和更长的启动期）
3. ✅ 添加了 Spring Boot Actuator 环境变量

### pom.xml 的改进
1. ✅ 添加了 `spring-boot-starter-actuator` 依赖
2. ✅ 确保 Java 后端能暴露 `/actuator/health` 端点

