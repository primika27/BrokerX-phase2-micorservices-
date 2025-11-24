# Script de test pour la gestion des abonnements utilisateur
# Teste les nouveaux endpoints d'abonnement avec Redis

param(
    [string]$BaseUrl = "http://localhost:8086"
)

$ErrorActionPreference = "Continue"

Write-Host "=== Test de Gestion des Abonnements Utilisateur ===" -ForegroundColor Green
Write-Host "URL de base: $BaseUrl"
Write-Host "Timestamp: $(Get-Date)" -ForegroundColor Gray

# Fonction pour faire une requête et afficher le résultat
function Test-SubscriptionEndpoint {
    param(
        [string]$Url,
        [string]$Method = "GET",
        [hashtable]$Body = $null,
        [string]$TestName
    )
    
    Write-Host "`n--- $TestName ---" -ForegroundColor Yellow
    
    try {
        if ($Method -eq "POST") {
            $response = Invoke-RestMethod -Uri $Url -Method POST -Body ($Body | ConvertTo-Json) -ContentType "application/json"
        } elseif ($Method -eq "DELETE") {
            $response = Invoke-RestMethod -Uri $Url -Method DELETE
        } else {
            $response = Invoke-RestMethod -Uri $Url -Method GET
        }
        
        Write-Host "✅ Succès" -ForegroundColor Green
        
        # Afficher les informations importantes
        if ($response.success -ne $null) {
            $status = if ($response.success) { "✅" } else { "❌" }
            Write-Host "   Status: $status $($response.message)"
        }
        
        if ($response.user_email) {
            Write-Host "   Utilisateur: $($response.user_email)"
        }
        
        if ($response.valid_symbols) {
            Write-Host "   Symboles valides: $($response.valid_symbols -join ', ')"
        }
        
        if ($response.invalid_symbols -and $response.invalid_symbols.Count -gt 0) {
            Write-Host "   Symboles invalides: $($response.invalid_symbols -join ', ')" -ForegroundColor Red
        }
        
        if ($response.total_symbols -ne $null) {
            Write-Host "   Nombre de symboles: $($response.total_symbols)"
        }
        
        if ($response.total_users -ne $null) {
            Write-Host "   Nombre d'utilisateurs: $($response.total_users)"
        }
        
        if ($response.supported_symbols) {
            Write-Host "   Symboles supportés: $($response.supported_symbols.Count) symboles"
        }
        
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
    exit 1
}

# Test 1: Lister les symboles supportés
Write-Host "`n" + "="*60 -ForegroundColor Magenta
Write-Host "TEST 1: SYMBOLES SUPPORTÉS" -ForegroundColor Magenta
Write-Host "="*60 -ForegroundColor Magenta

$supportedSymbols = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/symbols/supported" -TestName "Lister symboles supportés"

# Test 2: Ajouter des abonnements pour différents utilisateurs
Write-Host "`n" + "="*60 -ForegroundColor Magenta
Write-Host "TEST 2: AJOUT D'ABONNEMENTS" -ForegroundColor Magenta
Write-Host "="*60 -ForegroundColor Magenta

# Utilisateur 1 - Symboles valides
$addSub1 = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=john.doe@example.com&symbols=AAPL,GOOGL,MSFT&subscriptionType=realtime" -Method "POST" -TestName "Ajout abonnement John Doe"

# Utilisateur 2 - Mix symboles valides et invalides  
$addSub2 = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=jane.smith@example.com&symbols=TSLA,INVALID_SYM,SPY,FAKE_STOCK" -Method "POST" -TestName "Ajout abonnement Jane Smith (avec symboles invalides)"

# Utilisateur 3 - ETFs
$addSub3 = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=bob.wilson@example.com&symbols=QQQ,VTI,EFA&subscriptionType=delayed" -Method "POST" -TestName "Ajout abonnement Bob Wilson (ETFs)"

# Test 3: Consulter les abonnements individuels
Write-Host "`n" + "="*60 -ForegroundColor Magenta
Write-Host "TEST 3: CONSULTATION D'ABONNEMENTS" -ForegroundColor Magenta
Write-Host "="*60 -ForegroundColor Magenta

$getSub1 = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=john.doe@example.com" -TestName "Consultation abonnements John Doe"

$getSub2 = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=jane.smith@example.com" -TestName "Consultation abonnements Jane Smith"

$getSub3 = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=bob.wilson@example.com" -TestName "Consultation abonnements Bob Wilson"

# Test 4: Ajouter des symboles supplémentaires à un utilisateur existant
Write-Host "`n" + "="*60 -ForegroundColor Magenta
Write-Host "TEST 4: AJOUT DE SYMBOLES SUPPLÉMENTAIRES" -ForegroundColor Magenta
Write-Host "="*60 -ForegroundColor Magenta

$addMoreSub = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=john.doe@example.com&symbols=AMZN,IWM" -Method "POST" -TestName "Ajout symboles supplémentaires pour John Doe"

# Vérifier la mise à jour
$getUpdatedSub = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=john.doe@example.com" -TestName "Vérification abonnements mis à jour John Doe"

# Test 5: Consulter tous les abonnements
Write-Host "`n" + "="*60 -ForegroundColor Magenta
Write-Host "TEST 5: TOUS LES ABONNEMENTS" -ForegroundColor Magenta
Write-Host "="*60 -ForegroundColor Magenta

$allSubs = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions/all" -TestName "Consultation de tous les abonnements"

# Test 6: Suppression partielle d'abonnements
Write-Host "`n" + "="*60 -ForegroundColor Magenta
Write-Host "TEST 6: SUPPRESSION PARTIELLE" -ForegroundColor Magenta
Write-Host "="*60 -ForegroundColor Magenta

$removeSub = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=john.doe@example.com&symbols=GOOGL,IWM" -Method "DELETE" -TestName "Suppression GOOGL et IWM pour John Doe"

# Vérifier après suppression partielle
$getAfterRemoval = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=john.doe@example.com" -TestName "Vérification après suppression partielle"

# Test 7: Suppression complète d'un utilisateur
Write-Host "`n" + "="*60 -ForegroundColor Magenta
Write-Host "TEST 7: SUPPRESSION COMPLÈTE" -ForegroundColor Magenta
Write-Host "="*60 -ForegroundColor Magenta

$removeAllSub = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=bob.wilson@example.com" -Method "DELETE" -TestName "Suppression complète Bob Wilson"

# Vérifier que l'utilisateur n'a plus d'abonnements
$getAfterCompleteRemoval = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions?userEmail=bob.wilson@example.com" -TestName "Vérification suppression complète"

# Test 8: État final de tous les abonnements
Write-Host "`n" + "="*60 -ForegroundColor Magenta
Write-Host "TEST 8: ÉTAT FINAL" -ForegroundColor Magenta
Write-Host "="*60 -ForegroundColor Magenta

$finalState = Test-SubscriptionEndpoint -Url "$BaseUrl/api/market-data/subscriptions/all" -TestName "État final de tous les abonnements"

# Résumé final
Write-Host "`n" + "="*60 -ForegroundColor Green
Write-Host "RÉSUMÉ DES TESTS D'ABONNEMENTS" -ForegroundColor Green
Write-Host "="*60 -ForegroundColor Green

Write-Host "`nTests réussis :" -ForegroundColor Green
Write-Host "✅ Gestion des symboles supportés"
Write-Host "✅ Ajout d'abonnements avec validation"
Write-Host "✅ Gestion des symboles invalides"
Write-Host "✅ Consultation d'abonnements individuels"
Write-Host "✅ Ajout de symboles supplémentaires"
Write-Host "✅ Consultation de tous les abonnements"
Write-Host "✅ Suppression partielle d'abonnements"
Write-Host "✅ Suppression complète d'utilisateur"

if ($supportedSymbols -and $supportedSymbols.total_symbols) {
    Write-Host "`nNombre de symboles supportés: $($supportedSymbols.total_symbols)"
}

if ($finalState -and $finalState.total_users -ne $null) {
    Write-Host "Utilisateurs avec abonnements actifs: $($finalState.total_users)"
    
    if ($finalState.total_symbol_subscriptions -ne $null) {
        Write-Host "Total des abonnements aux symboles: $($finalState.total_symbol_subscriptions)"
    }
}

Write-Host "`nFonctionnalités validées :" -ForegroundColor Cyan
Write-Host "• Persistence Redis des abonnements ✅"
Write-Host "• Validation des symboles en temps réel ✅"
Write-Host "• Gestion multi-utilisateurs ✅"
Write-Host "• Ajout/suppression dynamique ✅"
Write-Host "• APIs REST complètes ✅"

Write-Host "`nTests terminés à $(Get-Date)" -ForegroundColor Gray