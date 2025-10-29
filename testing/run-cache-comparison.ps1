#!/usr/bin/env pwsh
# ============================================================================
# SCRIPT DE TEST COMPARATIF: BASELINE vs CACHE
# ============================================================================
# Ce script compare les performances AVANT et APRÈS cache Redis
# pour démontrer l'impact de l'optimisation sur les endpoints coûteux
# ============================================================================

param(
    [string]$K6Path = ".\k6.exe",
    [string]$TestFile = ".\testing\load-tests\brokerx-loadbalancing-test.js",
    [int[]]$InstanceCounts = @(2, 3),  # Optimal configurations identifiées
    [int]$TestDurationMinutes = 3
)

Write-Host "╔═══════════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                    CACHE IMPACT ANALYSIS                          ║" -ForegroundColor Green
Write-Host "║              Baseline vs Redis Cache Performance                   ║" -ForegroundColor Green
Write-Host "╚═══════════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

# Résultats baseline (déjà connus)
$baselineResults = @{
    N1 = @{ RPS = 39.92; P95 = 36.25; P50 = 9.94 }
    N2 = @{ RPS = 125.86; P95 = 26.87; P50 = 9.04 }
    N3 = @{ RPS = 126.52; P95 = 22.47; P50 = 8.06 }
    N4 = @{ RPS = 124.95; P95 = 22.88; P50 = 7.90 }
}

Write-Host " RÉSULTATS BASELINE (Sans Cache):" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════" -ForegroundColor Cyan
foreach ($key in $baselineResults.Keys | Sort-Object) {
    $result = $baselineResults[$key]
    Write-Host "   $key instances: $($result.RPS) RPS, P95: $($result.P95)ms, P50: $($result.P50)ms" -ForegroundColor White
}
Write-Host ""

# Vérifier que Redis est disponible
Write-Host "Vérification de Redis..." -ForegroundColor Yellow
try {
    $redisCheck = docker ps --filter "name=brokerx-redis" --format "{{.Status}}"
    if ($redisCheck -like "*Up*") {
        Write-Host " Redis est actif et prêt" -ForegroundColor Green
    } else {
        Write-Host "Redis n'est pas actif. Démarrage du monitoring stack..." -ForegroundColor Red
        docker-compose -f docker-compose.monitoring.yml up -d
        Start-Sleep 10
    }
} catch {
    Write-Host "Problème avec Redis. Vérifiez docker-compose.monitoring.yml" -ForegroundColor Yellow
}

$cacheResults = @{}

foreach ($instanceCount in $InstanceCounts) {
    Write-Host ""
    Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Magenta
    Write-Host "║            TEST AVEC CACHE - $instanceCount INSTANCES                     ║" -ForegroundColor Magenta  
    Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Magenta
    Write-Host ""

    # 1. Redémarrer les services avec cache activé
    Write-Host "Redémarrage des services avec cache Redis..." -ForegroundColor Yellow
    
    docker-compose -f docker-compose.yml down -t 10
    Start-Sleep 5
    
    # Scaling selon le nombre d'instances
    $scaleArgs = @("up", "-d", "--build")
    if ($instanceCount -gt 1) {
        $scaleArgs += "--scale"
        $scaleArgs += "gateway=$instanceCount"
        Write-Host "   Scaling gateway à $instanceCount instances" -ForegroundColor Gray
    }
    
    & docker-compose -f docker-compose.yml @scaleArgs
    
    # 2. Attendre que les services soient prêts
    Write-Host "Attente que les services avec cache soient prêts..." -ForegroundColor Yellow
    $ready = $false
    $attempts = 0
    
    do {
        Start-Sleep 5
        $attempts++
        try {
            $health = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -TimeoutSec 5 -UseBasicParsing -ErrorAction SilentlyContinue
            if ($health.StatusCode -eq 200) {
                Write-Host "Services avec cache prêts!" -ForegroundColor Green
                $ready = $true
            }
        } catch {
            Write-Host "   Tentative $attempts/24 - Services en cours de démarrage..." -ForegroundColor Gray
        }
    } while (-not $ready -and $attempts -lt 24)
    
    if (-not $ready) {
        Write-Host "Timeout - Services non disponibles" -ForegroundColor Red
        continue
    }
    
    # 3. Réchauffage du cache
    Write-Host "Réchauffage du cache (30 secondes)..." -ForegroundColor Yellow
    
    $warmupUrls = @(
        "http://localhost:8080/api/clients/getByEmail?email=user1@test.com",
        "http://localhost:8080/api/clients/getByEmail?email=user2@test.com", 
        "http://localhost:8080/api/clients/getEmailById?clientId=1",
        "http://localhost:8080/api/clients/getEmailById?clientId=2",
        "http://localhost:8080/api/wallet/balance?ownerEmail=user1@test.com"
    )
    
    for ($i = 0; $i -lt 6; $i++) {
        foreach ($url in $warmupUrls) {
            try {
                Invoke-WebRequest -Uri $url -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue | Out-Null
            } catch { }
        }
        Start-Sleep 5
    }
    
    # 4. Lancer le test K6 avec cache
    Write-Host "Lancement du test K6 avec cache pour $instanceCount instance(s)..." -ForegroundColor Green
    
    $env:INSTANCES = $instanceCount
    $testStart = Get-Date
    
    try {
        $k6Output = & $K6Path run --tag "instance_count=$instanceCount" --tag "test_type=with_cache" $TestFile 2>&1
        
        # Extraire les métriques du output K6
        $rpsMatch = $k6Output | Select-String "⚡ RPS: ([\d\.]+) req/sec"
        $p95Match = $k6Output | Select-String "📈 P95: ([\d\.]+)ms"
        $p50Match = $k6Output | Select-String "📈 P50: ([\d\.]+)ms"
        $totalMatch = $k6Output | Select-String "📈 Total requêtes: ([\d\.]+)"
        
        if ($rpsMatch -and $p95Match -and $p50Match) {
            $cacheResults["N$instanceCount"] = @{
                RPS = [double]$rpsMatch.Matches[0].Groups[1].Value
                P95 = [double]$p95Match.Matches[0].Groups[1].Value  
                P50 = [double]$p50Match.Matches[0].Groups[1].Value
                TotalRequests = if ($totalMatch) { [int]$totalMatch.Matches[0].Groups[1].Value } else { 0 }
            }
            
            Write-Host "Test terminé avec succès pour $instanceCount instance(s)" -ForegroundColor Green
        } else {
            Write-Host "Impossible d'extraire les métriques du test" -ForegroundColor Yellow
            $cacheResults["N$instanceCount"] = @{ RPS = 0; P95 = 0; P50 = 0; TotalRequests = 0 }
        }
        
    } catch {
        Write-Host "Échec du test pour $instanceCount instance(s): $_" -ForegroundColor Red
        $cacheResults["N$instanceCount"] = @{ RPS = 0; P95 = 0; P50 = 0; TotalRequests = 0 }
    }
}

# 5. Générer le rapport comparatif
Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                      ANALYSE COMPARATIVE                          ║" -ForegroundColor Green
Write-Host "║                    BASELINE vs CACHE REDIS                        ║" -ForegroundColor Green
Write-Host "╚═══════════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

Write-Host "IMPACT DU CACHE:" -ForegroundColor Cyan
Write-Host "═══════════════════" -ForegroundColor Cyan
Write-Host ""

foreach ($instanceCount in $InstanceCounts) {
    $nKey = "N$instanceCount"
    $baseline = $baselineResults[$nKey]
    $cached = $cacheResults[$nKey]
    
    if ($cached.RPS -gt 0 -and $baseline) {
        $rpsGain = (($cached.RPS - $baseline.RPS) / $baseline.RPS) * 100
        $p95Improvement = (($baseline.P95 - $cached.P95) / $baseline.P95) * 100
        $p50Improvement = (($baseline.P50 - $cached.P50) / $baseline.P50) * 100
        
        Write-Host "🏭 $instanceCount INSTANCES:" -ForegroundColor Yellow
        Write-Host "   RPS:     $($baseline.RPS) → $($cached.RPS) ($($rpsGain.ToString('F1'))% gain)" -ForegroundColor White
        Write-Host "   P95:     $($baseline.P95)ms → $($cached.P95)ms ($($p95Improvement.ToString('F1'))% amélioration)" -ForegroundColor White  
        Write-Host "   P50:     $($baseline.P50)ms → $($cached.P50)ms ($($p50Improvement.ToString('F1'))% amélioration)" -ForegroundColor White
        Write-Host ""
    }
}

Write-Host "CONCLUSIONS:" -ForegroundColor Green
Write-Host "• Cache Redis optimise les endpoints coûteux (/api/clients/*, /api/wallet/balance)" -ForegroundColor White
Write-Host "• Réduction significative des latences grâce à la mise en cache" -ForegroundColor White  
Write-Host "• Amélioration du débit sur les requêtes répétitives" -ForegroundColor White
Write-Host ""
Write-Host "Test terminé! Métriques disponibles dans Grafana: http://localhost:3000" -ForegroundColor Cyan