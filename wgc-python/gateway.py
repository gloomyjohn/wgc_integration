"""
API 网关服务 - 反向代理
将所有请求转发到后端微服务
"""

import httpx
import logging
import gzip
from fastapi import FastAPI, Request
from fastapi.responses import Response
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ============ 配置 ============

# 后端服务URL映射
# 在 Docker 中运行时，使用容器名称而不是 localhost
# 本地开发时将 python-engine 改为 localhost，java-backend 改为 localhost
SERVICES = {
    "match": "http://python-engine:8000",   # 匹配服务 (Docker 容器名)
    "v1": "http://java-backend:8080"        # Java 服务 (Docker 容器名)
}

# 创建 FastAPI 应用
app = FastAPI(
    title="API Gateway",
    description="统一入口网关，路由转发到各微服务",
    version="1.0.0"
)

# 配置 CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ============ 转发处理 ============

async def forward_request(path: str, request: Request, service_key: str):
    """
    转发请求到后端服务
    
    Args:
        path: 请求路径
        request: 原始请求对象
        service_key: 服务键（match 或 v1）
    """
    try:
        service_url = SERVICES.get(service_key)
        if not service_url:
            return {"error": f"未知的服务: {service_key}"}, 404
        
        # 构造完整的后端 URL
        # 移除前缀后的路径
        # /api/match/* -> / (移除 /api/match)
        # /api/v1/*    -> /v1/* (只移除 /api)
        if service_key == "match":
            relative_path = path.replace("/api/match", "", 1)
        elif service_key == "v1":
            # 只移除 /api，保留 /v1
            relative_path = path.replace("/api", "", 1)
        else:
            relative_path = path
        
        # 确保路径以 / 开头
        if not relative_path.startswith("/"):
            relative_path = "/" + relative_path
        
        backend_url = service_url + relative_path
        
        # 获取查询字符串
        if request.url.query:
            backend_url += f"?{request.url.query}"
        
        logger.info(
            f"转发请求: {request.method} {path} "
            f"-> {backend_url}"
        )
        
        # 转发请求
        async with httpx.AsyncClient(timeout=30.0) as client:
            # 读取请求体
            body = await request.body() if request.method in ["POST", "PUT", "PATCH"] else b""
            
            # 转发请求到后端
            backend_response = await client.request(
                method=request.method,
                url=backend_url,
                headers=dict(request.headers),
                content=body,
                follow_redirects=True
            )
            
            logger.info(
                f"后端响应: {service_key} - 状态码 {backend_response.status_code}"
            )
            
            # 获取响应内容
            content = backend_response.content
            
            # 如果内容被 gzip 压缩，解压它
            if backend_response.headers.get("content-encoding") == "gzip":
                try:
                    content = gzip.decompress(content)
                    logger.info("已解压 gzip 响应")
                except Exception as e:
                    logger.error(f"解压 gzip 失败: {str(e)}")
            
            # 处理响应头
            response_headers = dict(backend_response.headers)
            # 移除所有可能导致问题的头部
            response_headers.pop("transfer-encoding", None)
            response_headers.pop("content-length", None)
            response_headers.pop("content-encoding", None)  # 移除 gzip 标记（因为已解压）
            response_headers.pop("content-range", None)
            
            # 添加 CORS 和通用的安全头
            response_headers["Access-Control-Allow-Origin"] = "*"
            response_headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS, PATH"
            response_headers["Access-Control-Allow-Headers"] = "*"
            response_headers["Access-Control-Max-Age"] = "3600"
            
            # 返回后端响应（使用 Response 而不是 StreamingResponse）
            return Response(
                content=content,
                status_code=backend_response.status_code,
                headers=response_headers,
                media_type=backend_response.headers.get("content-type")
            )
    
    except httpx.ConnectError:
        logger.error(f"无法连接到后端服务: {service_key}")
        return {
            "error": f"无法连接到后端服务 '{service_key}'",
            "detail": f"请确保服务在 {SERVICES.get(service_key)} 运行"
        }, 503
    
    except Exception as e:
        logger.error(f"转发请求时出错: {str(e)}")
        return {"error": "网关错误", "detail": str(e)}, 500


# ============ 路由定义 ============

@app.get("/")
async def root():
    """根路由 - 显示网关信息"""
    return {
        "name": "API Gateway",
        "status": "运行中",
        "routes": {
            "/api/match/*": "转发到匹配服务 (localhost:8000)",
            "/api/v1/*": "转发到其他服务 (localhost:8080)",
            "/health": "网关健康检查",
            "/docs": "API 文档"
        },
        "example": {
            "match": "POST /api/match/match",
            "other": "GET /api/v1/some-endpoint"
        }
    }


@app.get("/health")
async def gateway_health():
    """网关健康检查"""
    health_status = {}
    
    # 检查各个后端服务
    async with httpx.AsyncClient(timeout=5.0) as client:
        for service_name, service_url in SERVICES.items():
            try:
                # 不同的服务使用不同的健康检查端点
                if service_name == "v1":  # Java 后端
                    health_endpoint = f"{service_url}/actuator/health"
                else:  # Python 服务
                    health_endpoint = f"{service_url}/health"
                
                response = await client.get(health_endpoint, timeout=2.0)
                health_status[service_name] = {
                    "status": "healthy" if response.status_code == 200 else "unhealthy",
                    "url": service_url,
                    "endpoint": health_endpoint
                }
            except Exception as e:
                health_status[service_name] = {
                    "status": "unreachable",
                    "url": service_url,
                    "error": str(e)
                }
    
    all_healthy = all(s["status"] == "healthy" for s in health_status.values())
    
    return {
        "gateway": "healthy",
        "services": health_status,
        "overall_status": "healthy" if all_healthy else "degraded"
    }


# ============ 匹配服务路由 (/api/match/*) ============

@app.api_route("/api/match/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"])
async def match_service_proxy(request: Request, path: str):
    """转发到匹配服务"""
    full_path = f"/api/match/{path}"
    return await forward_request(full_path, request, "match")


# 处理 /api/match 本身的请求
@app.api_route("/api/match", methods=["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"])
async def match_service_root(request: Request):
    """转发到匹配服务 (根路径)"""
    return await forward_request("/api/match", request, "match")


# ============ v1 服务路由 (/api/v1/*) ============

@app.api_route("/api/v1/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"])
async def v1_service_proxy(request: Request, path: str):
    """转发到 v1 服务"""
    full_path = f"/api/v1/{path}"
    return await forward_request(full_path, request, "v1")


# 处理 /api/v1 本身的请求
@app.api_route("/api/v1", methods=["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"])
async def v1_service_root(request: Request):
    """转发到 v1 服务 (根路径)"""
    return await forward_request("/api/v1", request, "v1")


# ============ 启动服务器 ============

if __name__ == "__main__":
    import sys
    
    # 从命令行参数获取端口（可选）
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9000
    host = sys.argv[2] if len(sys.argv) > 2 else "0.0.0.0"
    
    print(f"\n{'='*60}")
    print(f"🚀 启动 API 网关")
    print(f"📍 网关地址：http://{host}:{port}")
    print(f"📚 完整文档：http://{host}:{port}/docs")
    print(f"\n📌 路由映射:")
    print(f"   /api/match/* → {SERVICES['match']}")
    print(f"   /api/v1/*    → {SERVICES['v1']}")
    print(f"\n💡 Ngrok 命令:")
    print(f"   ngrok http {port}")
    print(f"{'='*60}\n")
    
    uvicorn.run(
        app,
        host=host,
        port=port,
        log_level="info"
    )
