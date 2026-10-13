#!/usr/bin/env bash
# 同时起后端（Spring Boot :8080）和前端 dev server（Vite :5199）。
# 改前端代码有热更新；Ctrl+C 会把两个进程一起收掉。
#
#   bash scripts/dev.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BACKEND_PORT="${BACKEND_PORT:-8080}"

if [ ! -d frontend/node_modules ]; then
  echo "==> 前端依赖未安装，先跑 npm install"
  (cd frontend && npm install)
fi

echo "==> 启动后端 :$BACKEND_PORT"
./mvnw -q spring-boot:run \
  -Dspring-boot.run.arguments="--app.open-browser=false --server.port=$BACKEND_PORT" &
BACKEND_PID=$!

cleanup() {
  echo
  echo "==> 收尾，停掉后端 (pid $BACKEND_PID)"
  kill "$BACKEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# 等后端真的能响应再起前端，否则前端首屏会去拉 /api/words 拿到连接错误
echo "==> 等后端就绪"
for _ in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:$BACKEND_PORT/api/meta" >/dev/null 2>&1; then
    echo "==> 后端就绪"
    break
  fi
  sleep 1
done

echo "==> 启动前端 :5199（/api 与 /audio 会代理到 :$BACKEND_PORT）"
cd frontend
BACKEND_URL="http://127.0.0.1:$BACKEND_PORT" npm run dev
