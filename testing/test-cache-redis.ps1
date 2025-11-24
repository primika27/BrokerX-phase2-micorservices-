# Script de test du cache Redis pour MarketDataService
# Utilise les endpoints de test intégrés

param(
    [string]$BaseUrl = "http://localhost:8086"
)

$ErrorActionPreference = "Continue"

Write-Host "=== Test du Cache Redis - MarketDataService ===" -ForegroundColor Green
Write-Host "URL de base: $BaseUrl"
Write-Host "Timestamp: $(Get-Date)" -ForegroundColor Gray

# Fonction pour faire une requête et afficher le résultat
function Test-Endpoint {
    param(
        [string]$Url,
        [string]$TestName,
        [string]$Method = "GET"
    )
    
    Write-Host "`n--- $TestName ---" -ForegroundColor Yellow
    
    try {
        $response = if ($Method -eq "POST") {
            Invoke-RestMethod -Uri $Url -Method POST -ContentType "application/json"
        } else {
            Invoke-RestMethod -Uri $Url -Method GET
        }
        
        Write-Host "✅ Succès" -ForegroundColor Green
        
        # Afficher les résultats importants
        if ($response.redis_connected -ne $null) {
            $status = if ($response.redis_connected) { "✅ Connecté" } else { "❌ Déconnecté" }
            Write-Host "   Redis Status: $status"
        }
        
        if ($response.cache_miss_time_ms -ne $null -and $response.cache_hit_time_ms -ne $null) {
            Write-Host "   Cache Miss: $($response.cache_miss_time_ms) ms"
            Write-Host "   Cache Hit: $($response.cache_hit_time_ms) ms"
            Write-Host "   Amélioration: $($response.performance_improvement_percent)%" -ForegroundColor Cyan
        }
        
        if ($response.total_keys -ne $null) {
            Write-Host "   Nombre de clés en cache: $($response.total_keys)"
        }
        
        if ($response.cache_cleared -ne $null) {
            $status = if ($response.cache_cleared) { "✅ Vidé" } else { "❌ Erreur" }
            Write-Host "   Cache: $status"
            if ($response.keys_removed -ne $null) {
                Write-Host "   Clés supprimées: $($response.keys_removed)"
            }
        }
        
        # Afficher les erreurs s'il y en a
        if ($response.error) {
            Write-Host "   Erreur: $($response.error)" -ForegroundColor Red
        }
        
        return $response
    }
    catch {
        Write-Host "❌ Erreur: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

# Attendre que le service soit démarré
Write-Host "`nVérification que le service est démarré..." -ForegroundColor Cyan
$maxRetries = 10
$retryCount = 0

do {
    try {
        $healthCheck = Invoke-RestMethod -Uri "$BaseUrl/api/market-data/quotes" -Method GET -TimeoutSec 5
        Write-Host "✅ Service MarketDataService est disponible" -ForegroundColor Green
        break
    }
    catch {
        $retryCount++
        Write-Host "⏳ Attente du démarrage du service... ($retryCount/$maxRetries)" -ForegroundColor Yellow
        Start-Sleep -Seconds 3
    }
} while ($retryCount -lt $maxRetries)

if ($retryCount -ge $maxRetries) {
    Write-Host "❌ Le service n'est pas disponible après $maxRetries tentatives" -ForegroundColor Red
    Write-Host "Vérifiez que le conteneur market-data-service est démarré :" -ForegroundColor Yellow
    Write-Host "  docker-compose ps market-data-service" -ForegroundColor Gray
    exit 1
}

# Test 1: Vérification de la connexion Redis
$connectionTest = Test-Endpoint -Url "$BaseUrl/api/market-data/test/cache/status" -TestName "Test de connexion Redis"

if ($connectionTest -and -not $connectionTest.redis_connected) {
    Write-Host "`n❌ Redis n'est pas connecté. Vérifiez que Redis est démarré :" -ForegroundColor Red
    Write-Host "  docker-compose ps redis" -ForegroundColor Gray
    exit 1
}

# Test 2: Test de performance du cache
Write-Host "`n=== Test de Performance du Cache ===" -ForegroundColor Magenta
$performanceTest = Test-Endpoint -Url "$BaseUrl/api/market-data/test/cache/performance" -TestName "Test de performance Cache Miss vs Cache Hit"

# Test 3: Lister les clés du cache
$keysTest = Test-Endpoint -Url "$BaseUrl/api/market-data/test/cache/keys" -TestName "Liste des clés en cache"

# Test 4: Test de requêtes répétées pour voir le cache en action
Write-Host "`n=== Test de Requêtes Répétées ===" -ForegroundColor Magenta
Write-Host "Test avec plusieurs requêtes identiques pour observer le cache..."

$times = @()
for ($i = 1; $i -le 5; $i++) {
    Write-Host "`nRequête $i :" -ForegroundColor Cyan
    
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/api/market-data/quotes?symbols=AAPL,GOOGL,MSFT" -Method GET
        $stopwatch.Stop()
        $responseTime = $stopwatch.ElapsedMilliseconds
        $times += $responseTime
        
        Write-Host "  ✅ Succès - Temps: $responseTime ms - Symboles: $($response.Count)"
        
        # Petite pause entre les requêtes
        Start-Sleep -Milliseconds 500
    }
    catch {
        $stopwatch.Stop()
        Write-Host "  ❌ Erreur: $($_.Exception.Message)" -ForegroundColor Red
    }
}

if ($times.Count -gt 1) {
    Write-Host "`nAnalyse des temps de réponse :" -ForegroundColor Cyan
    Write-Host "  Première requête (cache miss probable): $($times[0]) ms"
    Write-Host "  Dernière requête (cache hit probable): $($times[-1]) ms"
    
    $improvement = [math]::Round(($times[0] - $times[-1]) / $times[0] * 100, 2)
    if ($improvement -gt 0) {
        Write-Host "  Amélioration grâce au cache: $improvement%" -ForegroundColor Green
    } else {
        Write-Host "  Pas d'amélioration significative détectée" -ForegroundColor Yellow
    }
    
    $avgTime = [math]::Round(($times | Measure-Object -Average).Average, 2)
    Write-Host "  Temps moyen: $avgTime ms"
}

# Test 5: Vider le cache
Write-Host "`n=== Test de Vidage du Cache ===" -ForegroundColor Magenta
$clearTest = Test-Endpoint -Url "$BaseUrl/api/market-data/test/cache/clear" -TestName "Vidage du cache" -Method "POST"

# Test 6: Vérifier que le cache est vraiment vidé
$keysTestAfterClear = Test-Endpoint -Url "$BaseUrl/api/market-data/test/cache/keys" -TestName "Vérification après vidage"

# Résumé final
Write-Host "`n" + "="*60 -ForegroundColor Green
Write-Host "RÉSUMÉ DES TESTS DE CACHE" -ForegroundColor Green
Write-Host "="*60 -ForegroundColor Green

$redisStatus = if ($connectionTest -and $connectionTest.redis_connected) { "✅ Opérationnel" } else { "❌ Problème" }
Write-Host "Redis Connection: $redisStatus"

if ($performanceTest -and $performanceTest.performance_improvement_percent -ne $null) {
    $perfStatus = if ($performanceTest.performance_improvement_percent -gt 0) { "✅ Efficace" } else { "⚠️ À vérifier" }
    Write-Host "Cache Performance: $perfStatus ($($performanceTest.performance_improvement_percent)%)"
}

if ($clearTest -and $clearTest.cache_cleared -ne $null) {
    $clearStatus = if ($clearTest.cache_cleared) { "✅ Fonctionnel" } else { "❌ Problème" }
    Write-Host "Cache Clear: $clearStatus"
}

if ($times.Count -gt 1 -and $improvement -gt 0) {
    Write-Host "Requêtes Répétées: ✅ Cache efficace ($improvement% d'amélioration)"
} else {
    Write-Host "Requêtes Répétées: ⚠️ Amélioration non significative"
}

Write-Host "`nTests terminés à $(Get-Date)" -ForegroundColor Gray

# Conseils de dépannage
if ($connectionTest -and -not $connectionTest.redis_connected) {
    Write-Host "`n=== DÉPANNAGE ===" -ForegroundColor Red
    Write-Host "Redis n'est pas connecté. Vérifiez :"
    Write-Host "1. docker-compose ps redis"
    Write-Host "2. docker logs brokerx-phase2micorservices-redis-1"
    Write-Host "3. Les variables d'environnement SPRING_REDIS_* dans docker-compose.yml"
}

if ($performanceTest -and $performanceTest.performance_improvement_percent -le 0) {
    Write-Host "`n=== NOTE ===" -ForegroundColor Yellow
    Write-Host "Amélioration de performance faible ou négative."
    Write-Host "Cela peut être normal pour de petites données ou des tests locaux."
    Write-Host "Les bénéfices du cache sont plus visibles sous charge ou avec des calculs complexes."
}