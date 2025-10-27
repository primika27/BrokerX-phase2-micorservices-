@echo off
echo === BrokerX Load Testing with curl ===
echo.

echo Testing Load Balancer Health...
curl -s http://localhost:80/health
echo.

echo Running 50 requests to test load balancing...
for /L %%i in (1,1,50) do (
    echo Request %%i...
    curl -s http://localhost:80/api/health > nul
    timeout /t 1 /nobreak > nul
)

echo.
echo Testing Authentication Service...
for /L %%i in (1,1,20) do (
    echo Auth Request %%i...
    curl -s -X POST http://localhost:80/auth/login -H "Content-Type: application/json" -d "{\"email\":\"test@example.com\",\"password\":\"test123\"}" > nul
    timeout /t 1 /nobreak > nul
)

echo.
echo Load testing complete! Check Grafana at http://localhost:3000
pause