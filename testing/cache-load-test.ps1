# Script de Load Testing avec Cache Redis
# Test la performance avec et sans cache Redis activé

param(
    [int]$ConcurrentUsers = 50,
    [int]$TestDuration = 60,
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$WithCache = $true,
    [switch]$ComparePerformance = $false
)

$ErrorActionPreference = "Continue"

Write-Host "=== BrokerX Load Testing avec Cache Redis ===" -ForegroundColor Green
Write-Host "Utilisateurs simultanés: $ConcurrentUsers"
Write-Host "Durée du test: $TestDuration secondes"
Write-Host "URL de base: $BaseUrl"
Write-Host "Cache Redis activé: $WithCache"

# Fonction pour tester l'endpoint market data
function Test-MarketDataEndpoint {
    param(
        [string]$Url,
        [int]$Requests,
        [string]$TestName
    )
    
    Write-Host "`n--- Test $TestName ---" -ForegroundColor Yellow
    $results = @()
    
    for ($i = 1; $i -le $Requests; $i++) {
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        
        try {
            $response = Invoke-RestMethod -Uri $Url -Method GET -TimeoutSec 10
            $stopwatch.Stop()
            $responseTime = $stopwatch.ElapsedMilliseconds
            
            $results += @{
                RequestId = $i
                ResponseTime = $responseTime
                Success = $true
                Timestamp = Get-Date
            }
            
            if ($i % 10 -eq 0) {
                Write-Host "Progression: $i/$Requests requêtes complétées" -ForegroundColor Cyan
            }
        }
        catch {
            $stopwatch.Stop()
            $results += @{
                RequestId = $i
                ResponseTime = $stopwatch.ElapsedMilliseconds
                Success = $false
                Error = $_.Exception.Message
                Timestamp = Get-Date
            }
            Write-Host "Erreur requête $i : $($_.Exception.Message)" -ForegroundColor Red
        }
        
        # Petite pause pour éviter de surcharger
        Start-Sleep -Milliseconds 50
    }
    
    return $results
}

# Fonction pour analyser les résultats
function Analyze-Results {
    param($Results, $TestName)
    
    $successfulRequests = $Results | Where-Object { $_.Success -eq $true }
    $failedRequests = $Results | Where-Object { $_.Success -eq $false }
    
    if ($successfulRequests.Count -gt 0) {
        $avgResponseTime = ($successfulRequests | Measure-Object -Property ResponseTime -Average).Average
        $minResponseTime = ($successfulRequests | Measure-Object -Property ResponseTime -Minimum).Minimum
        $maxResponseTime = ($successfulRequests | Measure-Object -Property ResponseTime -Maximum).Maximum
        $medianResponseTime = ($successfulRequests | Sort-Object ResponseTime)[[math]::Floor($successfulRequests.Count / 2)].ResponseTime
    }
    else {
        $avgResponseTime = $minResponseTime = $maxResponseTime = $medianResponseTime = 0
    }
    
    Write-Host "`n=== Résultats $TestName ===" -ForegroundColor Green
    Write-Host "Total des requêtes: $($Results.Count)"
    Write-Host "Requêtes réussies: $($successfulRequests.Count)" -ForegroundColor Green
    Write-Host "Requêtes échouées: $($failedRequests.Count)" -ForegroundColor Red
    Write-Host "Taux de succès: $([math]::Round($successfulRequests.Count / $Results.Count * 100, 2))%"
    Write-Host "Temps de réponse moyen: $([math]::Round($avgResponseTime, 2)) ms"
    Write-Host "Temps de réponse médian: $medianResponseTime ms"
    Write-Host "Temps de réponse min: $minResponseTime ms"
    Write-Host "Temps de réponse max: $maxResponseTime ms"
    
    return @{
        TotalRequests = $Results.Count
        SuccessfulRequests = $successfulRequests.Count
        FailedRequests = $failedRequests.Count
        SuccessRate = [math]::Round($successfulRequests.Count / $Results.Count * 100, 2)
        AvgResponseTime = [math]::Round($avgResponseTime, 2)
        MedianResponseTime = $medianResponseTime
        MinResponseTime = $minResponseTime
        MaxResponseTime = $maxResponseTime
    }
}

# Test 1: Market Data API (le plus susceptible de bénéficier du cache)
Write-Host "`n=== Test 1: Market Data API ===" -ForegroundColor Magenta
$marketDataUrl = "$BaseUrl/api/market-data/quotes"
$marketDataResults = Test-MarketDataEndpoint -Url $marketDataUrl -Requests 100 -TestName "Market Data API"
$marketDataAnalysis = Analyze-Results -Results $marketDataResults -TestName "Market Data API"

# Test 2: Market Data pour des symboles spécifiques
Write-Host "`n=== Test 2: Market Data Symboles Spécifiques ===" -ForegroundColor Magenta
$specificSymbolsUrl = "$BaseUrl/api/market-data/quotes?symbols=AAPL,GOOGL,MSFT"
$specificSymbolsResults = Test-MarketDataEndpoint -Url $specificSymbolsUrl -Requests 100 -TestName "Symboles Spécifiques"
$specificSymbolsAnalysis = Analyze-Results -Results $specificSymbolsResults -TestName "Symboles Spécifiques"

# Test 3: Stress test concurrent
Write-Host "`n=== Test 3: Stress Test Concurrent ===" -ForegroundColor Magenta
$jobs = @()

for ($i = 1; $i -le $ConcurrentUsers; $i++) {
    $job = Start-Job -ScriptBlock {
        param($Url, $Requests, $UserId)
        
        $results = @()
        for ($j = 1; $j -le $Requests; $j++) {
            $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
            
            try {
                $response = Invoke-RestMethod -Uri $Url -Method GET -TimeoutSec 5
                $stopwatch.Stop()
                $results += @{
                    UserId = $UserId
                    RequestId = $j
                    ResponseTime = $stopwatch.ElapsedMilliseconds
                    Success = $true
                    Timestamp = Get-Date
                }
            }
            catch {
                $stopwatch.Stop()
                $results += @{
                    UserId = $UserId
                    RequestId = $j
                    ResponseTime = $stopwatch.ElapsedMilliseconds
                    Success = $false
                    Error = $_.Exception.Message
                    Timestamp = Get-Date
                }
            }
        }
        return $results
    } -ArgumentList $marketDataUrl, 20, $i
    
    $jobs += $job
}

Write-Host "Attente de la fin de tous les jobs concurrents..." -ForegroundColor Yellow
$allConcurrentResults = @()
foreach ($job in $jobs) {
    $jobResults = Receive-Job -Job $job -Wait
    $allConcurrentResults += $jobResults
    Remove-Job -Job $job
}

$concurrentAnalysis = Analyze-Results -Results $allConcurrentResults -TestName "Stress Test Concurrent"

# Test 4: Cache Performance - Requêtes répétées
if ($WithCache) {
    Write-Host "`n=== Test 4: Performance Cache - Requêtes Répétées ===" -ForegroundColor Magenta
    
    # Premier appel (cache miss attendu)
    Write-Host "Première série de requêtes (cache miss attendu)..." -ForegroundColor Cyan
    $firstCallResults = Test-MarketDataEndpoint -Url $marketDataUrl -Requests 10 -TestName "Cache Miss"
    Start-Sleep -Seconds 1
    
    # Deuxième appel (cache hit attendu)
    Write-Host "Deuxième série de requêtes (cache hit attendu)..." -ForegroundColor Cyan
    $secondCallResults = Test-MarketDataEndpoint -Url $marketDataUrl -Requests 10 -TestName "Cache Hit"
    
    $firstCallAnalysis = Analyze-Results -Results $firstCallResults -TestName "Cache Miss"
    $secondCallAnalysis = Analyze-Results -Results $secondCallResults -TestName "Cache Hit"
    
    $cacheImprovement = [math]::Round(($firstCallAnalysis.AvgResponseTime - $secondCallAnalysis.AvgResponseTime) / $firstCallAnalysis.AvgResponseTime * 100, 2)
    Write-Host "`nAmélioration grâce au cache: $cacheImprovement%" -ForegroundColor Green
}

# Résumé final
Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "=== RÉSUMÉ FINAL DES PERFORMANCES ===" -ForegroundColor Magenta
Write-Host "========================================" -ForegroundColor Magenta

Write-Host "`n1. Market Data API:"
Write-Host "   - Taux de succès: $($marketDataAnalysis.SuccessRate)%"
Write-Host "   - Temps de réponse moyen: $($marketDataAnalysis.AvgResponseTime) ms"

Write-Host "`n2. Symboles Spécifiques:"
Write-Host "   - Taux de succès: $($specificSymbolsAnalysis.SuccessRate)%"
Write-Host "   - Temps de réponse moyen: $($specificSymbolsAnalysis.AvgResponseTime) ms"

Write-Host "`n3. Test Concurrent ($ConcurrentUsers utilisateurs):"
Write-Host "   - Total requêtes: $($concurrentAnalysis.TotalRequests)"
Write-Host "   - Taux de succès: $($concurrentAnalysis.SuccessRate)%"
Write-Host "   - Temps de réponse moyen: $($concurrentAnalysis.AvgResponseTime) ms"

if ($WithCache -and $firstCallAnalysis -and $secondCallAnalysis) {
    Write-Host "`n4. Performance Cache:"
    Write-Host "   - Cache Miss: $($firstCallAnalysis.AvgResponseTime) ms"
    Write-Host "   - Cache Hit: $($secondCallAnalysis.AvgResponseTime) ms"
    Write-Host "   - Amélioration: $cacheImprovement%"
}

# Sauvegarde des résultats
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resultFile = ".\testing\results\cache_load_test_$timestamp.json"

$finalResults = @{
    TestConfiguration = @{
        ConcurrentUsers = $ConcurrentUsers
        TestDuration = $TestDuration
        BaseUrl = $BaseUrl
        WithCache = $WithCache
        Timestamp = Get-Date
    }
    MarketDataTest = $marketDataAnalysis
    SpecificSymbolsTest = $specificSymbolsAnalysis
    ConcurrentTest = $concurrentAnalysis
    CacheTest = if ($WithCache) { 
        @{
            CacheMiss = $firstCallAnalysis
            CacheHit = $secondCallAnalysis
            Improvement = $cacheImprovement
        } 
    } else { $null }
}

$finalResults | ConvertTo-Json -Depth 4 | Out-File -FilePath $resultFile -Encoding UTF8
Write-Host "`nRésultats sauvegardés dans: $resultFile" -ForegroundColor Green

Write-Host "`n=== Test terminé ===" -ForegroundColor Green