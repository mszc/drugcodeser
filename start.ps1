# 药品追溯码查询服务 - 启动脚本
# 用法: .\start.ps1

Write-Host "===== Drug Code Ser 启动 =====" -ForegroundColor Green

# 1. 设置控制台编码（解决中文乱码）
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

# 2. 检查 jar 文件
$jarFile = "target\drug-code-ser-1.0.0.jar"
if (-not (Test-Path $jarFile)) {
    Write-Host "[编译] 打包项目..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[错误] 编译失败，请检查代码" -ForegroundColor Red
        exit 1
    }
    Write-Host "[编译] 完成" -ForegroundColor Green
}

# 3. 关闭已运行的进程
$running = Get-Process -Name java -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "[清理] 关闭已运行的 Java 进程..." -ForegroundColor Yellow
    $running | Stop-Process -Force
    Start-Sleep -Seconds 1
}

# 4. 启动应用
Write-Host "[启动] 正在启动应用..." -ForegroundColor Green
Write-Host "[启动] Swagger: http://localhost:8080/swagger-ui.html" -ForegroundColor Cyan
Write-Host ""

java `
    --add-opens java.base/java.lang=ALL-UNNAMED `
    --add-opens java.base/java.util=ALL-UNNAMED `
    -jar $jarFile
