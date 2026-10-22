@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"
title K12 英语学习 · 公网分享

echo ==========================================
echo   K12 英语学习平台 · 公网分享
echo ==========================================
echo.

rem ---------- 1. 找 cloudflared ----------
set "CF=C:\Program Files (x86)\cloudflared\cloudflared.exe"
if not exist "%CF%" set "CF=C:\Program Files\cloudflared\cloudflared.exe"
if not exist "%CF%" (
  where cloudflared >nul 2>nul
  if errorlevel 1 (
    echo [错误] 没找到 cloudflared。
    echo        先装一次（约 40MB，只需一次）：
    echo          winget install --id Cloudflare.cloudflared -e
    echo.
    pause
    exit /b 1
  )
  set "CF=cloudflared"
)

rem ---------- 2. 缺 jar 就先打包 ----------
if not exist "target\k12-english.jar" (
  echo [1/3] 没找到 target\k12-english.jar，正在打包...
  echo       首次会把 6877 个发音 mp3 复制进 jar，可能明显偏慢。
  call mvnw.cmd -B -DskipTests package
  if errorlevel 1 (
    echo [错误] 打包失败。
    echo.
    pause
    exit /b 1
  )
)

rem ---------- 3. 读访问口令 ----------
set "TOKEN_FILE=%USERPROFILE%\.k12-english\access.token"
set "TOKEN="
if exist "!TOKEN_FILE!" (
  for /f "usebackq delims=" %%i in ("!TOKEN_FILE!") do (
    if not defined TOKEN set "TOKEN=%%i"
  )
)

rem ---------- 4. 起服务 ----------
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>nul
if errorlevel 1 (
  echo [2/3] 正在后台启动服务...
  start "K12 英语服务" /min java -Dfile.encoding=UTF-8 -jar target\k12-english.jar --server.port=8080 --app.open-browser=false
) else (
  echo [2/3] 检测到 8080 已在监听，复用现有服务。
)

echo        等待端口就绪...
powershell -NoProfile -ExecutionPolicy Bypass -Command "for($i=0;$i -lt 120;$i++){try{$c=New-Object Net.Sockets.TcpClient('127.0.0.1',8080);$c.Close();exit 0}catch{Start-Sleep -Milliseconds 500}};exit 1"
if errorlevel 1 (
  echo [错误] 服务没能在 60 秒内起来。先单独运行 start.bat 看看报什么错。
  echo.
  pause
  exit /b 1
)

rem 服务第一次启动才会生成口令文件，所以这里再读一次
if not defined TOKEN (
  if exist "!TOKEN_FILE!" (
    for /f "usebackq delims=" %%i in ("!TOKEN_FILE!") do (
      if not defined TOKEN set "TOKEN=%%i"
    )
  )
)

rem ---------- 5. 开隧道 ----------
echo [3/3] 正在建立 Cloudflare 隧道...
echo.
echo   出现 https://xxx.trycloudflare.com 后，把带 ?k= 的完整地址发出去。
echo   手机 / 平板直接打开即可，不用再找口令。
echo.
echo   关掉本窗口 = 公网访问断开（本机服务仍在另一个最小化窗口里跑）。
echo.
set "CF_PATH=!CF!"
set "K12_TOKEN=!TOKEN!"
powershell -NoProfile -ExecutionPolicy Bypass -Command "& $env:CF_PATH tunnel --no-autoupdate --url http://127.0.0.1:8080 2>&1 | ForEach-Object { $line = [string]$_; Write-Host $line; if ($line -match 'https://([a-z0-9-]+)\.trycloudflare\.com') { $u = 'https://' + $Matches[1] + '.trycloudflare.com'; Write-Host ''; Write-Host '==================================================' -ForegroundColor Green; Write-Host ' 公网地址（手机 / 平板直接打开）' -ForegroundColor Green; if ($env:K12_TOKEN) { Write-Host ('  ' + $u + '/?k=' + $env:K12_TOKEN) -ForegroundColor Yellow } else { Write-Host ('  ' + $u) -ForegroundColor Yellow; Write-Host '  （没读到口令文件，打开后按提示输入口令）' -ForegroundColor DarkGray }; Write-Host '==================================================' -ForegroundColor Green; Write-Host '' } }"

echo.
echo 隧道已关闭，公网访问断开。
echo 本机服务还在那个最小化的「K12 英语服务」窗口里跑，关掉它才真正停止。
pause
