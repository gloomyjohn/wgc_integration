from fastapi import FastAPI
from pydantic import BaseModel
from typing import Dict, Tuple, Optional
import matching_algorithm

app = FastAPI(root_path="/api/match")

class MatchRequest(BaseModel):
    drivers: Dict[str, Tuple[float, float]]
    passengers: Dict[str, Tuple[float, float]]
    k: int = 20
    max_dist: Optional[float] = None

@app.post("/")
def perform_matching(data: MatchRequest):
    # 调用你上传的那个 allocate_knn_greedy 函数
    result = matching_algorithm.allocate_knn_greedy(
        data.drivers,
        data.passengers,
        k=data.k,
        max_dist=data.max_dist
    )
    return result

@app.get("/health")
async def health_check(request: Request):
    # 验证是否收到了来自 Nginx 的转发头部
    return {
        "status": "up",
        "client_ip": request.headers.get("x-real-ip"),
        "forwarded_proto": request.headers.get("x-forwarded-proto"),
        "message": "Hello from Python via Nginx Gateway!"
    }