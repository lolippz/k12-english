#!/usr/bin/env bash
# 一键启动：缺什么补什么，最后把服务跑起来。
#
#   bash start.sh
#
# 想追加启动参数直接跟在后面：
#   bash start.sh --server.port=9000 --app.open-browser=false
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

if ! command -v java >/dev/null 2>&1 && [ -z "${JAVA_HOME:-}" ]; then
  echo "[错误] 没找到 java。请先安装 JDK 17 或更高版本：https://adoptium.net" >&2
  exit 1
fi

# 1. 前端产物
if [ ! -f frontend/dist/index.html ]; then
  echo "[1/2] 前端产物不存在，正在构建..."
  if ! command -v npm >/dev/null 2>&1; then
    echo "[错误] 需要 Node.js 才能构建前端，请安装 Node 22+：https://nodejs.org" >&2
    exit 1
  fi
  (cd frontend && [ -d node_modules ] || npm install; npm run build)
fi

# 2. 后端 jar
if [ ! -f target/k12-english.jar ]; then
  echo "[2/2] 正在构建后端（首次约 1-2 分钟）..."
  ./mvnw -B -DskipTests package
fi

# 3. 启动
echo
echo "正在启动，浏览器会自动打开 http://127.0.0.1:8080"
echo "按 Ctrl+C 停止服务。"
echo
exec java -Dfile.encoding=UTF-8 -jar target/k12-english.jar "$@"
