#!/usr/bin/env pwsh
# Simple script pour lancer les tests k6 avec Grafana
Write-Host "🎯 Test de load balancing BrokerX" -ForegroundColor Cyan

# Télécharger k6 si nécessaire
if (-not (Test-Path ".\k6.exe")) {
    Write-Host "📥 Téléchargement de k6..."
    Invoke-WebRequest -Uri "https://github.com/grafana/k6/releases/download/v0.52.0/k6-v0.52.0-windows-amd64.zip" -OutFile "k6.zip"
    Expand-Archive -Path "k6.zip" -DestinationPath "." -Force
    Remove-Item "k6.zip"
    Write-Host "✅ k6 installé"
}

# Démarrer monitoring
Write-Host "🚀 Démarrage monitoring..."
docker-compose up -d prometheus grafana

Start-Sleep 5

# Tests pour différentes configurations
$instances = @(1, 2, 3, 4)

foreach ($count in $instances) {
    Write-Host ""
    Write-Host "🧪 Test avec $count instance(s)..." -ForegroundColor Yellow
    
    $env:INSTANCES = $count
    
    # Lancer k6 (version simple sans Prometheus output pour l'instant)
    .\k6.exe run --tag "instance_count=$count" .\testing\load-tests\brokerx-loadbalancing-test.js
    
    Write-Host "✅ Test $count terminé"
    
    # Pause entre tests
    if ($count -ne 4) {
        Write-Host "⏱️ Pause 30s..."
        Start-Sleep 30
    }
}

Write-Host ""
Write-Host "🎉 Tous les tests terminés!"
Write-Host "📊 Grafana: http://localhost:3000"
Write-Host "🔍 Prometheus: http://localhost:9090"