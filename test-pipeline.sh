#!/bin/bash

# Script de test pour valider le pipeline CI/CD BrokerX
# Utilise curl pour tester tous les endpoints

set -e

echo "Début des tests du pipeline BrokerX..."

# Configuration
GATEWAY_URL="http://localhost:8080"
AUTH_URL="http://localhost:8081"
CLIENT_URL="http://localhost:8082"
WALLET_URL="http://localhost:8083"
ORDER_URL="http://localhost:8084"
PROMETHEUS_URL="http://localhost:9090"
GRAFANA_URL="http://localhost:3000"

# Couleurs pour l'affichage
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Fonctions utilitaires
log_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Fonction pour tester un endpoint
test_endpoint() {
    local name=$1
    local url=$2
    local expected_status=${3:-200}
    
    log_info "Test: $name"
    
    local response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    
    if [ "$response" = "$expected_status" ]; then
        log_success "$name - Status: $response"
        return 0
    else
        log_error "$name - Expected: $expected_status, Got: $response"
        return 1
    fi
}

# Fonction pour tester un endpoint avec authentification
test_authenticated_endpoint() {
    local name=$1
    local url=$2
    local token=$3
    local expected_status=${4:-200}
    
    log_info "Test: $name (avec authentification)"
    
    local response=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer $token" \
        "$url" 2>/dev/null || echo "000")
    
    if [ "$response" = "$expected_status" ]; then
        log_success "$name - Status: $response"
        return 0
    else
        log_error "$name - Expected: $expected_status, Got: $response"
        return 1
    fi
}

# Variables pour les tests
failed_tests=0
total_tests=0

# Fonction pour compter les tests
count_test() {
    ((total_tests++))
    if [ $? -ne 0 ]; then
        ((failed_tests++))
    fi
}

echo ""
echo "🏥 Tests de santé des services..."

# Test des endpoints de santé
test_endpoint "Gateway Health" "$GATEWAY_URL/actuator/health"
count_test

test_endpoint "AuthService Health" "$AUTH_URL/actuator/health"
count_test

test_endpoint "ClientService Health" "$CLIENT_URL/actuator/health"
count_test

test_endpoint "WalletService Health" "$WALLET_URL/actuator/health"
count_test

test_endpoint "OrderService Health" "$ORDER_URL/actuator/health"
count_test

echo ""
echo "📊 Tests des endpoints de métriques..."

# Test des métriques Prometheus
test_endpoint "Gateway Prometheus" "$GATEWAY_URL/actuator/prometheus"
count_test

test_endpoint "AuthService Prometheus" "$AUTH_URL/actuator/prometheus"
count_test

test_endpoint "ClientService Prometheus" "$CLIENT_URL/actuator/prometheus"
count_test

test_endpoint "WalletService Prometheus" "$WALLET_URL/actuator/prometheus"
count_test

test_endpoint "OrderService Prometheus" "$ORDER_URL/actuator/prometheus"
count_test

echo ""
echo "📖 Tests de la documentation API..."

# Test des endpoints Swagger
test_endpoint "Gateway Swagger UI" "$GATEWAY_URL/swagger-ui.html"
count_test

test_endpoint "AuthService Swagger UI" "$AUTH_URL/swagger-ui.html"
count_test

test_endpoint "ClientService Swagger UI" "$CLIENT_URL/swagger-ui.html"
count_test

test_endpoint "WalletService Swagger UI" "$WALLET_URL/swagger-ui.html"
count_test

test_endpoint "OrderService Swagger UI" "$ORDER_URL/swagger-ui.html"
count_test

# Test des API docs JSON
test_endpoint "Gateway API Docs" "$GATEWAY_URL/api-docs"
count_test

test_endpoint "AuthService API Docs" "$AUTH_URL/api-docs"
count_test

echo ""
echo "📈 Tests du monitoring..."

# Test Prometheus
test_endpoint "Prometheus" "$PROMETHEUS_URL"
count_test

test_endpoint "Prometheus Targets" "$PROMETHEUS_URL/targets"
count_test

# Test Grafana
test_endpoint "Grafana" "$GRAFANA_URL"
count_test

echo ""
echo "🔧 Tests des endpoints fonctionnels..."

# Test de registration (doit retourner 400 ou 422 sans données)
test_endpoint "Registration Endpoint" "$GATEWAY_URL/api/auth/register" 400
count_test

# Test de login (doit retourner 400 ou 401 sans credentials)
test_endpoint "Login Endpoint" "$GATEWAY_URL/api/auth/login" 400
count_test

echo ""
echo "🧪 Test d'intégration simple..."

# Test de création d'un utilisateur et login
log_info "Test de création d'utilisateur..."

# Données de test
TEST_USER_DATA='{
    "firstName": "Test",
    "lastName": "User",
    "email": "test@example.com",
    "password": "TestPassword123!"
}'

# Tentative de création d'utilisateur
REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -d "$TEST_USER_DATA" \
    "$GATEWAY_URL/api/auth/register" 2>/dev/null)

REGISTER_STATUS=$(echo "$REGISTER_RESPONSE" | tail -n1)
REGISTER_BODY=$(echo "$REGISTER_RESPONSE" | head -n -1)

if [ "$REGISTER_STATUS" = "201" ] || [ "$REGISTER_STATUS" = "200" ]; then
    log_success "Création d'utilisateur réussie - Status: $REGISTER_STATUS"
else
    log_info "Création d'utilisateur - Status: $REGISTER_STATUS (peut être normal si l'utilisateur existe déjà)"
fi

echo ""
echo "📊 Résumé des tests..."

# Calcul du pourcentage de réussite
if [ $total_tests -gt 0 ]; then
    success_rate=$(( (total_tests - failed_tests) * 100 / total_tests ))
    
    echo "Tests exécutés: $total_tests"
    echo "Tests réussis: $((total_tests - failed_tests))"
    echo "Tests échoués: $failed_tests"
    echo "Taux de réussite: $success_rate%"
    
    if [ $failed_tests -eq 0 ]; then
        log_success "Tous les tests sont passés! 🎉"
        echo ""
        echo "🌐 URLs disponibles:"
        echo "   - Gateway API: $GATEWAY_URL"
        echo "   - Prometheus: $PROMETHEUS_URL"
        echo "   - Grafana: $GRAFANA_URL"
        echo "   - Gateway Swagger: $GATEWAY_URL/swagger-ui.html"
        exit 0
    else
        log_error "$failed_tests test(s) ont échoué"
        exit 1
    fi
else
    log_error "Aucun test exécuté"
    exit 1
fi