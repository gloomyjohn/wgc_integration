from fastapi import FastAPI
from pydantic import BaseModel
from typing import Dict, Tuple, Optional
import matching_algorithm

app = FastAPI()

class MatchRequest(BaseModel):
    drivers: Dict[str, Tuple[float, float]]
    passengers: Dict[str, Tuple[float, float]]
    k: int = 20
    max_dist: Optional[float] = None

@app.post("/match")
def perform_matching(data: MatchRequest):
    # 调用你上传的那个 allocate_knn_greedy 函数
    result = matching_algorithm.allocate_knn_greedy(
        data.drivers,
        data.passengers,
        k=data.k,
        max_dist=data.max_dist
    )
    return result