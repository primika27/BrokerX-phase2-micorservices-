param(
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host "Testing BrokerX Endpoints" -ForegroundColor Cyan
Write-Host "=========================" -ForegroundColor Cyan
Write-Host ""

$endpoints = @(
    @{ Path = "/actuator/health"; Description = "Health Check"; ExpectedStatuses = @(200) },
    @{ Path = "/api/auth/test"; Description = "Auth Service Test"; ExpectedStatuses = @(200, 500) },
    @{ Path = "/api/clients/test"; Description = "Client Service Test"; ExpectedStatuses = @(200, 500) },
    @{ Path = "/api/wallet/test"; Description = "Wallet Service Test"; ExpectedStatuses = @(200, 500) },
    @{ Path = "/api/clients/getByEmail?email=user1@test.com"; Description = "Client by Email"; ExpectedStatuses = @(200, 400, 404, 500) },
    @{ Path = "/api/clients/getEmailById?clientId=1"; Description = "Email by ID"; ExpectedStatuses = @(200, 404, 500) },
    @{ Path = "/api/wallet/balance?ownerEmail=user1@test.com"; Description = "Wallet Balance"; ExpectedStatuses = @(200, 400, 404, 500) }
)

$results = @()

Write-Host "Testing endpoints..." -ForegroundColor Yellow
Write-Host ""

foreach ($endpoint in $endpoints) {
    $url = "$BaseUrl$($endpoint.Path)"
    
    try {
        Write-Host "Testing: $($endpoint.Description)" -NoNewline -ForegroundColor White
        
        $response = Invoke-WebRequest -Uri $url -Method GET -TimeoutSec 10 -UseBasicParsing -ErrorAction SilentlyContinue
        $statusCode = $response.StatusCode
        
        $isExpected = $endpoint.ExpectedStatuses -contains $statusCode
        $emoji = if ($isExpected) { " -> OK" } else { " -> WARN" }
        
        Write-Host "$emoji $statusCode" -ForegroundColor $(if ($isExpected) { "Green" } else { "Yellow" })
        
        $results += @{
            Endpoint = $endpoint.Path
            Description = $endpoint.Description  
            StatusCode = $statusCode
            IsExpected = $isExpected
            Status = "Success"
        }
    }
    catch {
        Write-Host " -> ERROR: $($_.Exception.Message)" -ForegroundColor Red
        
        $results += @{
            Endpoint = $endpoint.Path
            Description = $endpoint.Description
            StatusCode = 0
            IsExpected = $false
            Status = "Failed"
            Error = $_.Exception.Message
        }
    }
}

Write-Host ""
Write-Host "SUMMARY" -ForegroundColor Green
Write-Host "=======" -ForegroundColor Green
Write-Host ""

$successCount = ($results | Where-Object { $_.Status -eq "Success" -and $_.IsExpected }).Count
$totalCount = $results.Count

Write-Host "Endpoints tested: $totalCount" -ForegroundColor Cyan
Write-Host "Expected responses: $successCount" -ForegroundColor Green
Write-Host "Unexpected responses: $($totalCount - $successCount)" -ForegroundColor Yellow
Write-Host ""

if ($successCount -eq $totalCount) {
    Write-Host "All endpoints responding correctly!" -ForegroundColor Green
    Write-Host "Ready for load balancing tests." -ForegroundColor Cyan
} else {
    Write-Host "Some endpoints have issues." -ForegroundColor Yellow
    Write-Host "Check that all services are started correctly." -ForegroundColor Yellow
}

$results | ConvertTo-Json -Depth 3 | Out-File "endpoint-validation-results.json" -Encoding UTF8
Write-Host ""
Write-Host "Results saved to: endpoint-validation-results.json" -ForegroundColor Gray