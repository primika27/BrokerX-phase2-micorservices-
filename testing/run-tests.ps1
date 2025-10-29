# Simple k6 load balancing test script
Write-Host "Starting BrokerX load balancing tests"

# Download k6 if needed
if (-not (Test-Path ".\k6.exe")) {
    Write-Host "Downloading k6..."
    Invoke-WebRequest -Uri "https://github.com/grafana/k6/releases/download/v0.52.0/k6-v0.52.0-windows-amd64.zip" -OutFile "k6.zip"
    Expand-Archive -Path "k6.zip" -DestinationPath "." -Force
    Remove-Item "k6.zip"
    Write-Host "k6 installed"
}

# Start monitoring
Write-Host "Starting monitoring services..."
docker-compose up -d prometheus grafana

Start-Sleep 10

# Run tests for different instance counts
$instances = @(1, 2, 3, 4)

foreach ($count in $instances) {
    Write-Host ""
    Write-Host "Testing with $count instance(s)..."
    
    $env:INSTANCES = $count
    
    # Run k6 test
    .\k6.exe run --tag "instance_count=$count" .\testing\load-tests\brokerx-loadbalancing-test.js
    
    Write-Host "Test with $count instances completed"
    
    # Pause between tests
    if ($count -ne 4) {
        Write-Host "Waiting 30 seconds..."
        Start-Sleep 30
    }
}

Write-Host ""
Write-Host "All tests completed!"
Write-Host "Grafana: http://localhost:3000"
Write-Host "Prometheus: http://localhost:9090"