@echo off
setlocal enabledelayedexpansion

REM BrokerX Load Testing Script for Windows
REM Usage: run-load-tests.bat [test-type] [environment]

set TEST_TYPE=%1
set ENVIRONMENT=%2

if "%TEST_TYPE%"=="" set TEST_TYPE=load
if "%ENVIRONMENT%"=="" set ENVIRONMENT=local

set TIMESTAMP=%date:~-4,4%%date:~-10,2%%date:~-7,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set RESULTS_DIR=.\results\%TIMESTAMP%

REM Environment URLs
if "%ENVIRONMENT%"=="local" (
    set BASE_URL=http://localhost
) else if "%ENVIRONMENT%"=="staging" (
    set BASE_URL=https://staging.brokerx.com
) else if "%ENVIRONMENT%"=="prod" (
    set BASE_URL=https://api.brokerx.com
) else (
    echo Unknown environment: %ENVIRONMENT%
    exit /b 1
)

echo 🚀Starting BrokerX Load Tests
echo Environment: %ENVIRONMENT% (%BASE_URL%)
echo Test Type: %TEST_TYPE%
echo Results Directory: %RESULTS_DIR%

REM Create results directory
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"

REM Health check before testing
echo 📊 Performing pre-test health check...
curl -s "%BASE_URL%/api/health" >nul 2>&1
if errorlevel 1 (
    echo ❌ Health check failed. System may be down.
    exit /b 1
)
echo ✅ System is healthy

REM Run the appropriate test
if "%TEST_TYPE%"=="load" (
    echo 🔄 Running load test...
    k6 run --out json="%RESULTS_DIR%\load-test-results.json" --env BASE_URL="%BASE_URL%" .\brokerx-load-test.js
) else if "%TEST_TYPE%"=="stress" (
    echo ⚡ Running stress test...
    k6 run --out json="%RESULTS_DIR%\stress-test-results.json" --env BASE_URL="%BASE_URL%" .\stress-test.js
) else if "%TEST_TYPE%"=="spike" (
    echo 🌊 Running spike test...
    k6 run --out json="%RESULTS_DIR%\spike-test-results.json" --env BASE_URL="%BASE_URL%" .\spike-test.js
) else (
    echo Unknown test type: %TEST_TYPE%
    echo Available types: load, stress, spike
    exit /b 1
)

REM Post-test health check
echo 🔍 Performing post-test health check...
curl -s "%BASE_URL%/api/health" >nul 2>&1
if errorlevel 1 (
    echo ⚠️  Warning: System may be degraded after testing
) else (
    echo ✅ System is still healthy
)

echo 📈 Test completed. Results saved to: %RESULTS_DIR%
echo 🔗 View Grafana dashboard: http://localhost:3000
echo 🎉 Load testing complete!

pause