#FROM ubuntu:latest
#LABEL authors="JunnyJ"
#
#ENTRYPOINT ["top", "-b"]
# 使用官方轻量级 Python 镜像
FROM python:3.9-slim

# 设置工作目录
WORKDIR /app

# 安装必要的系统依赖（scipy 编译时可能需要）
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# 1. 先复制依赖文件并安装，利用 Docker 缓存加速后续构建
# 你需要先在文件夹下运行 pip freeze > requirements.txt
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 2. 复制你的匹配算法代码和 FastAPI 启动脚本
COPY matching_algorithm.py .
COPY main.py .

# 3. 暴露 FastAPI 默认端口
EXPOSE 8000

# 4. 启动服务
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]