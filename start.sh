#!/bin/bash
# WGC 系统快速启动和验证脚本

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}   WGC 系统快速启动脚本${NC}"
echo -e "${BLUE}================================${NC}\n"

# 检查 Docker
echo -e "${YELLOW}检查 Docker 环境...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker 未安装${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose 未安装${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker 环境已就绪${NC}\n"

# 检查是否在正确的目录
if [ ! -f "docker-compose.yml" ]; then
    echo -e "${RED}❌ 未找到 docker-compose.yml，请在项目根目录运行此脚本${NC}"
    exit 1
fi

echo -e "${YELLOW}step 1: 停止现有服务...${NC}"
docker-compose down 2>/dev/null || true
echo -e "${GREEN}✓ 完成${NC}\n"

echo -e "${YELLOW}Step 2: 清理资源...${NC}"
docker system prune -f > /dev/null 2>&1 || true
echo -e "${GREEN}✓ 完成${NC}\n"

echo -e "${YELLOW}Step 3: 构建 Docker 镜像...${NC}"
docker-compose build --no-cache
echo -e "${GREEN}✓ 完成${NC}\n"

echo -e "${YELLOW}Step 4: 启动所有服务...${NC}"
docker-compose up -d
echo -e "${GREEN}✓ 完成${NC}\n"

echo -e "${YELLOW}Step 5: 等待服务启动 (120 秒)...${NC}"
for i in {1..24}; do
    echo -ne "\r   进度: $((i * 5)) 秒"
    sleep 5
done
echo -e "\n${GREEN}✓ 完成${NC}\n"

echo -e "${YELLOW}Step 6: 检查服务状态...${NC}"
docker-compose ps
echo ""

echo -e "${YELLOW}Step 7: 运行诊断测试...${NC}"
if command -v python3 &> /dev/null; then
    python3 diagnose.py
elif command -v python &> /dev/null; then
    python diagnose.py
else
    echo -e "${YELLOW}⚠ Python 未安装，跳过诊断测试${NC}"
    echo -e "${YELLOW}请手动运行: python diagnose.py${NC}"
fi

echo -e "\n${GREEN}════════════════════════════════${NC}"
echo -e "${GREEN}✓ 启动完成！${NC}"
echo -e "${GREEN}════════════════════════════════${NC}\n"

echo -e "📍 服务地址:"
echo -e "   • API 网关: ${BLUE}http://localhost:9000${NC}"
echo -e "   • Python 引擎: ${BLUE}http://localhost:8000${NC}"
echo -e "   • Java 后端: ${BLUE}http://localhost:8080${NC}"
echo ""

echo -e "🔍 检查健康状态:"
echo -e "   ${BLUE}curl http://localhost:9000/health${NC}"
echo ""

echo -e "📚 查看日志:"
echo -e "   ${BLUE}docker-compose logs -f gateway${NC}"
echo -e "   ${BLUE}docker-compose logs -f python-engine${NC}"
echo -e "   ${BLUE}docker-compose logs -f java-backend${NC}"
echo ""

echo -e "🛑 停止服务:"
echo -e "   ${BLUE}docker-compose down${NC}"
echo ""
