import numpy as np
from scipy.spatial import cKDTree
from typing import Dict, Tuple, Optional, Any, List
import time
import logging
from datetime import datetime

# python matching_algorithm.py 8080 0.0.0.0
# FastAPI 相关导入
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, validator
import uvicorn

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ============ 数据模型定义 ============

class LocationData(BaseModel):
    """位置数据模型"""
    id: str = Field(..., description="对象ID")
    latitude: float = Field(..., description="纬度")
    longitude: float = Field(..., description="经度")
    
    @validator('latitude')
    def validate_latitude(cls, v):
        if not -90 <= v <= 90:
            raise ValueError('Latitude must be between -90 and 90')
        return v
    
    @validator('longitude')
    def validate_longitude(cls, v):
        if not -180 <= v <= 180:
            raise ValueError('Longitude must be between -180 and 180')
        return v


class MatchingRequest(BaseModel):
    """匹配请求模型"""
    drivers: Dict[str, Tuple[float, float]] = Field(
        ..., 
        description="司机坐标字典 {driver_id: (latitude, longitude)}"
    )
    passengers: Dict[str, Tuple[float, float]] = Field(
        ..., 
        description="乘客坐标字典 {passenger_id: (latitude, longitude)}"
    )
    k: int = Field(20, description="每个司机考虑的最近乘客数量", ge=1, le=100)
    max_dist: Optional[float] = Field(None, description="最大匹配距离阈值")
    
    @validator('drivers', 'passengers', pre=True)
    def validate_locations(cls, v):
        if not v or len(v) == 0:
            raise ValueError('驱动程序和乘客字典不能为空')
        return v


class MatchingResponse(BaseModel):
    """匹配响应模型"""
    status: str = Field(..., description="处理状态")
    matches: Dict[str, Optional[str]] = Field(..., description="匹配结果 {driver_id: passenger_id}")
    summary: Dict[str, int] = Field(..., description="匹配统计摘要")
    runtime_ms: float = Field(..., description="算法运行时间(毫秒)")
    timestamp: str = Field(..., description="处理时间戳")


class HealthResponse(BaseModel):
    """健康检查响应"""
    status: str = Field(..., description="服务状态")
    timestamp: str = Field(..., description="检查时间戳")
    version: str = Field(..., description="API版本")


# ============ 核心算法函数 ============

def allocate_knn_greedy(
    active_drivers: Dict[Any, Tuple[float, float]],
    active_passengers: Dict[Any, Tuple[float, float]],
    k: int = 20,
    max_dist: Optional[float] = None,
) -> Dict[Any, Optional[Any]]:
    """
    快速 kNN 贪心二部匹配算法，用于实时打车系统。

    该方法使用 KD 树替代立方时间的匈牙利算法，适合大规模车队。

    算法概述
    --------
    1. 在乘客 GPS 位置上构建 KD 树
    2. 对每个司机，查询其 k 个最近乘客候选人
    3. 贪心分配最近的可用乘客
    4. 每个乘客最多只能匹配一个司机
    5. 无可行乘客的司机保持未匹配状态（None）

    参数
    ----
    active_drivers : dict
        司机坐标字典 {driver_id: (latitude, longitude)}
    active_passengers : dict
        乘客坐标字典 {passenger_id: (latitude, longitude)}
    k : int, 可选
        每个司机考虑的最近乘客数量，默认值 20
    max_dist : float 或 None, 可选
        距离阈值，超过该距离的乘客被忽略

    返回值
    ------
    allocation : dict
        匹配结果 {driver_id: passenger_id 或 None}
    """
    # Extract IDs for stable indexing
    driver_ids = list(active_drivers.keys())
    passenger_ids = list(active_passengers.keys())

    # Initialize allocation: unmatched by default
    allocation: Dict[Any, Optional[Any]] = {d: None for d in driver_ids}

    nD, nP = len(driver_ids), len(passenger_ids)
    if nD == 0 or nP == 0:
        return allocation

    # Convert GPS coordinates to NumPy arrays
    D = np.asarray(
        [active_drivers[d] for d in driver_ids], dtype=float
    )  # shape: (nD, 2)

    P = np.asarray(
        [active_passengers[p] for p in passenger_ids], dtype=float
    )  # shape: (nP, 2)

    # Limit k by number of passengers
    k_eff = min(k, nP)

    # Build KD-tree on passenger locations
    tree = cKDTree(P)

    # Query k nearest passengers for all drivers (vectorized)
    dists, idxs = tree.query(D, k=k_eff, workers=-1)

    # Ensure 2D arrays even when k_eff == 1
    if k_eff == 1:
        dists = dists[:, None]
        idxs = idxs[:, None]

    # Track which passengers have already been assigned
    taken = np.zeros(nP, dtype=bool)

    # Optional improvement: assign drivers with fewer feasible options first
    if max_dist is not None:
        feasible_counts = (dists <= max_dist).sum(axis=1)
        driver_order = np.argsort(feasible_counts)
    else:
        driver_order = np.arange(nD)

    # Greedy matching
    for i in driver_order:
        for dist, j in zip(dists[i], idxs[i]):
            if max_dist is not None and dist > max_dist:
                break
            if not taken[j]:
                taken[j] = True
                allocation[driver_ids[i]] = passenger_ids[j]
                break

    return allocation


def random_points_singapore(n: int, seed: int = 0) -> Dict[str, Tuple[float, float]]:
    """
    生成新加坡地区范围内的随机 GPS 点。

    参数
    ----
    n : int
        要生成的点数
    seed : int, 可选
        随机数种子，用于可重复性

    返回值
    ------
    points : dict
        点的映射 {id_i: (latitude, longitude)}
    """
    rng = np.random.default_rng(seed)

    # 新加坡近似边界框
    lat_min, lat_max = 1.15, 1.47
    lon_min, lon_max = 103.60, 104.10

    return {
        f"id_{i}": (
            float(rng.uniform(lat_min, lat_max)),
            float(rng.uniform(lon_min, lon_max)),
        )
        for i in range(n)
    }


# ============ FastAPI 应用配置 ============

app = FastAPI(
    title="实时匹配引擎 API",
    description="用于打车系统的 kNN 贪心匹配算法服务",
    version="1.0.0"
)

# 配置 CORS 以允许跨域请求
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 允许所有来源，生产环境应该更严格
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 性能统计
stats = {
    "total_requests": 0,
    "total_matches": 0,
    "total_runtime_ms": 0,
    "last_request_time": None
}


# ============ API 端点定义 ============

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """
    健康检查端点，验证服务正常运行。
    
    Returns:
        HealthResponse: 包含服务状态信息
    """
    return HealthResponse(
        status="healthy",
        timestamp=datetime.now().isoformat(),
        version="1.0.0"
    )


@app.post("/match", response_model=MatchingResponse)
async def match_drivers_and_passengers(request: MatchingRequest):
    """
    执行司机与乘客的匹配。
    
    这个端点接收司机和乘客的坐标数据，使用 kNN 贪心算法进行实时匹配。
    
    Args:
        request: 包含司机、乘客坐标和算法参数的匹配请求
        
    Returns:
        MatchingResponse: 包含匹配结果、统计和性能指标的响应
        
    Raises:
        HTTPException: 当输入验证失败或算法执行出错时抛出
    """
    try:
        # 记录请求开始时间
        start_time = time.perf_counter()
        
        logger.info(
            f"接收匹配请求：{len(request.drivers)} 司机，"
            f"{len(request.passengers)} 乘客"
        )
        
        # 验证输入
        if len(request.drivers) == 0 or len(request.passengers) == 0:
            raise ValueError("司机和乘客数量不能为零")
        
        # 调用匹配算法
        matches = allocate_knn_greedy(
            request.drivers,
            request.passengers,
            k=request.k,
            max_dist=request.max_dist
        )
        
        # 计算统计信息
        matched_count = sum(1 for v in matches.values() if v is not None)
        runtime_ms = (time.perf_counter() - start_time) * 1000
        
        # 更新全局统计
        stats["total_requests"] += 1
        stats["total_matches"] += matched_count
        stats["total_runtime_ms"] += runtime_ms
        stats["last_request_time"] = datetime.now().isoformat()
        
        logger.info(
            f"匹配完成：{matched_count}/{len(request.drivers)} 司机被成功分配，"
            f"耗时 {runtime_ms:.2f}ms"
        )
        
        return MatchingResponse(
            status="success",
            matches=matches,
            summary={
                "total_drivers": len(request.drivers),
                "total_passengers": len(request.passengers),
                "matched_count": matched_count,
                "unmatched_drivers": len(request.drivers) - matched_count,
                "match_rate": round(matched_count / len(request.drivers) * 100, 2)
            },
            runtime_ms=round(runtime_ms, 2),
            timestamp=datetime.now().isoformat()
        )
        
    except ValueError as ve:
        logger.error(f"输入验证错误：{str(ve)}")
        raise HTTPException(status_code=400, detail=str(ve))
    except Exception as e:
        logger.error(f"匹配算法执行出错：{str(e)}")
        raise HTTPException(status_code=500, detail=f"内部服务器错误：{str(e)}")


@app.get("/stats")
async def get_statistics():
    """
    获取 API 的性能统计信息。
    
    Returns:
        dict: 包含总请求数、总匹配数、平均响应时间等信息
    """
    avg_runtime = (
        stats["total_runtime_ms"] / stats["total_requests"]
        if stats["total_requests"] > 0
        else 0
    )
    
    return {
        "status": "success",
        "statistics": {
            "total_requests": stats["total_requests"],
            "total_matches": stats["total_matches"],
            "average_runtime_ms": round(avg_runtime, 2),
            "last_request_time": stats["last_request_time"]
        },
        "timestamp": datetime.now().isoformat()
    }


@app.get("/test")
async def test_matching():
    """
    测试端点：使用随机数据执行一次匹配，验证服务功能正常。
    
    Returns:
        dict: 测试匹配的结果
    """
    try:
        # 生成测试数据
        n_drivers = 100
        n_passengers = 100
        
        test_drivers = random_points_singapore(n_drivers, seed=45)
        test_passengers = random_points_singapore(n_passengers, seed=40)
        
        # 执行匹配
        matches = allocate_knn_greedy(
            test_drivers,
            test_passengers,
            k=20,
            max_dist=None
        )
        
        matched_count = sum(1 for v in matches.values() if v is not None)
        
        return {
            "status": "success",
            "test_data": {
                "drivers": n_drivers,
                "passengers": n_passengers,
                "matched": matched_count,
                "match_rate": f"{matched_count/n_drivers*100:.2f}%"
            },
            "sample_matches": dict(list(matches.items())[:5]),  # 显示前5个匹配
            "timestamp": datetime.now().isoformat()
        }
        
    except Exception as e:
        logger.error(f"测试执行出错：{str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# ============ 启动服务器 ============

if __name__ == "__main__":
    import sys
    
    # 从命令行参数获取端口（可选）
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    host = sys.argv[2] if len(sys.argv) > 2 else "0.0.0.0"
    
    print(f"\n{'='*50}")
    print(f"🚀 启动实时匹配引擎服务器")
    print(f"📍 地址：http://{host}:{port}")
    print(f"📚 API 文档：http://{host}:{port}/docs")
    print(f"{'='*50}\n")
    
    # 启动 Uvicorn 服务器
    uvicorn.run(
        app,
        host=host,
        port=port,
        log_level="info"
    )