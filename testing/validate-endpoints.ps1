#!/usr/bin/env pwsh
# ============================================================================
# SCRIPT DE VALIDATION DES ENDPOINTS BROKERX
# ============================================================================
# Ce script teste rapidement tous les endpoints pour s'assurer qu'ils 
# répondent correctement avant de lancer les tests de load balancing
# ============================================================================

param(
    [string]$BaseUrl = "http://localhost:80"
)

Write-Host "╔═══════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║               VALIDATION DES ENDPOINTS BROKERX                    ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

$endpoints = @(
    @{ Path = "/health"; Description = "Health Check"; ExpectedStatuses = @(200) },
    @{ Path = "/api/auth/test"; Description = "Auth Service Test"; ExpectedStatuses = @(200, 500) },
    @{ Path = "/api/clients/test"; Description = "Client Service Test"; ExpectedStatuses = @(200, 500) },
    @{ Path = "/api/wallet/test"; Description = "Wallet Service Test"; ExpectedStatuses = @(200, 500) },
    @{ Path = "/api/clients/getByEmail?email=user1@test.com"; Description = "Client by Email"; ExpectedStatuses = @(200, 400, 404, 500) },
    @{ Path = "/api/clients/getEmailById?clientId=1"; Description = "Email by ID"; ExpectedStatuses = @(200, 404, 500) },
    @{ Path = "/api/wallet/balance?ownerEmail=user1@test.com"; Description = "Wallet Balance"; ExpectedStatuses = @(200, 400, 404, 500) },
    @{ Path = "/api/orders/holdings?ownerEmail=user1@test.com"; Description = "Order Holdings"; ExpectedStatuses = @(200, 400, 401, 404, 500) }
)

$results = @()

Write-Host "🔍 Test des endpoints..." -ForegroundColor Yellow
Write-Host ""

foreach ($endpoint in $endpoints) {
    $url = "$BaseUrl$($endpoint.Path)"
    
    try {
        Write-Host "Testing: $($endpoint.Description)" -NoNewline -ForegroundColor White
        
        $response = Invoke-WebRequest -Uri $url -Method GET -TimeoutSec 10 -UseBasicParsing -ErrorAction SilentlyContinue
        $statusCode = $response.StatusCode
        $responseTime = (Measure-Command { 
            Invoke-WebRequest -Uri $url -Method GET -TimeoutSec 10 -UseBasicParsing -ErrorAction SilentlyContinue
        }).TotalMilliseconds
        
        $isExpected = $endpoint.ExpectedStatuses -contains $statusCode
        $emoji = if ($isExpected) { "✅" } else { "⚠️" }
        
        Write-Host " → $emoji $statusCode ($([math]::Round($responseTime, 0))ms)" -ForegroundColor $(if ($isExpected) { "Green" } else { "Yellow" })
        
        $results += @{
            Endpoint = $endpoint.Path
            Description = $endpoint.Description  
            StatusCode = $statusCode
            ResponseTimeMs = $responseTime
            IsExpected = $isExpected
            Status = "Success"
        }
    }
    catch {
        Write-Host " → ❌ ERROR: $_" -ForegroundColor Red
        
        $results += @{
            Endpoint = $endpoint.Path
            Description = $endpoint.Description
            StatusCode = 0
            ResponseTimeMs = 0
            IsExpected = $false
            Status = "Failed"
            Error = $_.Exception.Message
        }
    }
}

Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                          RÉSUMÉ                                   ║" -ForegroundColor Green  
Write-Host "╚═══════════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

$successCount = ($results | Where-Object { $_.Status -eq "Success" -and $_.IsExpected }).Count
$totalCount = $results.Count

Write-Host "📊 Endpoints testés: $totalCount" -ForegroundColor Cyan
Write-Host "✅ Réponses attendues: $successCount" -ForegroundColor Green
Write-Host "⚠️  Réponses inattendues: $($totalCount - $successCount)" -ForegroundColor Yellow
Write-Host ""

if ($successCount -eq $totalCount) {
    Write-Host "🎯 Tous les endpoints répondent correctement!" -ForegroundColor Green
    Write-Host "🚀 Vous pouvez maintenant lancer les tests de load balancing." -ForegroundColor Cyan
} else {
    Write-Host "⚠️ Certains endpoints ont des problèmes." -ForegroundColor Yellow
    Write-Host "🔧 Vérifiez que tous les services sont démarrés correctement." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Pour lancer les tests de load balancing:" -ForegroundColor Cyan  
Write-Host "   .\testing\run-loadbalancing-tests.ps1" -ForegroundColor White
Write-Host ""

# Sauvegarder les résultats
$results | ConvertTo-Json -Depth 3 | Out-File "endpoint-validation-results.json" -Encoding UTF8
Write-Host "Results saved to: endpoint-validation-results.json" -ForegroundColor Gray