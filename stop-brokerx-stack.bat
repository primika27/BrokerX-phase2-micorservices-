@echo off
echo ===========================================
echo    BrokerX Microservices Stack - Stop
echo ===========================================
echo.

echo Stopping monitoring stack...
docker-compose -f docker-compose.monitoring.yml down

echo.
echo Stopping Spring Boot applications...
taskkill /f /im "java.exe" > nul 2>&1
taskkill /f /im "javaw.exe" > nul 2>&1

echo.
echo All services have been stopped.
echo.
pause