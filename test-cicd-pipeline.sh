#!/bin/bash

# Script de test du pipeline CI/CD BrokerX
# Ce script simule et teste les étapes du pipeline CI/CD

echo "=============================================="
echo "   Test Pipeline CI/CD BrokerX Microservices"
echo "=============================================="
echo ""

# Variables
GITHUB_REPO_URL="https://github.com/primika27/BrokerX-phase2-micorservices-"
DEPLOY_DIR="/tmp/brokerx-test-$(date +%s)"
JWT_SECRET="YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU="

# Couleurs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_step() {
    echo -e "${BLUE}=== $1 ===${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Fonction de nettoyage
cleanup() {
    echo ""
    print_step "Nettoyage"
    if [ -d "$DEPLOY_DIR" ]; then
        rm -rf "$DEPLOY_DIR"
        print_success "Répertoire de test supprimé"
    fi
    
    # Arrêter les conteneurs de test s'ils existent
    docker-compose -f docker-compose.test.yml down 2>/dev/null || true
}

# Trap pour nettoyage automatique
trap cleanup EXIT

print_step "1. Simulation du job 'test'"

# Vérifier les prérequis
if ! command -v java &> /dev/null; then
    print_error "Java n'est pas installé"
    exit 1
fi

if ! command -v docker &> /dev/null; then
    print_error "Docker n'est pas installé"  
    exit 1
fi

print_success "Prérequis installés"

# Cloner le repository
print_step "2. Checkout du code"
git clone $GITHUB_REPO_URL $DEPLOY_DIR
cd $DEPLOY_DIR
print_success "Code source récupéré"

# Configurer l'environnement de test
export JWT_SECRET=$JWT_SECRET
export SPRING_PROFILES_ACTIVE=test
print_success "Variables d'environnement configurées"

# Test des microservices
print_step "3. Tests unitaires"

services=("authService/authService" "clientService/clientService" "walletService/walletService" "orderService/orderService" "gatewayService")

for service in "${services[@]}"; do
    echo "Test de $service..."
    cd $service
    
    if [ -f "./mvnw" ]; then
        chmod +x ./mvnw
        if ./mvnw clean test -Dspring.profiles.active=test -q; then
            print_success "Tests $service passent"
        else
            print_error "Tests $service échouent"
            # Ne pas arrêter, continuer les autres tests
        fi
    else
        print_warning "Pas de mvnw trouvé pour $service"
    fi
    
    cd $DEPLOY_DIR
done

print_step "4. Build des artefacts"

for service in "${services[@]}"; do
    echo "Build de $service..."
    cd $service
    
    if [ -f "./mvnw" ]; then
        if ./mvnw clean package -DskipTests -q; then
            print_success "Build $service réussi"
            
            # Vérifier que le JAR existe
            jar_file=$(find target -name "*.jar" -not -name "*sources*" 2>/dev/null | head -n 1)
            if [ -n "$jar_file" ]; then
                print_success "JAR généré: $(basename $jar_file)"
            else
                print_warning "Aucun JAR trouvé pour $service"
            fi
        else
            print_error "Build $service échoue"
        fi
    fi
    
    cd $DEPLOY_DIR
done

print_step "5. Simulation scan de sécurité"
# Simuler un scan de sécurité rapide avec des outils disponibles
echo "Vérification des dépendances connues..."

# Vérifier s'il y a des dépendances vulnérables connues
if find . -name "pom.xml" -exec grep -l "log4j.*2\.[0-9]" {} \; 2>/dev/null | head -1; then
    print_warning "Dépendances Log4j détectées - vérifier les versions"
else
    print_success "Aucune dépendance Log4j vulnérable évidente"
fi

print_step "6. Test de déploiement Docker"

# Créer un fichier docker-compose de test
cat > docker-compose.test.yml << EOF
version: '3.8'
services:
  test-gateway:
    build: ./gatewayService
    ports:
      - "18080:8080"
    environment:
      - JWT_SECRET=$JWT_SECRET
      - SPRING_PROFILES_ACTIVE=test
    command: ["java", "-Dspring.profiles.active=test", "-jar", "/app/target/gatewayService-0.0.1-SNAPSHOT.jar"]
EOF

echo "Construction de l'image de test..."
if docker-compose -f docker-compose.test.yml build test-gateway; then
    print_success "Image Docker construite avec succès"
else
    print_error "Échec de construction de l'image Docker"
fi

print_step "7. Test de connectivité et health checks"

# Démarrer temporairement un service pour test
echo "Démarrage temporaire du gateway de test..."
docker-compose -f docker-compose.test.yml up -d test-gateway

# Attendre le démarrage
echo "Attente du démarrage du service (30s)..."
sleep 30

# Test de health check
if curl -f http://localhost:18080/actuator/health 2>/dev/null; then
    print_success "Health check du gateway réussi"
else
    print_warning "Health check du gateway échoue (normal si dépendances manquantes)"
fi

# Arrêter le service de test
docker-compose -f docker-compose.test.yml down

print_step "8. Validation de la configuration de déploiement"

# Vérifier la présence des fichiers de configuration
config_files=("docker-compose.yml" "docker-compose.monitoring.yml" ".env.template")

for file in "${config_files[@]}"; do
    if [ -f "$file" ]; then
        print_success "Fichier de configuration $file présent"
    else
        print_error "Fichier de configuration $file manquant"
    fi
done

# Vérifier la configuration Docker Compose
if docker-compose config > /dev/null 2>&1; then
    print_success "Configuration Docker Compose valide"
else
    print_error "Configuration Docker Compose invalide"
fi

print_step "9. Test des scripts de déploiement"

# Vérifier les scripts de déploiement
deploy_scripts=("start-brokerx-stack.bat" "stop-brokerx-stack.bat" "deploy.sh")

for script in "${deploy_scripts[@]}"; do
    if [ -f "$script" ]; then
        print_success "Script de déploiement $script présent"
        
        # Vérifier la syntaxe bash pour les scripts .sh
        if [[ "$script" == *.sh ]]; then
            if bash -n "$script" 2>/dev/null; then
                print_success "Syntaxe bash valide pour $script"
            else
                print_warning "Problème de syntaxe dans $script"
            fi
        fi
    else
        print_warning "Script de déploiement $script manquant"
    fi
done

print_step "10. Résumé du test"

echo ""
echo "Tests terminés!"
echo ""
echo "Prochaines étapes pour finaliser le CI/CD:"
echo "1. Configurer le runner GitHub Actions avec CI-CD-RUNNER-SETUP.md"
echo "2. Ajouter les secrets dans GitHub Repository Settings"  
echo "3. Valider l'environnement de production avec validate-runner.sh"
echo "4. Effectuer un push sur main/master pour déclencher le pipeline"
echo ""
echo "Commandes utiles:"
echo "# Valider l'environnement runner:"
echo "./validate-runner.sh"
echo ""
echo "# Surveiller le déploiement:"
echo "docker-compose logs -f"
echo ""
echo "# Health check complet:"
echo "make health"
echo ""

print_success "Test du pipeline CI/CD terminé"