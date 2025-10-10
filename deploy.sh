#!/bin/bash

# Script de déploiement BrokerX Microservices
# Pour utilisation avec GitHub Actions Self-Hosted Runner

set -e  # Arrêter le script en cas d'erreur

echo "🚀 Début du déploiement BrokerX Microservices..."

# Variables
DEPLOY_DIR="/home/gha-runner/brokerx-microservices"
DATA_DIR="/home/gha-runner/brokerx-data"
BACKUP_DIR="/home/gha-runner/brokerx-backups/$(date +%Y%m%d_%H%M%S)"

# Fonctions utilitaires
log_info() {
    echo "ℹ️  $1"
}

log_success() {
    echo "✅ $1"
}

log_error() {
    echo "❌ $1"
    exit 1
}

check_service_health() {
    local service_name=$1
    local port=$2
    local max_attempts=30
    local attempt=1
    
    log_info "Vérification de la santé du service $service_name sur le port $port..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f http://localhost:$port/actuator/health >/dev/null 2>&1; then
            log_success "Service $service_name est opérationnel!"
            return 0
        fi
        
        echo "Tentative $attempt/$max_attempts - Service $service_name pas encore prêt..."
        sleep 10
        ((attempt++))
    done
    
    log_error "Service $service_name n'a pas démarré correctement après $((max_attempts * 10)) secondes"
}

# Créer le répertoire de sauvegarde
log_info "Création du répertoire de sauvegarde: $BACKUP_DIR"
mkdir -p "$BACKUP_DIR"

# Sauvegarde des données existantes
if [ -d "$DATA_DIR" ]; then
    log_info "Sauvegarde des données existantes..."
    cp -r "$DATA_DIR" "$BACKUP_DIR/data_backup" || log_error "Échec de la sauvegarde des données"
    log_success "Données sauvegardées dans $BACKUP_DIR"
fi

# Naviguer vers le répertoire de déploiement
cd "$DEPLOY_DIR" || log_error "Impossible d'accéder au répertoire $DEPLOY_DIR"

# Vérifier que tous les fichiers nécessaires sont présents
log_info "Vérification des fichiers de déploiement..."
required_files=("docker-compose.yml" "docker-compose.monitoring.yml" ".env")
for file in "${required_files[@]}"; do
    if [ ! -f "$file" ]; then
        log_error "Fichier requis manquant: $file"
    fi
done
log_success "Tous les fichiers requis sont présents"

# Arrêter les services existants
log_info "Arrêt des services existants..."
docker compose down --remove-orphans || true
docker compose -f docker-compose.monitoring.yml down || true
log_success "Services existants arrêtés"

# Nettoyer les images obsolètes
log_info "Nettoyage des images Docker obsolètes..."
docker system prune -f
log_success "Images obsolètes supprimées"

# Créer les répertoires de données
log_info "Création/vérification des répertoires de données..."
mkdir -p "$DATA_DIR"
chown -R gha-runner:gha-runner "$DATA_DIR"
log_success "Répertoires de données préparés"

# Construire les nouvelles images
log_info "Construction des images Docker..."
docker compose build --no-cache || log_error "Échec de la construction des images"
log_success "Images Docker construites avec succès"

# Démarrer l'infrastructure de monitoring
log_info "Démarrage de l'infrastructure de monitoring..."
docker compose -f docker-compose.monitoring.yml up -d || log_error "Échec du démarrage du monitoring"
log_success "Infrastructure de monitoring démarrée"

# Attendre que l'infrastructure soit prête
log_info "Attente de la disponibilité de l'infrastructure..."
sleep 30

# Démarrer les microservices
log_info "Démarrage des microservices..."
docker compose up -d || log_error "Échec du démarrage des microservices"
log_success "Microservices démarrés"

# Vérifier la santé de tous les services
log_info "Vérification de la santé des services..."
check_service_health "Gateway" 8080
check_service_health "AuthService" 8081
check_service_health "ClientService" 8082
check_service_health "WalletService" 8083
check_service_health "OrderService" 8084

# Vérifier les services de monitoring
log_info "Vérification des services de monitoring..."
if curl -f http://localhost:9090 >/dev/null 2>&1; then
    log_success "Prometheus est opérationnel!"
else
    log_error "Prometheus n'est pas accessible"
fi

if curl -f http://localhost:3000 >/dev/null 2>&1; then
    log_success "Grafana est opérationnel!"
else
    log_error "Grafana n'est pas accessible"
fi

# Afficher un résumé du déploiement
echo ""
echo "🎉 Déploiement terminé avec succès!"
echo ""
echo "📊 Services disponibles:"
echo "   - Gateway (API): http://localhost:8080"
echo "   - AuthService: http://localhost:8081"
echo "   - ClientService: http://localhost:8082"
echo "   - WalletService: http://localhost:8083"
echo "   - OrderService: http://localhost:8084"
echo ""
echo "📈 Monitoring:"
echo "   - Prometheus: http://localhost:9090"
echo "   - Grafana: http://localhost:3000"
echo ""
echo "📖 Documentation API:"
echo "   - Gateway Swagger: http://localhost:8080/swagger-ui.html"
echo "   - AuthService Swagger: http://localhost:8081/swagger-ui.html"
echo "   - ClientService Swagger: http://localhost:8082/swagger-ui.html"
echo "   - WalletService Swagger: http://localhost:8083/swagger-ui.html"
echo "   - OrderService Swagger: http://localhost:8084/swagger-ui.html"
echo ""
echo "💾 Sauvegarde créée dans: $BACKUP_DIR"
echo ""
echo "🔍 Pour voir les logs:"
echo "   docker compose logs -f [service-name]"
echo ""
echo "🛑 Pour arrêter les services:"
echo "   docker compose down"
echo "   docker compose -f docker-compose.monitoring.yml down"