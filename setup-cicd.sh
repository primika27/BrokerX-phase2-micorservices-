#!/bin/bash

# Script de configuration rapide CI/CD pour BrokerX
# Ce script automatise la configuration initiale du CI/CD

echo "================================================"
echo "  Configuration Rapide CI/CD BrokerX"  
echo "================================================"
echo ""

# Variables
GITHUB_REPO="primika27/BrokerX-phase2-micorservices-"
RUNNER_USER="gha-runner"

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

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# Vérification des permissions root
if [ "$EUID" -ne 0 ]; then
    print_error "Ce script doit être exécuté avec sudo"
    echo "Usage: sudo ./setup-cicd.sh"
    exit 1
fi

print_step "1. Création de l'utilisateur runner"

# Créer l'utilisateur si n'existe pas
if ! id "$RUNNER_USER" &>/dev/null; then
    useradd -m -s /bin/bash "$RUNNER_USER"
    print_success "Utilisateur $RUNNER_USER créé"
else
    print_info "Utilisateur $RUNNER_USER existe déjà"
fi

# Ajouter au groupe docker
usermod -aG docker "$RUNNER_USER"
print_success "Utilisateur ajouté au groupe docker"

print_step "2. Création des répertoires"

# Créer les répertoires nécessaires
directories=(
    "/home/$RUNNER_USER/brokerx-microservices"
    "/home/$RUNNER_USER/brokerx-data"
    "/home/$RUNNER_USER/brokerx-backups"
    "/home/$RUNNER_USER/actions-runner"
)

for dir in "${directories[@]}"; do
    mkdir -p "$dir"
    chown -R "$RUNNER_USER:$RUNNER_USER" "$dir"
    print_success "Répertoire créé: $dir"
done

print_step "3. Installation des dépendances"

# Mettre à jour le système
apt update -qq
print_success "Système mis à jour"

# Installer les paquets nécessaires
packages=(
    "git"
    "curl" 
    "wget"
    "unzip"
    "jq"
    "netcat"
)

for package in "${packages[@]}"; do
    if ! dpkg -l | grep -q "^ii  $package "; then
        apt install -y "$package" -qq
        print_success "Installé: $package"
    else
        print_info "$package déjà installé"
    fi
done

print_step "4. Configuration Docker"

# Installer Docker si pas présent
if ! command -v docker &> /dev/null; then
    print_info "Installation de Docker..."
    curl -fsSL https://get.docker.com -o get-docker.sh
    sh get-docker.sh
    rm get-docker.sh
    print_success "Docker installé"
else
    print_info "Docker déjà installé"
fi

# Démarrer et activer Docker
systemctl enable docker
systemctl start docker
print_success "Service Docker activé et démarré"

# Vérifier que l'utilisateur peut utiliser Docker
sudo -u "$RUNNER_USER" docker ps &>/dev/null
if [ $? -eq 0 ]; then
    print_success "Permissions Docker OK pour $RUNNER_USER"
else
    print_info "Redémarrage nécessaire pour les permissions Docker"
fi

print_step "5. Configuration des scripts"

# Rendre les scripts exécutables
script_files=(
    "validate-runner.sh"
    "test-cicd-pipeline.sh"  
    "deploy.sh"
    "test-pipeline.sh"
)

for script in "${script_files[@]}"; do
    if [ -f "$script" ]; then
        chmod +x "$script"
        print_success "Script $script rendu exécutable"
    else
        print_info "Script $script non trouvé (normal si pas encore créé)"
    fi
done

print_step "6. Configuration firewall (UFW)"

# Installer et configurer UFW
if ! command -v ufw &> /dev/null; then
    apt install -y ufw -qq
    print_success "UFW installé"
fi

# Configurer les règles de base
ufw --force reset >/dev/null 2>&1
ufw default deny incoming >/dev/null 2>&1  
ufw default allow outgoing >/dev/null 2>&1

# Autoriser SSH
ufw allow ssh >/dev/null 2>&1

# Autoriser les ports de l'application
ports=(8080 8081 8082 8083 8084 8085 5173 9090 3000)
for port in "${ports[@]}"; do
    ufw allow "$port" >/dev/null 2>&1
done

# Activer UFW
ufw --force enable >/dev/null 2>&1
print_success "Firewall configuré et activé"

print_step "7. Préparation du runner GitHub Actions"

print_info "Pour configurer le runner GitHub Actions:"
echo ""
echo "1. Aller sur GitHub: https://github.com/$GITHUB_REPO"
echo "2. Settings → Actions → Runners → New self-hosted runner"
echo "3. Sélectionner Linux x64"
echo "4. Exécuter les commandes d'installation EN TANT QUE gha-runner:"
echo ""
echo "   sudo su - $RUNNER_USER"
echo "   cd ~/actions-runner"
echo "   # Suivre les instructions GitHub"
echo ""
echo "5. Installer le service:"
echo "   sudo ~/actions-runner/svc.sh install"
echo "   sudo ~/actions-runner/svc.sh start"
echo ""

print_step "8. Configuration des secrets GitHub"

print_info "Secrets à configurer dans GitHub Repository Settings:"
echo ""
echo "JWT_SECRET: YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU="
echo "EMAIL_USER: votre-email@gmail.com"  
echo "EMAIL_PASS: votre-mot-de-passe-app"
echo "GRAFANA_ADMIN_PASSWORD: admin123"
echo ""

print_step "9. Scripts de validation disponibles"

echo "Scripts créés pour valider la configuration:"
echo ""
echo "# Valider l'environnement:"
echo "./validate-runner.sh"
echo ""
echo "# Tester le pipeline localement:"  
echo "./test-cicd-pipeline.sh"
echo ""
echo "# Suivre la checklist complète:"
echo "cat CI-CD-CHECKLIST.md"
echo ""

print_step "Configuration terminée!"

print_success "Infrastructure CI/CD configurée"
print_info "Étapes suivantes:"
echo "1. Configurer le runner GitHub Actions (instructions ci-dessus)"
echo "2. Ajouter les secrets GitHub Repository"
echo "3. Exécuter ./validate-runner.sh pour validation"
echo "4. Faire un push sur main/master pour tester le pipeline"
echo ""
echo "Documentation complète: CI-CD-RUNNER-SETUP.md"