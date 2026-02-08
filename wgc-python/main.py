import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Dict, Tuple, Optional, Any
import numpy as np
from scipy.spatial import cKDTree

# --- 核心算法部分 (保留你的逻辑) ---

def allocate_knn_greedy(
        active_drivers: Dict[str, Tuple[float, float]],
        active_passengers: Dict[str, Tuple[float, float]],
        k: int = 20,
        max_dist: Optional[float] = None,
) -> Dict[str, Optional[str]]:
    driver_ids = list(active_drivers.keys())
    passenger_ids = list(active_passengers.keys())
    allocation = {d: None for d in driver_ids}

    nD, nP = len(driver_ids), len(passenger_ids)
    if nD == 0 or nP == 0:
        return allocation

    D = np.array([active_drivers[d] for d in driver_ids], dtype=float)
    P = np.array([active_passengers[p] for p in passenger_ids], dtype=float)

    k_eff = min(k, nP)
    tree = cKDTree(P)
    dists, idxs = tree.query(D, k=k_eff, workers=-1)

    if k_eff == 1:
        dists, idxs = dists[:, None], idxs[:, None]

    taken = np.zeros(nP, dtype=bool)

    if max_dist is not None:
        feasible_counts = (dists <= max_dist).sum(axis=1)
        driver_order = np.argsort(feasible_counts)
    else:
        driver_order = np.arange(nD)

    for i in driver_order:
        for dist, j in zip(dists[i], idxs[i]):
            if max_dist is not None and dist > max_dist:
                break
            if not taken[j]:
                taken[j] = True
                allocation[driver_ids[i]] = passenger_ids[j]
                break
    return allocation

# --- API 接口定义部分 ---

app = FastAPI(title="Real-time Matching Engine API")

# 定义请求数据结构
class MatchingRequest(BaseModel):
    # 格式: {"driver_1": [1.35, 103.8], ...}
    drivers: Dict[str, Tuple[float, float]]
    passengers: Dict[str, Tuple[float, float]]
    k: Optional[int] = 20
    max_dist: Optional[float] = None

@app.post("/match")
async def match_drivers_and_passengers(request: MatchingRequest):
    """
    接收前端发送的司机和乘客坐标，返回匹配方案。
    """
    try:
        # 调用算法
        result = allocate_knn_greedy(
            request.drivers,
            request.passengers,
            k=request.k,
            max_dist=request.max_dist
        )
        return {
            "status": "success",
            "matches": result,
            "summary": {
                "total_drivers": len(request.drivers),
                "total_passengers": len(request.passengers),
                "matched_count": sum(1 for v in result.values() if v is not None)
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    # 启动服务器 (本地默认 8000 端口)
    uvicorn.run(app, host="0.0.0.0", port=8000)