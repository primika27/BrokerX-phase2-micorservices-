#!/bin/bash

# Script de diagnostic des erreurs CI/CD BrokerX
# Ce script identifie et corrige les problèmes courants du pipeline CI/CD

echo "=============================================="
echo "    Diagnostic des Erreurs CI/CD BrokerX"
echo "=============================================="
echo ""

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

print_info() {
    echo "ℹ️ $1"
}

print_step "1. Vérification des fichiers workflow"

# Vérifier la présence des workflows
if [ -f ".github/workflows/ci-cd.yml" ]; then
    print_success "Workflow principal ci-cd.yml trouvé"
else
    print_error "Workflow principal ci-cd.yml manquant"
fi

if [ -f ".github/workflows/ci-simple.yml" ]; then
    print_success "Workflow simple ci-simple.yml trouvé"
else
    print_warning "Workflow simple ci-simple.yml manquant"
fi

print_step "2. Diagnostic des erreurs communes"

# Vérifier la syntaxe YAML
echo "Vérification de la syntaxe YAML..."
if command -v yamllint &> /dev/null; then
    if yamllint .github/workflows/*.yml; then
        print_success "Syntaxe YAML valide"
    else
        print_error "Erreurs de syntaxe YAML détectées"
    fi
else
    print_info "yamllint non installé - installation recommandée: pip install yamllint"
fi

print_step "3. Vérification des secrets GitHub"

echo "Secrets requis pour le pipeline complet:"
echo "- JWT_SECRET (88 caractères base64)"
echo "- EMAIL_USER (adresse email valide)"
echo "- EMAIL_PASS (mot de passe d'application)"
echo "- GRAFANA_ADMIN_PASSWORD (minimum 8 caractères)"
echo ""
print_info "Configurez ces secrets dans GitHub Repository Settings → Secrets and variables → Actions"

print_step "4. Vérification des dépendances Maven"

services=("authService/authService" "clientService/clientService" "walletService/walletService" "orderService/orderService" "gatewayService")

for service in "${services[@]}"; do
    if [ -d "$service" ]; then
        echo "Vérification de $service..."
        
        if [ -f "$service/pom.xml" ]; then
            print_success "pom.xml trouvé pour $service"
            
            # Vérifier mvnw
            if [ -f "$service/mvnw" ]; then
                print_success "mvnw trouvé pour $service"
                chmod +x "$service/mvnw"
            else
                print_error "mvnw manquant pour $service"
            fi
            
            # Vérifier la structure src
            if [ -d "$service/src/main/java" ]; then
                print_success "Structure source Java OK pour $service"
            else
                print_warning "Structure source manquante pour $service"
            fi
            
            # Vérifier les tests
            if [ -d "$service/src/test/java" ]; then
                print_success "Tests trouvés pour $service"
            else
                print_warning "Pas de tests pour $service"
            fi
            
        else
            print_error "pom.xml manquant pour $service"
        fi
    else
        print_error "Répertoire $service manquant"
    fi
    echo ""
done

print_step "5. Test de compilation locale"

print_info "Test de compilation rapide (premier service)..."
if [ -d "authService/authService" ]; then
    cd authService/authService
    if [ -f "mvnw" ]; then
        chmod +x ./mvnw
        if ./mvnw compile -q; then
            print_success "Compilation AuthService réussie"
        else
            print_error "Échec compilation AuthService"
        fi
    fi
    cd ../..
fi

print_step "6. Vérification Docker"

if command -v docker &> /dev/null; then
    print_success "Docker installé"
    
    # Vérifier les Dockerfiles
    dockerfiles=("gatewayService/Dockerfile" "authService/authService/Dockerfile")
    
    for dockerfile in "${dockerfiles[@]}"; do
        if [ -f "$dockerfile" ]; then
            print_success "Dockerfile trouvé: $dockerfile"
        else
            print_warning "Dockerfile manquant: $dockerfile"
        fi
    done
    
else
    print_error "Docker non installé"
fi

print_step "7. Solutions aux erreurs communes"

echo ""
echo "SOLUTIONS AUX PROBLÈMES FRÉQUENTS:"
echo "=================================="
echo ""

echo "1. ERREUR: 'Context access might be invalid: JWT_SECRET'"
echo "   SOLUTION: Configurer les secrets GitHub ou utiliser le workflow simple"
echo "   → Utiliser ci-simple.yml qui fonctionne sans secrets"
echo ""

echo "2. ERREUR: 'Environment production not found'"
echo "   SOLUTION: Créer l'environnement dans GitHub ou commenter la section"
echo "   → GitHub Settings → Environments → New environment 'production'"
echo ""

echo "3. ERREUR: 'No self-hosted runners available'"
echo "   SOLUTION: Configurer un runner ou utiliser ubuntu-latest temporairement"
echo "   → Suivre CI-CD-RUNNER-SETUP.md pour configurer le runner"
echo ""

echo "4. ERREUR: Tests échouent"
echo "   SOLUTION: Vérifier les variables d'environnement et profils Spring Boot"
echo "   → Utiliser -Dspring.profiles.active=test"
echo ""

echo "5. ERREUR: Build Maven échoue"
echo "   SOLUTION: Nettoyer et rebuilder avec les bonnes permissions"
echo "   → chmod +x ./mvnw && ./mvnw clean compile"
echo ""

print_step "8. Configuration recommandée pour débuter"

echo ""
echo "CONFIGURATION RAPIDE POUR COMMENCER:"
echo "===================================="
echo ""
echo "1. Renommer le workflow principal:"
echo "   mv .github/workflows/ci-cd.yml .github/workflows/ci-cd.yml.disabled"
echo ""
echo "2. Utiliser le workflow simple:"
echo "   → ci-simple.yml fonctionne sur ubuntu-latest sans secrets"
echo ""
echo "3. Tester localement d'abord:"
echo "   → ./test-cicd-pipeline.sh"
echo ""
echo "4. Après validation, configurer les secrets et le runner"
echo "   → Suivre CI-CD-RUNNER-SETUP.md"
echo ""

print_step "9. Commandes de test rapide"

echo ""
echo "TESTS RAPIDES À EXÉCUTER:"
echo "========================="
echo ""
echo "# Test compilation de tous les services:"
echo "for dir in authService/authService clientService/clientService walletService/walletService orderService/orderService gatewayService; do"
echo "  echo \"Testing \$dir...\""
echo "  cd \$dir && chmod +x ./mvnw && ./mvnw clean compile -q && cd \$(dirname \$PWD)"
echo "done"
echo ""
echo "# Test de build Docker simple:"
echo "docker build -t test-gateway ./gatewayService"
echo ""
echo "# Push pour déclencher le workflow simple:"
echo "git add ."
echo "git commit -m \"Test CI/CD simple workflow\""
echo "git push origin \$(git branch --show-current)"
echo ""

print_step "10. État actuel et recommandations"

echo ""
if [ -f ".github/workflows/ci-simple.yml" ]; then
    print_success "Workflow simple disponible - recommandé pour commencer"
else
    print_warning "Créer le workflow simple d'abord"
fi

if [ -f ".github/workflows/ci-cd.yml" ]; then
    print_info "Workflow complet disponible - pour usage avec runner configuré"
else
    print_warning "Workflow complet manquant"
fi

echo ""
echo "RECOMMANDATIONS:"
echo "1. Commencer avec ci-simple.yml (pas de secrets requis)"
echo "2. Configurer les secrets GitHub progressivement"  
echo "3. Installer le runner self-hosted quand prêt"
echo "4. Migrer vers ci-cd.yml pour le déploiement complet"
echo ""

print_success "Diagnostic terminé!"
echo ""
echo "Prochaines étapes:"
echo "- Corriger les erreurs identifiées"
echo "- Tester avec le workflow simple"
echo "- Configurer progressivement l'infrastructure complète"