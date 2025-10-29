#!/usr/bin/env pwsh
# ============================================================================
# SCRIPT AUTOMATISÉ POUR TESTS DE LOAD BALANCING - BROKERX
# ============================================================================
# Ce script lance des tests k6 pour N=1,2,3,4 instances et envoie 
# les métriques à Grafana/Prometheus pour visualisation comparative
# ============================================================================

param(
    [string]$K6Path = ".\k6.exe",
    [string]$TestFile = ".\testing\load-tests\brokerx-loadbalancing-test.js",
    [int[]]$InstanceCounts = @(1, 2, 3, 4),
    [int]$DelayBetweenTests = 30  # secondes entre chaque test
)

Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║             BROKERX LOAD BALANCING AUTOMATED TESTS            ║" -ForegroundColor Cyan  
Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Vérification des prérequis
if (-not (Test-Path $K6Path)) {
    Write-Host "❌ k6 non trouvé à: $K6Path" -ForegroundColor Red
    Write-Host "💡 Téléchargement de k6..."
    try {
        Invoke-WebRequest -Uri "https://github.com/grafana/k6/releases/download/v0.52.0/k6-v0.52.0-windows-amd64.zip" -OutFile "k6.zip"
        Expand-Archive -Path "k6.zip" -DestinationPath "." -Force
        Remove-Item "k6.zip"
        Write-Host "✅ k6 installé avec succès" -ForegroundColor Green
    }
    catch {
        Write-Host "❌ Échec du téléchargement de k6: $_" -ForegroundColor Red
        exit 1
    }
}

if (-not (Test-Path $TestFile)) {
    Write-Host "❌ Fichier de test non trouvé: $TestFile" -ForegroundColor Red
    exit 1
}

# Fonction pour attendre que les services soient prêts
function Wait-ForServices {
    param([int]$TimeoutSeconds = 120)
    
    Write-Host "⏳ Attente que les services soient prêts..." -ForegroundColor Yellow
    $elapsed = 0
    
    do {
        Start-Sleep 5
        $elapsed += 5
        
        try {
            $health = Invoke-WebRequest -Uri "http://localhost/health" -TimeoutSec 5 -UseBasicParsing
            if ($health.StatusCode -eq 200) {
                Write-Host "✅ Services prêts!" -ForegroundColor Green
                return $true
            }
        }
        catch {
            Write-Host "⏳ Attente des services... ($elapsed/$TimeoutSeconds s)" -ForegroundColor Yellow
        }
    } while ($elapsed -lt $TimeoutSeconds)
    
    Write-Host "⚠️ Timeout - les services ne répondent pas après $TimeoutSeconds secondes" -ForegroundColor Red
    return $false
}

# Fonction pour démarrer les services avec monitoring
function Start-Services {
    param([string]$DockerComposeFile)

    Write-Host "🚀 Démarrage des services + monitoring avec $DockerComposeFile..." -ForegroundColor Yellow
    
    # Arrêter d'abord tous les services
    docker-compose -f $DockerComposeFile down 2>$null
    
    # Démarrer les services
    docker-compose -f $DockerComposeFile up -d --build
    
    # Attendre que tout soit prêt
    return Wait-ForServices
}

# Main script
Write-Host "📋 Tests planifiés pour: $($InstanceCounts -join ', ') instances" -ForegroundColor Cyan
Write-Host "⏱️ Délai entre tests: $DelayBetweenTests secondes" -ForegroundColor Cyan
Write-Host ""

$testResults = @()

foreach ($instanceCount in $InstanceCounts) {
    Write-Host ""
    Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Blue
    Write-Host "║                  TEST AVEC $instanceCount INSTANCE(S)                       ║" -ForegroundColor Blue
    Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Blue
    Write-Host ""
    
    # 1. Sélectionner le fichier docker-compose approprié
    $dockerComposeFile = "docker-compose.loadbalanced.yml"
    if ($instanceCount -eq 1) {
        $dockerComposeFile = "docker-compose.1instance.yml"  
    } elseif ($instanceCount -eq 3) {
        $dockerComposeFile = "docker-compose.3instances.yml"
    } elseif ($instanceCount -eq 4) {
        $dockerComposeFile = "docker-compose.4instances.yml"
    }

    if (-not (Test-Path $dockerComposeFile)) {
        Write-Host "❌ Fichier docker-compose non trouvé: $dockerComposeFile" -ForegroundColor Red
        continue
    }
    
    # 2. Redémarrer les services
    if (-not (Start-Services -DockerComposeFile $dockerComposeFile)) {
        Write-Host "❌ Impossible de démarrer les services pour $instanceCount instance(s)" -ForegroundColor Red
        continue
    }
    
    # 3. Lancer le test k6 avec output Prometheus
    Write-Host "🎯 Lancement du test k6 pour $instanceCount instance(s)..." -ForegroundColor Green
    
    $env:INSTANCES = $instanceCount
    $testStart = Get-Date
    
    try {
        # Lancer k6 avec sortie Prometheus et JSON
        $k6Args = @(
            "run",
            "--out", "experimental-prometheus-rw",
            "--tag", "instance_count=$instanceCount",
            "--tag", "test_type=load_balancing",
            $TestFile
        )
        
        & $K6Path $k6Args
        
        $testEnd = Get-Date
        $duration = ($testEnd - $testStart).TotalSeconds
        
        Write-Host "✅ Test terminé pour $instanceCount instance(s) en $([math]::Round($duration,1))s" -ForegroundColor Green
        
        $testResults += @{
            InstanceCount = $instanceCount
            StartTime = $testStart
            EndTime = $testEnd
            DurationSeconds = $duration
            Status = "Success"
        }
        
    }
    catch {
        Write-Host "❌ Échec du test pour $instanceCount instance(s): $_" -ForegroundColor Red
        $testResults += @{
            InstanceCount = $instanceCount
            StartTime = $testStart
            EndTime = Get-Date
            DurationSeconds = 0
            Status = "Failed"
            Error = $_.Exception.Message
        }
    }
    
    # 4. Délai avant le test suivant
    if ($instanceCount -ne $InstanceCounts[-1]) {
        Write-Host ""
        Write-Host "⏱️ Pause de $DelayBetweenTests secondes avant le test suivant..." -ForegroundColor Yellow
        Start-Sleep $DelayBetweenTests
    }
}

# Résumé final
Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                   RÉSUMÉ DES TESTS                       ║" -ForegroundColor Green
Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

foreach ($result in $testResults) {
    $status = if ($result.Status -eq "Success") { "✅" } else { "❌" }
    $duration = if ($result.Status -eq "Success") { "$([math]::Round($result.DurationSeconds,1))s" } else { "Failed" }
    Write-Host "$status $($result.InstanceCount) instance(s): $duration"
}

Write-Host ""
Write-Host "📊 Les métriques sont disponibles dans Grafana: http://localhost:3000" -ForegroundColor Cyan
Write-Host "🔍 Prometheus: http://localhost:9090" -ForegroundColor Cyan
Write-Host "💡 Utilisez les tags instance_count et test_type pour filtrer les donnees" -ForegroundColor Cyan
Write-Host ""
Write-Host "🎯 Tests terminés! Analysez les résultats dans Grafana." -ForegroundColor Green
