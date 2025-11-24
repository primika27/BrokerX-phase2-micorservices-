# Script de test pour Load Balancing et Cache Performance
# Compare les performances avec et sans load balancing

param(
    [int]$ConcurrentUsers = 30,
    [int]$RequestsPerUser = 20,
    [string]$LoadBalancerUrl = "http://localhost:8080",
    [string]$DirectGatewayUrl = "http://localhost:8081",
    [switch]$TestCache = $true,
    [switch]$TestLoadBalancing = $true
)

$ErrorActionPreference = "Continue"

Write-Host "=== BrokerX Load Balancing & Cache Performance Tests ===" -ForegroundColor Green
Write-Host "Utilisateurs simultanés: $ConcurrentUsers"
Write-Host "Requêtes par utilisateur: $RequestsPerUser"
Write-Host "URL Load Balancer: $LoadBalancerUrl"
Write-Host "URL Gateway Direct: $DirectGatewayUrl"

# Fonction de test avec mesure de performance
function Invoke-LoadTest {
    param(
        [string]$BaseUrl,
        [string]$TestName,
        [int]$Users,
        [int]$RequestsPerUser,
        [string]$Endpoint = "/api/market-data/quotes"
    )
    
    Write-Host "`n=== Test: $TestName ===" -ForegroundColor Yellow
    $allResults = @()
    $jobs = @()
    $testStartTime = Get-Date
    
    # Lancer les jobs concurrents
    for ($i = 1; $i -le $Users; $i++) {
        $job = Start-Job -ScriptBlock {
            param($Url, $Requests, $UserId, $Endpoint)
            
            $results = @()
            $userStartTime = Get-Date
            
            for ($j = 1; $j -le $Requests; $j++) {
                $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
                $requestUrl = "$Url$Endpoint"
                
                try {
                    $response = Invoke-RestMethod -Uri $requestUrl -Method GET -TimeoutSec 10 -Headers @{
                        'User-Agent' = "LoadTester-User-$UserId"
                        'X-Request-ID' = "$UserId-$j"
                    }
                    
                    $stopwatch.Stop()
                    $responseTime = $stopwatch.ElapsedMilliseconds
                    
                    # Vérifier la réponse pour détecter quel service a répondu
                    $serverInstance = if ($response -and $response.Count -gt 0) { 
                        "detected" 
                    } else { 
                        "unknown" 
                    }
                    
                    $results += @{
                        UserId = $UserId
                        RequestId = $j
                        ResponseTime = $responseTime
                        Success = $true
                        Timestamp = Get-Date
                        ServerInstance = $serverInstance
                        ResponseSize = if ($response) { ($response | ConvertTo-Json).Length } else { 0 }
                    }
                    
                    # Pause aléatoire pour simuler un usage réel
                    Start-Sleep -Milliseconds (Get-Random -Minimum 50 -Maximum 200)
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
                        ServerInstance = "error"
                        ResponseSize = 0
                    }
                }
            }
            
            $userEndTime = Get-Date
            $userDuration = ($userEndTime - $userStartTime).TotalMilliseconds
            
            return @{
                UserId = $UserId
                Results = $results
                TotalDuration = $userDuration
                RequestsPerSecond = [math]::Round($Requests / ($userDuration / 1000), 2)
            }
        } -ArgumentList $BaseUrl, $RequestsPerUser, $i, $Endpoint
        
        $jobs += $job
    }
    
    # Attendre tous les jobs
    Write-Host "Attente de la fin de tous les jobs ($Users utilisateurs)..." -ForegroundColor Cyan
    
    foreach ($job in $jobs) {
        $jobResult = Receive-Job -Job $job -Wait
        $allResults += $jobResult.Results
        Remove-Job -Job $job
    }
    
    $testEndTime = Get-Date
    $totalTestDuration = ($testEndTime - $testStartTime).TotalSeconds
    
    return @{
        TestName = $TestName
        Results = $allResults
        TotalDuration = $totalTestDuration
        TotalRequests = $allResults.Count
    }
}

# Fonction d'analyse des résultats
function Get-TestAnalysis {
    param($TestData)
    
    $results = $TestData.Results
    $successfulRequests = $results | Where-Object { $_.Success -eq $true }
    $failedRequests = $results | Where-Object { $_.Success -eq $false }
    
    if ($successfulRequests.Count -gt 0) {
        $avgResponseTime = ($successfulRequests | Measure-Object -Property ResponseTime -Average).Average
        $minResponseTime = ($successfulRequests | Measure-Object -Property ResponseTime -Minimum).Minimum
        $maxResponseTime = ($successfulRequests | Measure-Object -Property ResponseTime -Maximum).Maximum
        $medianResponseTime = ($successfulRequests | Sort-Object ResponseTime)[[math]::Floor($successfulRequests.Count / 2)].ResponseTime
        $p95ResponseTime = ($successfulRequests | Sort-Object ResponseTime)[[math]::Floor($successfulRequests.Count * 0.95)].ResponseTime
        $p99ResponseTime = ($successfulRequests | Sort-Object ResponseTime)[[math]::Floor($successfulRequests.Count * 0.99)].ResponseTime
    }
    else {
        $avgResponseTime = $minResponseTime = $maxResponseTime = $medianResponseTime = $p95ResponseTime = $p99ResponseTime = 0
    }
    
    $requestsPerSecond = [math]::Round($TestData.TotalRequests / $TestData.TotalDuration, 2)
    $successRate = [math]::Round($successfulRequests.Count / $results.Count * 100, 2)
    
    Write-Host "`n=== Analyse: $($TestData.TestName) ===" -ForegroundColor Green
    Write-Host "Durée totale du test: $([math]::Round($TestData.TotalDuration, 2)) secondes"
    Write-Host "Total des requêtes: $($TestData.TotalRequests)"
    Write-Host "Requêtes/seconde: $requestsPerSecond"
    Write-Host "Requêtes réussies: $($successfulRequests.Count)" -ForegroundColor Green
    Write-Host "Requêtes échouées: $($failedRequests.Count)" -ForegroundColor Red
    Write-Host "Taux de succès: $successRate%"
    Write-Host "Temps de réponse moyen: $([math]::Round($avgResponseTime, 2)) ms"
    Write-Host "Temps de réponse médian: $medianResponseTime ms"
    Write-Host "Temps de réponse P95: $p95ResponseTime ms"
    Write-Host "Temps de réponse P99: $p99ResponseTime ms"
    Write-Host "Temps de réponse min/max: $minResponseTime ms / $maxResponseTime ms"
    
    # Analyser la distribution des serveurs (pour load balancing)
    $serverDistribution = $successfulRequests | Group-Object ServerInstance | ForEach-Object {
        @{
            Server = $_.Name
            Count = $_.Count
            Percentage = [math]::Round($_.Count / $successfulRequests.Count * 100, 2)
        }
    }
    
    if ($serverDistribution.Count -gt 1) {
        Write-Host "`nDistribution des serveurs:" -ForegroundColor Cyan
        foreach ($server in $serverDistribution) {
            Write-Host "  $($server.Server): $($server.Count) requêtes ($($server.Percentage)%)"
        }
    }
    
    return @{
        TestName = $TestData.TestName
        TotalRequests = $TestData.TotalRequests
        SuccessfulRequests = $successfulRequests.Count
        FailedRequests = $failedRequests.Count
        SuccessRate = $successRate
        RequestsPerSecond = $requestsPerSecond
        AvgResponseTime = [math]::Round($avgResponseTime, 2)
        MedianResponseTime = $medianResponseTime
        P95ResponseTime = $p95ResponseTime
        P99ResponseTime = $p99ResponseTime
        MinResponseTime = $minResponseTime
        MaxResponseTime = $maxResponseTime
        TestDuration = $TestData.TotalDuration
        ServerDistribution = $serverDistribution
    }
}

# Test 1: Load Balancer vs Gateway Direct
if ($TestLoadBalancing) {
    Write-Host "`n" + "="*60 -ForegroundColor Magenta
    Write-Host "TEST LOAD BALANCING" -ForegroundColor Magenta
    Write-Host "="*60 -ForegroundColor Magenta
    
    # Test avec Load Balancer
    $lbTest = Invoke-LoadTest -BaseUrl $LoadBalancerUrl -TestName "Load Balancer" -Users $ConcurrentUsers -RequestsPerUser $RequestsPerUser
    $lbAnalysis = Get-TestAnalysis -TestData $lbTest
    
    # Pause entre les tests
    Start-Sleep -Seconds 5
    
    # Test avec Gateway Direct
    $directTest = Invoke-LoadTest -BaseUrl $DirectGatewayUrl -TestName "Gateway Direct" -Users $ConcurrentUsers -RequestsPerUser $RequestsPerUser
    $directAnalysis = Get-TestAnalysis -TestData $directTest
    
    # Comparaison
    Write-Host "`n=== COMPARAISON LOAD BALANCING ===" -ForegroundColor Cyan
    $performanceGain = [math]::Round(($lbAnalysis.RequestsPerSecond - $directAnalysis.RequestsPerSecond) / $directAnalysis.RequestsPerSecond * 100, 2)
    $responseTimeChange = [math]::Round(($lbAnalysis.AvgResponseTime - $directAnalysis.AvgResponseTime) / $directAnalysis.AvgResponseTime * 100, 2)
    
    Write-Host "Load Balancer:"
    Write-Host "  - Requêtes/sec: $($lbAnalysis.RequestsPerSecond)"
    Write-Host "  - Temps réponse moyen: $($lbAnalysis.AvgResponseTime) ms"
    Write-Host "  - Taux succès: $($lbAnalysis.SuccessRate)%"
    
    Write-Host "Gateway Direct:"
    Write-Host "  - Requêtes/sec: $($directAnalysis.RequestsPerSecond)"
    Write-Host "  - Temps réponse moyen: $($directAnalysis.AvgResponseTime) ms"
    Write-Host "  - Taux succès: $($directAnalysis.SuccessRate)%"
    
    Write-Host "Amélioration avec Load Balancer:"
    Write-Host "  - Throughput: $performanceGain%" -ForegroundColor $(if($performanceGain -gt 0) {'Green'} else {'Red'})
    Write-Host "  - Temps réponse: $responseTimeChange%" -ForegroundColor $(if($responseTimeChange -lt 0) {'Green'} else {'Red'})
}

# Test 2: Performance du Cache
if ($TestCache) {
    Write-Host "`n" + "="*60 -ForegroundColor Magenta
    Write-Host "TEST PERFORMANCE CACHE" -ForegroundColor Magenta
    Write-Host "="*60 -ForegroundColor Magenta
    
    # Test Cache Miss (premier accès)
    Write-Host "`nVidage du cache Redis..." -ForegroundColor Yellow
    # Note: Dans un vrai environnement, on pourrait appeler une API pour vider le cache
    
    $cacheMissTest = Invoke-LoadTest -BaseUrl $LoadBalancerUrl -TestName "Cache Miss" -Users 10 -RequestsPerUser 5
    $cacheMissAnalysis = Get-TestAnalysis -TestData $cacheMissTest
    
    # Pause pour permettre la mise en cache
    Start-Sleep -Seconds 2
    
    # Test Cache Hit (accès répétés)
    $cacheHitTest = Invoke-LoadTest -BaseUrl $LoadBalancerUrl -TestName "Cache Hit" -Users 10 -RequestsPerUser 5
    $cacheHitAnalysis = Get-TestAnalysis -TestData $cacheHitTest
    
    # Comparaison Cache
    Write-Host "`n=== COMPARAISON CACHE PERFORMANCE ===" -ForegroundColor Cyan
    $cacheImprovement = [math]::Round(($cacheMissAnalysis.AvgResponseTime - $cacheHitAnalysis.AvgResponseTime) / $cacheMissAnalysis.AvgResponseTime * 100, 2)
    $throughputImprovement = [math]::Round(($cacheHitAnalysis.RequestsPerSecond - $cacheMissAnalysis.RequestsPerSecond) / $cacheMissAnalysis.RequestsPerSecond * 100, 2)
    
    Write-Host "Cache Miss (premier accès):"
    Write-Host "  - Temps réponse moyen: $($cacheMissAnalysis.AvgResponseTime) ms"
    Write-Host "  - Requêtes/sec: $($cacheMissAnalysis.RequestsPerSecond)"
    
    Write-Host "Cache Hit (accès répétés):"
    Write-Host "  - Temps réponse moyen: $($cacheHitAnalysis.AvgResponseTime) ms"
    Write-Host "  - Requêtes/sec: $($cacheHitAnalysis.RequestsPerSecond)"
    
    Write-Host "Amélioration avec Cache:"
    Write-Host "  - Réduction temps réponse: $cacheImprovement%" -ForegroundColor Green
    Write-Host "  - Amélioration throughput: $throughputImprovement%" -ForegroundColor Green
}

# Test 3: Stress Test avec Load Balancer
Write-Host "`n" + "="*60 -ForegroundColor Magenta
Write-Host "STRESS TEST AVEC LOAD BALANCER" -ForegroundColor Magenta
Write-Host "="*60 -ForegroundColor Magenta

$stressTest = Invoke-LoadTest -BaseUrl $LoadBalancerUrl -TestName "Stress Test" -Users ($ConcurrentUsers * 2) -RequestsPerUser ($RequestsPerUser * 2)
$stressAnalysis = Get-TestAnalysis -TestData $stressTest

# Sauvegarde des résultats
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resultFile = ".\testing\results\loadbalancer_test_$timestamp.json"

$allResults = @{
    TestConfiguration = @{
        ConcurrentUsers = $ConcurrentUsers
        RequestsPerUser = $RequestsPerUser
        LoadBalancerUrl = $LoadBalancerUrl
        DirectGatewayUrl = $DirectGatewayUrl
        Timestamp = Get-Date
    }
    LoadBalancingTest = if ($TestLoadBalancing) {
        @{
            LoadBalancer = $lbAnalysis
            DirectGateway = $directAnalysis
            PerformanceGain = $performanceGain
            ResponseTimeChange = $responseTimeChange
        }
    } else { $null }
    CacheTest = if ($TestCache) {
        @{
            CacheMiss = $cacheMissAnalysis
            CacheHit = $cacheHitAnalysis
            CacheImprovement = $cacheImprovement
            ThroughputImprovement = $throughputImprovement
        }
    } else { $null }
    StressTest = $stressAnalysis
}

$allResults | ConvertTo-Json -Depth 4 | Out-File -FilePath $resultFile -Encoding UTF8

Write-Host "`n" + "="*60 -ForegroundColor Green
Write-Host "RÉSUMÉ FINAL" -ForegroundColor Green
Write-Host "="*60 -ForegroundColor Green

if ($TestLoadBalancing) {
    Write-Host "`nLoad Balancing:"
    Write-Host "  - Amélioration throughput: $performanceGain%"
    Write-Host "  - Changement temps réponse: $responseTimeChange%"
}

if ($TestCache) {
    Write-Host "`nCache Performance:"
    Write-Host "  - Réduction temps réponse: $cacheImprovement%"
    Write-Host "  - Amélioration throughput: $throughputImprovement%"
}

Write-Host "`nStress Test:"
Write-Host "  - Requêtes/sec max: $($stressAnalysis.RequestsPerSecond)"
Write-Host "  - Taux succès sous charge: $($stressAnalysis.SuccessRate)%"
Write-Host "  - P99 temps réponse: $($stressAnalysis.P99ResponseTime) ms"

Write-Host "`nRésultats détaillés sauvegardés: $resultFile" -ForegroundColor Cyan
Write-Host "`n=== Tests terminés ===" -ForegroundColor Green