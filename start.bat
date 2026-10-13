@echo off
chcp 65001 >nul
cd /d "%~dp0"
title K12 英语学习平台

echo ==========================================
echo   K12 英语学习平台
echo ==========================================
echo.

rem ---------- 1. 检查 Java ----------
where java >nul 2>nul
if errorlevel 1 (
  echo [错误] 没找到 java 命令。
  echo        请先安装 JDK 17 或更高版本：https://adoptium.net
  echo.
  pause
  exit /b 1
)

rem ---------- 2. 缺前端产物就先构建 ----------
if not exist "frontend\dist\index.html" (
  echo [1/2] 前端产物不存在，正在构建...
  where npm >nul 2>nul
  if errorlevel 1 (
    echo [错误] 需要 Node.js 才能构建前端。请安装 Node 22+：https://nodejs.org
    echo        或者从 GitHub Release 下载已经打包好的 zip。
    echo.
    pause
    exit /b 1
  )
  pushd frontend
  if not exist "node_modules" call npm install
  call npm run build
  if errorlevel 1 (
    echo [错误] 前端构建失败。
    popd
    pause
    exit /b 1
  )
  popd
)

rem ---------- 3. 缺 jar 就打包 ----------
if not exist "target\k12-english.jar" (
  echo [2/2] 正在构建后端...
  echo       首次构建要把 6877 个发音 mp3 复制进 jar，可能明显偏慢（详见 README）。
  call mvnw.cmd -B -DskipTests package
  if errorlevel 1 (
    echo [错误] 后端构建失败。
    echo.
    pause
    exit /b 1
  )
)

rem ---------- 4. 启动 ----------
echo.
echo 正在启动，浏览器会自动打开 http://127.0.0.1:8080
echo 关闭本窗口即停止服务。
echo.
java -Dfile.encoding=UTF-8 -jar target\k12-english.jar %*

echo.
echo 服务已停止。
pause
