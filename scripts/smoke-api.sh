#!/usr/bin/env bash
# 后端接口冒烟测试。
#
# 用法：先启动应用，再执行本脚本。
#   java -jar target/k12-english.jar --server.port=8099 --app.open-browser=false
#   bash scripts/smoke-api.sh http://127.0.0.1:8099
#
# 覆盖：元信息、词库、设置读写、学习队列、作答落库、学情聚合、备份导出导入、静态资源、异常处理、404 语义。
set -uo pipefail

B="${1:-http://127.0.0.1:8099}"
PASS=0
FAIL=0

ok()   { PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
head_() { printf '\n\033[1m%s\033[0m\n' "$1"; }

expect_match() { # $1=名称 $2=期望正则 $3=实际
  if printf '%s' "$3" | grep -Eq "$2"; then ok "$1"; else bad "$1（实际：$(printf '%s' "$3" | head -c 240)）"; fi
}
expect_code() { # $1=名称 $2=期望码 $3=实际码
  if [ "$2" = "$3" ]; then ok "$1"; else bad "$1（期望 $2 实际 $3）"; fi
}

# 临时文件放在 target/ 下：既在 .gitignore 里，又是相对路径，
# 不会踩到 Windows 版 curl 与安全删除防护的路径转换问题。
TMP="target/smoke"
mkdir -p "$TMP"
get() { curl -s -o "$TMP/body" -w '%{http_code}' "$1"; }

head_ "1. 元信息与词库"
code=$(get "$B/api/meta"); body=$(cat "$TMP/body")
expect_code "GET /api/meta 200" 200 "$code"
expect_match "词库 6877 条" '"totalWords":6877' "$body"

code=$(get "$B/api/stages")
expect_match "GET /api/stages 含三学段" '"primary","junior","senior"' "$(cat "$TMP/body")"

code=$(get "$B/api/words"); body=$(cat "$TMP/body")
expect_code "GET /api/words 200" 200 "$code"
expect_match "返回 words 数组" '"words":\[\{"w":' "$body"
expect_match "返回 6877 条词" '"total":6877' "$body"

code=$(get "$B/api/words/pool?stages=primary&books=%E5%B0%8F%E5%AD%A6%E8%8B%B1%E8%AF%AD(%E4%B8%89%E8%B5%B7)%E4%B8%89%E5%B9%B4%E7%BA%A7%E4%B8%8A%E5%86%8C")
expect_code "GET /api/words/pool 200" 200 "$code"

code=$(get "$B/api/books?stages=primary")
expect_match "GET /api/books 返回册次" '^\[' "$(cat "$TMP/body")"

head_ "2. 设置读写"
code=$(get "$B/api/settings")
expect_match "GET /api/settings 默认 newPerDay=20" '"newPerDay":20' "$(cat "$TMP/body")"

code=$(curl -s -o "$TMP/body" -w '%{http_code}' -X PUT "$B/api/settings" \
  -H 'Content-Type: application/json' \
  -d '{"stages":["primary"],"books":[],"newPerDay":5,"reviewPerDay":30,"mode":"spell","accent":"uk","sound":false}')
expect_code "PUT /api/settings 200" 200 "$code"
expect_match "设置已生效（newPerDay=5）" '"newPerDay":5' "$(cat "$TMP/body")"

# 还原默认设置，避免影响后续断言
curl -s -o /dev/null -X PUT "$B/api/settings" -H 'Content-Type: application/json' \
  -d '{"stages":["primary","junior","senior"],"books":[],"newPerDay":20,"reviewPerDay":120,"mode":"mix","accent":"us","sound":true}'

head_ "3. 学习队列"
code=$(get "$B/api/study/queue"); body=$(cat "$TMP/body")
expect_code "GET /api/study/queue 200" 200 "$code"
expect_match "含 reviews 字段" '"reviews":\[' "$body"
expect_match "含 fresh 字段" '"fresh":\[' "$body"
expect_match "新词队列非空" '"fresh":\["[^"]+' "$body"

head_ "4. 作答落库（POST /api/review）"
NOW=$(date +%s)000
cat > "$TMP/review.json" <<EOF
{
  "card": {
    "w": "apple", "due": ${NOW}, "stability": 1.5, "difficulty": 5.0,
    "elapsed_days": 0, "scheduled_days": 1, "learning_steps": 0,
    "reps": 1, "lapses": 0, "state": 1, "last_review": ${NOW},
    "seen": 1, "wrong": 0, "firstSeen": ${NOW}
  },
  "log": { "w": "apple", "ts": ${NOW}, "grade": 3, "mode": "spell", "correct": true }
}
EOF
code=$(curl -s -o "$TMP/body" -w '%{http_code}' -X POST "$B/api/review" \
  -H 'Content-Type: application/json' --data-binary "@$TMP/review.json")
expect_code "POST /api/review 200" 200 "$code"
expect_match "返回带自增 id 的日志" '"id":[1-9][0-9]*' "$(cat "$TMP/body")"

code=$(get "$B/api/meta"); body=$(cat "$TMP/body")
expect_match "卡片已计入（totalCards=1）" '"totalCards":1' "$body"
expect_match "日志已计入（totalLogs=1）" '"totalLogs":1' "$body"

head_ "5. 学情聚合"
code=$(get "$B/api/stats"); body=$(cat "$TMP/body")
expect_code "GET /api/stats 200" 200 "$code"
expect_match "含 daily 14 天序列" '"daily":\[' "$body"
expect_match "含 streak" '"streak":' "$body"
expect_match "含 accuracy" '"accuracy":' "$body"
expect_match "含 byStage" '"byStage":\{' "$body"
expect_match "今日学习数 1" '"today":1' "$body"
expect_match "总卡片数 1" '"totalCards":1' "$body"

head_ "6. 状态引导"
code=$(get "$B/api/state"); body=$(cat "$TMP/body")
expect_code "GET /api/state 200" 200 "$code"
expect_match "含 settings" '"settings":\{' "$body"
expect_match "含 cards" '"cards":\[' "$body"
expect_match "含 logs" '"logs":\[' "$body"

head_ "7. 备份导出与导入"
code=$(get "$B/api/data/export"); body=$(cat "$TMP/body")
expect_code "GET /api/data/export 200" 200 "$code"
expect_match "导出含 version=1" '"version":1' "$body"
printf '%s' "$body" > "$TMP/backup.json"

code=$(curl -s -o "$TMP/body" -w '%{http_code}' -X POST "$B/api/data/import" \
  -H 'Content-Type: application/json' --data-binary "@$TMP/backup.json")
expect_code "POST /api/data/import 200" 200 "$code"

code=$(get "$B/api/meta")
expect_match "导入后卡片仍为 1（未重复）" '"totalCards":1' "$(cat "$TMP/body")"

head_ "8. 重置"
code=$(curl -s -o "$TMP/body" -w '%{http_code}' -X POST "$B/api/data/reset")
expect_code "POST /api/data/reset 200" 200 "$code"
code=$(get "$B/api/meta"); body=$(cat "$TMP/body")
expect_match "卡片已清空" '"totalCards":0' "$body"
expect_match "日志已清空" '"totalLogs":0' "$body"

head_ "9. 静态资源"
code=$(get "$B/")
expect_code "GET / 返回首页 200" 200 "$code"
expect_match "首页是 HTML" 'id="root"' "$(cat "$TMP/body")"

code=$(get "$B/audio/apple.mp3")
expect_code "GET /audio/apple.mp3 200" 200 "$code"

head_ "10. 异常处理"
code=$(curl -s -o "$TMP/body" -w '%{http_code}' -X POST "$B/api/review" \
  -H 'Content-Type: application/json' -d '{"card":null,"log":null}')
expect_code "空卡片不崩（200）" 200 "$code"

code=$(curl -s -o "$TMP/body" -w '%{http_code}' -X POST "$B/api/review" \
  -H 'Content-Type: application/json' -d '{"card":{"w":')
expect_code "畸形 JSON 返回 400" 400 "$code"
expect_match "错误体结构化" '"error":"bad_request"' "$(cat "$TMP/body")"

# 回归保护：这些路径本该是 404。曾经因为 Exception 兜底把它们变成 500，
# 而浏览器每次开页面都会请求 /favicon.ico，日志会被堆栈刷满。
code=$(get "$B/favicon.ico"); body=$(cat "$TMP/body")
expect_code "不存在的静态资源返回 404（不是 500）" 404 "$code"
expect_match "404 错误体结构化" '"error":"not_found"' "$body"

code=$(get "$B/api/no-such-endpoint")
expect_code "不存在的接口返回 404" 404 "$code"

code=$(get "$B/assets/index-0000000000.js")
expect_code "旧哈希的 JS 返回 404" 404 "$code"

printf '\n\033[1m结果：%d 通过，%d 失败\033[0m\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
