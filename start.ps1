# Drug Code Ser - Start Script
# Usage: .\start.ps1

Write-Host "===== Drug Code Ser Start =====" -ForegroundColor Green

# 1. Set console encoding (fix Chinese garbled text)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

# 2. Check jar file
$jarFile = "target\drug-code-ser-1.0.0.jar"
if (-not (Test-Path $jarFile)) {
    Write-Host "[Build] Packaging project..." -ForegroundColor Yellow
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[Error] Build failed, please check code" -ForegroundColor Red
        exit 1
    }
    Write-Host "[Build] Done" -ForegroundColor Green
}

# 3. Kill running Java processes
$running = Get-Process -Name java -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "[Clean] Stopping running Java process..." -ForegroundColor Yellow
    $running | Stop-Process -Force
    Start-Sleep -Seconds 1
}

# 4. Start application
Write-Host "[Start] Starting application..." -ForegroundColor Green
Write-Host "[Start] Swagger: http://localhost:8080/swagger-ui.html" -ForegroundColor Cyan
Write-Host ""

java `
    --add-opens java.base/java.lang=ALL-UNNAMED `
    --add-opens java.base/java.util=ALL-UNNAMED `
    -jar $jarFile