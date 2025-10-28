#!/bin/bash

# Script de validation des runners GitHub Actions
# Ce script vérifie que l'environnement de déploiement est correctement configuré

echo "==================================================="
echo "    BrokerX CI/CD Runner Validation Script"
echo "==================================================="
echo ""

# Couleurs pour l'affichage
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Fonctions utilitaires
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "ℹ️ $1"
}

# Variables
RUNNER_USER="gha-runner"
DEPLOY_DIR="/home/$RUNNER_USER/brokerx-microservices"
DATA_DIR="/home/$RUNNER_USER/brokerx-data"
BACKUP_DIR="/home/$RUNNER_USER/brokerx-backups"

echo "1. Vérification de l'utilisateur runner"
echo "======================================"

# Vérifier que l'utilisateur runner existe
if id "$RUNNER_USER" &>/dev/null; then
    print_success "Utilisateur $RUNNER_USER existe"
else
    print_error "Utilisateur $RUNNER_USER n'existe pas"
    echo "Commande pour créer l'utilisateur:"
    echo "sudo useradd -m -s /bin/bash $RUNNER_USER"
    echo "sudo usermod -aG docker $RUNNER_USER"
    exit 1
fi

echo ""
echo "2. Vérification des répertoires"
echo "================================"

# Vérifier les répertoires
for dir in "$DEPLOY_DIR" "$DATA_DIR" "$BACKUP_DIR"; do
    if [ -d "$dir" ]; then
        print_success "Répertoire $dir existe"
        # Vérifier les permissions
        if [ -w "$dir" ]; then
            print_success "Permissions d'écriture OK pour $dir"
        else
            print_error "Pas de permissions d'écriture pour $dir"
        fi
    else
        print_warning "Répertoire $dir n'existe pas"
        echo "Commande pour créer: sudo mkdir -p $dir && sudo chown -R $RUNNER_USER:$RUNNER_USER $dir"
    fi
done

echo ""
echo "3. Vérification des dépendances système"
echo "======================================="

# Vérifier Docker
if command -v docker &> /dev/null; then
    print_success "Docker est installé"
    
    # Vérifier que Docker fonctionne
    if docker ps &>/dev/null; then
        print_success "Docker daemon est actif"
    else
        print_error "Docker daemon n'est pas actif"
        echo "Démarrer avec: sudo systemctl start docker"
    fi
    
    # Vérifier les permissions Docker pour l'utilisateur runner
    if groups "$RUNNER_USER" | grep -q docker; then
        print_success "Utilisateur $RUNNER_USER est dans le groupe docker"
    else
        print_error "Utilisateur $RUNNER_USER n'est PAS dans le groupe docker"
        echo "Ajouter avec: sudo usermod -aG docker $RUNNER_USER"
    fi
else
    print_error "Docker n'est pas installé"
fi

# Vérifier Docker Compose
if command -v docker-compose &> /dev/null || docker compose version &> /dev/null; then
    print_success "Docker Compose est disponible"
else
    print_error "Docker Compose n'est pas installé"
fi

# Vérifier Git
if command -v git &> /dev/null; then
    print_success "Git est installé"
else
    print_error "Git n'est pas installé"
fi

# Vérifier curl
if command -v curl &> /dev/null; then
    print_success "curl est installé"
else
    print_error "curl n'est pas installé"
fi

echo ""
echo "4. Vérification des ports"
echo "========================="

# Ports requis
REQUIRED_PORTS=(8080 8081 8082 8083 8084 8085 5173 9090 3000)

for port in "${REQUIRED_PORTS[@]}"; do
    if netstat -tuln | grep -q ":$port "; then
        print_warning "Port $port est occupé"
        echo "   Processus: $(lsof -ti:$port 2>/dev/null || echo 'Inconnu')"
    else
        print_success "Port $port est libre"
    fi
done

echo ""
echo "5. Vérification du runner GitHub Actions"
echo "========================================"

# Vérifier si le runner est enregistré
RUNNER_DIR="/home/$RUNNER_USER/actions-runner"
if [ -d "$RUNNER_DIR" ]; then
    print_success "Répertoire du runner GitHub Actions existe"
    
    # Vérifier le statut du runner
    if [ -f "$RUNNER_DIR/.runner" ]; then
        print_success "Runner GitHub Actions est configuré"
        
        # Vérifier si le service runner est actif
        if systemctl is-active --quiet actions.runner.* 2>/dev/null; then
            print_success "Service runner GitHub Actions est actif"
        else
            print_warning "Service runner GitHub Actions n'est pas actif"
            echo "Vérifier avec: sudo systemctl status actions.runner.*"
        fi
    else
        print_error "Runner GitHub Actions n'est pas configuré"
        echo "Configurer le runner depuis GitHub repository Settings > Actions > Runners"
    fi
else
    print_error "Runner GitHub Actions n'est pas installé"
fi

echo ""
echo "6. Test de connectivité réseau"
echo "============================="

# Test de connectivité vers GitHub
if ping -c 1 github.com &>/dev/null; then
    print_success "Connectivité vers GitHub OK"
else
    print_error "Pas de connectivité vers GitHub"
fi

# Test de connectivité vers Docker Hub
if ping -c 1 registry-1.docker.io &>/dev/null; then
    print_success "Connectivité vers Docker Hub OK"
else
    print_warning "Connectivité vers Docker Hub limitée"
fi

echo ""
echo "7. Vérification des ressources système"
echo "====================================="

# Vérifier l'espace disque
DISK_USAGE=$(df /home | tail -1 | awk '{print $5}' | sed 's/%//')
if [ "$DISK_USAGE" -lt 80 ]; then
    print_success "Espace disque suffisant (${DISK_USAGE}% utilisé)"
else
    print_warning "Espace disque faible (${DISK_USAGE}% utilisé)"
fi

# Vérifier la mémoire
MEMORY_GB=$(free -g | grep "^Mem" | awk '{print $2}')
if [ "$MEMORY_GB" -ge 4 ]; then
    print_success "Mémoire suffisante (${MEMORY_GB}GB)"
else
    print_warning "Mémoire limitée (${MEMORY_GB}GB) - minimum recommandé: 4GB"
fi

echo ""
echo "8. Résumé et recommandations"
echo "============================"

echo ""
echo "Commandes de préparation rapide:"
echo "---------------------------------"
echo "# Créer l'utilisateur runner"
echo "sudo useradd -m -s /bin/bash $RUNNER_USER"
echo "sudo usermod -aG docker $RUNNER_USER"
echo ""
echo "# Créer les répertoires nécessaires"
echo "sudo mkdir -p $DEPLOY_DIR $DATA_DIR $BACKUP_DIR"
echo "sudo chown -R $RUNNER_USER:$RUNNER_USER /home/$RUNNER_USER/"
echo ""
echo "# Installer Docker (si nécessaire)"
echo "curl -fsSL https://get.docker.com -o get-docker.sh"
echo "sudo sh get-docker.sh"
echo "sudo systemctl enable docker"
echo "sudo systemctl start docker"
echo ""
echo "# Variables d'environnement GitHub Secrets à configurer:"
echo "# JWT_SECRET, EMAIL_USER, EMAIL_PASS, GRAFANA_ADMIN_PASSWORD"
echo ""
echo "Configuration terminée!"