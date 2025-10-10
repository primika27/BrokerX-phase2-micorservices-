@echo off
echo ===========================================
echo    BrokerX Microservices Stack - Start
echo ===========================================
echo.

echo Starting monitoring stack (Prometheus + Grafana)...
docker-compose -f docker-compose.monitoring.yml up -d

echo.
echo Waiting for monitoring stack to be ready...
timeout /t 10 /nobreak > nul

echo.
echo Starting BrokerX microservices...
echo.

echo [1/5] Starting Gateway Service (Port 8080)...
start "Gateway Service" cmd /k "cd gatewayService && mvnw spring-boot:run"
timeout /t 5 /nobreak > nul

echo [2/5] Starting Auth Service (Port 8081)...
start "Auth Service" cmd /k "cd authService\authService && mvnw spring-boot:run"
timeout /t 5 /nobreak > nul

echo [3/5] Starting Client Service (Port 8082)...
start "Client Service" cmd /k "cd clientService\clientService && mvnw spring-boot:run"
timeout /t 5 /nobreak > nul

echo [4/5] Starting Wallet Service (Port 8083)...
start "Wallet Service" cmd /k "cd walletService\walletService && mvnw spring-boot:run"
timeout /t 5 /nobreak > nul

echo [5/5] Starting Order Service (Port 8084)...
start "Order Service" cmd /k "cd orderService\orderService && mvnw spring-boot:run"

echo.
echo ===========================================
echo    All services are starting up...
echo ===========================================
echo.

echo Service URLs:
echo - API Gateway: http://localhost:8080
echo - Auth Service: http://localhost:8081
echo - Client Service: http://localhost:8082
echo - Wallet Service: http://localhost:8083
echo - Order Service: http://localhost:8084
echo.

echo Observability URLs:
echo - Prometheus: http://localhost:9090
echo - Grafana: http://localhost:3000 (admin/admin)
echo.

echo Swagger Documentation:
echo - Gateway API: http://localhost:8080/swagger-ui.html
echo - Auth Service: http://localhost:8081/swagger-ui.html
echo - Client Service: http://localhost:8082/swagger-ui.html
echo - Wallet Service: http://localhost:8083/swagger-ui.html
echo - Order Service: http://localhost:8084/swagger-ui.html
echo.

echo Health Checks:
echo - Gateway: http://localhost:8080/actuator/health
echo - Auth Service: http://localhost:8081/actuator/health
echo - Client Service: http://localhost:8082/actuator/health
echo - Wallet Service: http://localhost:8083/actuator/health
echo - Order Service: http://localhost:8084/actuator/health
echo.

echo Metrics Endpoints:
echo - Gateway: http://localhost:8080/actuator/prometheus
echo - Auth Service: http://localhost:8081/actuator/prometheus
echo - Client Service: http://localhost:8082/actuator/prometheus
echo - Wallet Service: http://localhost:8083/actuator/prometheus
echo - Order Service: http://localhost:8084/actuator/prometheus
echo.

echo Please wait ~30 seconds for all services to fully start...
pause