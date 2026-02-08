FROM python:3.11-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 安装 Python 依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY matching_algorithm.py .
COPY gateway.py .
COPY main.py .

# 暴露所有可能的端口
EXPOSE 8000 8001 9000

# 默认启动匹配服务，可通过 docker run 覆盖
CMD ["python", "matching_algorithm.py", "8000", "0.0.0.0"]