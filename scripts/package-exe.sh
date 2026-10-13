#!/usr/bin/env bash
# 用 JDK 自带的 jpackage 打一个免安装的 Windows 应用目录，再压成 zip。
#
#   bash scripts/package-exe.sh
#
# 产物：dist/K12English-<版本>-windows-x64.zip
# 解压后双击 K12English.exe 即可运行（自带精简 JRE，目标机器无需装 Java）。
#
# 注意：
#   - 只支持 Windows（jpackage 的 --type app-image 在 macOS/Linux 上产出对应平台格式）
#   - 必须用 JDK 17+，且该 JDK 带 jpackage / jlink / jmods（Temurin、Zulu、Oracle JDK 都有）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APP_NAME="${APP_NAME:-K12English}"   # 用 ASCII 名，避免中文 exe 名在部分环境下的编码问题
VERSION="${VERSION:-1.0.0}"
JAR="k12-english.jar"
MAIN_CLASS="org.springframework.boot.loader.launch.JarLauncher"

# ---- 定位 JDK ----
if [ -n "${JAVA_HOME:-}" ]; then
  JDK="$JAVA_HOME"
elif command -v java >/dev/null 2>&1; then
  JDK="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
else
  echo "[ERROR] 找不到 JDK。请设置 JAVA_HOME 指向 JDK 17+" >&2
  exit 1
fi

for tool in jpackage jlink; do
  if [ ! -x "$JDK/bin/$tool" ] && [ ! -x "$JDK/bin/$tool.exe" ]; then
    echo "[ERROR] $JDK 里没有 $tool，需要完整 JDK（不是 JRE）" >&2
    exit 1
  fi
done
echo "==> 使用 JDK: $JDK"

# ---- 1. 构建 fat jar ----
echo "==> 构建 $JAR"
./mvnw -B -ntp package

if [ ! -f "target/$JAR" ]; then
  echo "[ERROR] 没找到 target/$JAR" >&2
  exit 1
fi

# ---- 2. 准备只含 jar 的暂存目录 ----
# jpackage 的 --input 会把目录里所有东西都塞进去，所以不能直接给 target/
STAGE="build/jpackage-input"
rm -rf build/jpackage
mkdir -p "$STAGE" dist
cp "target/$JAR" "$STAGE/"

# ---- 3. 生成 app-image ----
echo "==> jpackage 生成应用目录（首次会 jlink 出精简 JRE，约 1-2 分钟）"
rm -rf "dist/$APP_NAME"
"$JDK/bin/jpackage" \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$VERSION" \
  --input "$STAGE" \
  --main-jar "$JAR" \
  --main-class "$MAIN_CLASS" \
  --dest dist \
  --win-console \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "-Dsun.jnu.encoding=UTF-8"

if [ ! -d "dist/$APP_NAME" ]; then
  echo "[ERROR] jpackage 没有产出 dist/$APP_NAME" >&2
  exit 1
fi

# ---- 4. 附带一个说明文件 ----
cat > "dist/$APP_NAME/使用说明.txt" <<'EOF'
K12 英语学习平台 - 免安装版
================================

双击 K12English.exe 启动。

启动后会自动打开浏览器；如果没有，请手动访问 http://127.0.0.1:8080

学习进度保存在：%USERPROFILE%\.k12-english\progress.db
（卸载时删掉这个目录即可清空全部记录；想换电脑就把这个文件拷过去）

关闭那个黑色控制台窗口即停止服务。

换端口：
  在命令行里执行  K12English.exe --server.port=9000
EOF

# ---- 5. 压成 zip ----
ZIP="dist/$APP_NAME-$VERSION-windows-x64.zip"
echo "==> 压缩 $ZIP"
rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
  (cd dist && zip -qr "$(basename "$ZIP")" "$APP_NAME")
elif command -v powershell >/dev/null 2>&1; then
  powershell -NoProfile -Command \
    "Compress-Archive -Path 'dist/$APP_NAME' -DestinationPath '$ZIP' -Force"
else
  echo "[WARN] 没有 zip 也没有 powershell，跳过压缩。应用目录已在 dist/$APP_NAME"
  exit 0
fi

echo
echo "==> 完成"
echo "    应用目录: dist/$APP_NAME"
echo "    分发包  : $ZIP"
ls -lh "$ZIP" 2>/dev/null || true
