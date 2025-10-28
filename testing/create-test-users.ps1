# Script PowerShell pour créer des utilisateurs de test
$BASE_URL = "http://localhost:80"

Write-Host "🔧 Création d'utilisateurs de test pour load testing..." -ForegroundColor Green

# Utilisateurs de test
$users = @(
    @{username="testuser1"; password="Test123!"},
    @{username="testuser2"; password="Test123!"},
    @{username="testuser3"; password="Test123!"},
    @{username="testuser4"; password="Test123!"},
    @{username="testuser5"; password="Test123!"}
)

$tokens = @()

foreach ($i in 0..($users.Count-1)) {
    $user = $users[$i]
    Write-Host "Créating user: $($user.username)" -ForegroundColor Yellow
    
    # 1. Inscription
    $registerBody = @{
        username = $user.username
        email = "$($user.username)@test.com"
        password = $user.password
        firstName = "Test"
        lastName = "User$($i+1)"
    } | ConvertTo-Json
    
    try {
        $registerResponse = Invoke-RestMethod -Uri "$BASE_URL/api/auth/register" -Method POST -Body $registerBody -ContentType "application/json" -ErrorAction SilentlyContinue
        Write-Host "✅ Inscription réussie pour $($user.username)" -ForegroundColor Green
        
        # 2. Login pour récupérer le token
        $loginBody = @{
            username = $user.username
            password = $user.password
        } | ConvertTo-Json
        
        Start-Sleep -Seconds 1
        
        $loginResponse = Invoke-RestMethod -Uri "$BASE_URL/api/auth/login" -Method POST -Body $loginBody -ContentType "application/json" -ErrorAction SilentlyContinue
        
        # Extraire le token (plusieurs formats possibles)
        $token = $null
        if ($loginResponse.token) { $token = $loginResponse.token }
        elseif ($loginResponse.accessToken) { $token = $loginResponse.accessToken }
        elseif ($loginResponse.jwt) { $token = $loginResponse.jwt }
        
        if ($token) {
            Write-Host "✅ Token récupéré pour $($user.username)" -ForegroundColor Green
            $tokens += @{
                username = $user.username
                token = $token
                userId = $i + 1
            }
        } else {
            Write-Host "❌ Échec récupération token pour $($user.username)" -ForegroundColor Red
            Write-Host "Login response: $($loginResponse | ConvertTo-Json)" -ForegroundColor Red
        }
    }
    catch {
        Write-Host "❌ Erreur pour $($user.username): $($_.Exception.Message)" -ForegroundColor Red
    }
    
    Start-Sleep -Seconds 1
}

# Sauvegarder les tokens
$tokensFile = "test-users-tokens.json"
$tokens | ConvertTo-Json -Depth 3 | Out-File -FilePath $tokensFile -Encoding UTF8

Write-Host "✅ Tokens sauvegardés dans $tokensFile" -ForegroundColor Green
Write-Host "Tokens disponibles: $($tokens.Count)" -ForegroundColor Cyan

# Afficher le contenu
Get-Content $tokensFile