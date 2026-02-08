#!/bin/bash
# 启动 ngrok 和 API 网关

PORT=${1:-9000}
HOST=${2:-0.0.0.0}

echo "=========================================="
echo "🚀 启动 API 网关"
echo "📍 网关地址：http://${HOST}:${PORT}"
echo "=========================================="

# 在后台启动 ngrok
echo "🌐 启动 ngrok 隧道..."
ngrok http ${PORT} > /tmp/ngrok.log 2>&1 &
NGROK_PID=$!
sleep 3

# 显示 ngrok URL
if [ -f /tmp/ngrok.log ]; then
    NGROK_URL=$(cat /tmp/ngrok.log | grep -oP 'https://[a-zA-Z0-9\-]+\.ngrok\.io' | head -1)
    if [ ! -z "$NGROK_URL" ]; then
        echo "✅ Ngrok 已启动: $NGROK_URL"
    fi
fi

# 在前台启动网关
echo "🚀 启动 FastAPI 网关..."
exec python gateway.py ${PORT} ${HOST}
