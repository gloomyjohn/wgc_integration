@echo off
REM WGC 系统快速启动和验证脚本 (Windows 版本)

setlocal enabledelayedexpansion

echo.
echo ================================
echo    WGC 系统快速启动脚本 (Windows)
echo ================================
echo.

REM 检查 Docker
echo [1/7] 检查 Docker 环境...
docker --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker 未安装！
    exit /b 1
)

docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker Compose 未安装！
    exit /b 1
)

echo [SUCCESS] Docker 环境已就绪
echo.

REM 检查 docker-compose.yml
if not exist "docker-compose.yml" (
    echo ERROR: 未找到 docker-compose.yml，请在项目根目录运行此脚本
    exit /b 1
)

REM 停止现有服务
echo [2/7] 停止现有服务...
docker-compose down >nul 2>&1
echo [SUCCESS] 完成
echo.

REM 清理资源
echo [3/7] 清理资源...
docker system prune -f >nul 2>&1
echo [SUCCESS] 完成
echo.

REM 构建镜像
echo [4/7] 构建 Docker 镜像（这可能需要几分钟）...
docker-compose build --no-cache
if errorlevel 1 (
    echo ERROR: 镜像构建失败！
    exit /b 1
)
echo [SUCCESS] 完成
echo.

REM 启动服务
echo [5/7] 启动所有服务...
docker-compose up -d
if errorlevel 1 (
    echo ERROR: 服务启动失败！
    exit /b 1
)
echo [SUCCESS] 完成
echo.

REM 等待服务启动
echo [6/7] 等待服务启动 (120 秒)...
for /l %%i in (1,1,24) do (
    set /a progress=%%i * 5
    cls
    echo.
    echo ================================
    echo    WGC 系统快速启动脚本 (Windows)
    echo ================================
    echo.
    echo [6/7] 等待服务启动...
    echo    进度: !progress! 秒
    timeout /t 5 /nobreak >nul
)
echo [SUCCESS] 完成
echo.

REM 检查状态
echo [7/7] 检查服务状态...
docker-compose ps
echo.

REM 运行诊断（如果 Python 可用）
python diagnose.py 2>nul
if errorlevel 1 (
    python3 diagnose.py 2>nul
    if errorlevel 1 (
        echo 提示: Python 未安装或诊断脚本不可用
        echo 请手动运行: python diagnose.py
    )
)

echo.
echo ════════════════════════════════
echo [SUCCESS] 启动完成！
echo ════════════════════════════════
echo.

echo 📍 服务地址:
echo    - API 网关:    http://localhost:9000
echo    - Python 引擎: http://localhost:8000
echo    - Java 后端:   http://localhost:8080
echo.

echo 🔍 检查健康状态:
echo    curl http://localhost:9000/health
echo.

echo 📚 查看日志:
echo    docker-compose logs -f gateway
echo    docker-compose logs -f python-engine
echo    docker-compose logs -f java-backend
echo.

echo 🛑 停止服务:
echo    docker-compose down
echo.

pause
