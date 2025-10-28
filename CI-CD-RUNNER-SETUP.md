# Configuration CI/CD avec GitHub Actions Self-Hosted Runners

## Vue d'ensemble

Ce guide explique comment configurer et valider un runner GitHub Actions self-hosted pour le déploiement automatique des microservices BrokerX.

## Prérequis

### Serveur/VM de Production
- Ubuntu 20.04+ ou CentOS 8+
- 4GB RAM minimum (8GB recommandé)
- 20GB espace disque libre
- Accès Internet (GitHub, Docker Hub)
- Docker et Docker Compose installés

### Accès Administrateur
- Accès sudo sur le serveur
- Permissions pour installer des services système
- Accès au repository GitHub avec permissions Actions

## Étape 1: Préparation du Serveur

### 1.1 Création de l'utilisateur dédié
```bash
# Créer l'utilisateur pour le runner GitHub Actions----
sudo useradd -m -s /bin/bash gha-runner
sudo usermod -aG docker gha-runner

# Créer les répertoires de travail
sudo mkdir -p /home/gha-runner/brokerx-microservices
sudo mkdir -p /home/gha-runner/brokerx-data
sudo mkdir -p /home/gha-runner/brokerx-backups

# Attribuer les permissions
sudo chown -R gha-runner:gha-runner /home/gha-runner/
```

### 1.2 Installation des dépendances
```bash
# Mettre à jour le système
sudo apt update && sudo apt upgrade -y

# Installer les outils nécessaires
sudo apt install -y git curl wget unzip

# Installer Docker (si pas déjà fait)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo systemctl enable docker
sudo systemctl start docker

# Vérifier l'installation
docker --version
docker-compose --version
```

### 1.3 Validation de l'environnement
```bash
# Copier et exécuter le script de validation
chmod +x validate-runner.sh
./validate-runner.sh
```

## Étape 2: Configuration du Runner GitHub Actions

### 2.1 Obtenir le token et URL d'enregistrement
1. Aller sur GitHub → Repository → Settings → Actions → Runners
2. Cliquer "New self-hosted runner"
3. Sélectionner "Linux" 
4. Copier les commandes d'installation

### 2.2 Installation du runner
```bash
# Se connecter en tant qu'utilisateur runner
sudo su - gha-runner

# Créer le répertoire du runner
mkdir actions-runner && cd actions-runner

# Télécharger le runner (remplacer par la version actuelle)
curl -o actions-runner-linux-x64-2.311.0.tar.gz -L https://github.com/actions/runner/releases/download/v2.311.0/actions-runner-linux-x64-2.311.0.tar.gz

# Extraire
tar xzf ./actions-runner-linux-x64-2.311.0.tar.gz

# Configurer le runner avec les informations GitHub
./config.sh --url https://github.com/VOTRE_ORG/VOTRE_REPO --token VOTRE_TOKEN

# Répondre aux questions:
# - Runner name: vm-lab-runner
# - Runner group: Default
# - Labels: self-hosted,linux,vm-lab
# - Work folder: _work
```

### 2.3 Installation du service système
```bash
# Installer le runner comme service système
sudo ./svc.sh install

# Démarrer le service
sudo ./svc.sh start

# Vérifier le statut
sudo ./svc.sh status
```

## Étape 3: Configuration des Secrets GitHub

### 3.1 Secrets requis dans le repository GitHub
Aller sur GitHub → Repository → Settings → Secrets and variables → Actions

Ajouter les secrets suivants:

```bash
# JWT Secret (OBLIGATOIRE - même valeur partout)
JWT_SECRET: YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU=

# Configuration Email (pour notifications)
EMAIL_USER: votre-email@gmail.com
EMAIL_PASS: votre-mot-de-passe-app

# Mot de passe Grafana Admin
GRAFANA_ADMIN_PASSWORD: admin123

# Optionnel: Configuration base de données production
DB_PASSWORD: votre-db-password
```

### 3.2 Variables d'environnement
Dans GitHub → Repository → Settings → Secrets and variables → Actions → Variables:

```bash
DEPLOY_SERVER_IP: votre-ip-serveur
MONITORING_PORT: 3000
PROMETHEUS_PORT: 9090
```

## Étape 4: Test du Pipeline CI/CD

### 4.1 Test de validation
```bash
# Sur le serveur, vérifier les logs du runner
sudo journalctl -u actions.runner.* -f

# Créer un commit de test pour déclencher le pipeline
git add .
git commit -m "Test CI/CD pipeline configuration"
git push origin main
```

### 4.2 Surveillance du déploiement
```bash
# Surveiller l'exécution
# 1. GitHub Actions tab pour voir les logs
# 2. Sur le serveur pour les logs Docker
docker-compose logs -f

# Vérifier les services après déploiement
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

## Étape 5: Configuration du Monitoring

### 5.1 Accès aux dashboards
```bash
# Grafana
URL: http://VOTRE_IP:3000
Login: admin
Password: (valeur de GRAFANA_ADMIN_PASSWORD)

# Prometheus
URL: http://VOTRE_IP:9090
```

### 5.2 Alerting (Optionnel)
```bash
# Configuration des alertes Grafana
# 1. Connecter à Grafana
# 2. Alerting → Notification channels
# 3. Configurer email/Slack/Teams
```

## Étape 6: Maintenance et Troubleshooting

### 6.1 Commandes de maintenance
```bash
# Redémarrer le runner
sudo systemctl restart actions.runner.*

# Voir les logs du runner
sudo journalctl -u actions.runner.* -n 50

# Nettoyer les anciens builds
docker system prune -f
docker volume prune -f

# Sauvegarde des données
tar -czf backup-$(date +%Y%m%d).tar.gz /home/gha-runner/brokerx-data/
```

### 6.2 Problèmes courants

#### Runner ne se connecte pas
```bash
# Vérifier le statut
sudo systemctl status actions.runner.*

# Redémarrer le service
sudo systemctl restart actions.runner.*

# Vérifier la connectivité GitHub
ping github.com
```

#### Échec de déploiement
```bash
# Vérifier les logs Docker
docker-compose logs

# Vérifier l'espace disque
df -h

# Vérifier les ports occupés
netstat -tulpn | grep :8080
```

#### Services ne démarrent pas
```bash
# Vérifier les variables d'environnement
docker-compose config

# Redémarrer tous les services
docker-compose down && docker-compose up -d

# Vérifier les health checks
make health
```

## Étape 7: Sécurisation

### 7.1 Firewall
```bash
# Configurer UFW (Ubuntu)
sudo ufw allow ssh
sudo ufw allow 8080
sudo ufw allow 3000
sudo ufw allow 9090
sudo ufw enable
```

### 7.2 SSL/TLS (Production)
```bash
# Installer Nginx reverse proxy avec Let's Encrypt
sudo apt install nginx certbot python3-certbot-nginx

# Configurer les certificats SSL
sudo certbot --nginx -d votre-domaine.com
```

## Checklist de Validation

### Avant mise en production
- [ ] Runner GitHub Actions configuré et actif
- [ ] Tous les secrets GitHub configurés
- [ ] Script validate-runner.sh passe tous les tests
- [ ] Pipeline CI/CD s'exécute sans erreur
- [ ] Tous les microservices démarrent correctement
- [ ] Health checks passent
- [ ] Monitoring accessible (Grafana/Prometheus)
- [ ] Sauvegarde automatique configurée
- [ ] Firewall configuré
- [ ] Documentation à jour

### Après déploiement
- [ ] Tests de fumée passent
- [ ] Monitoring affiche des métriques
- [ ] Logs accessibles et cohérents
- [ ] Performance acceptable
- [ ] Alerting fonctionnel

## Support et Documentation

### Logs importants
```bash
# Logs du runner GitHub Actions
sudo journalctl -u actions.runner.* -f

# Logs des microservices
docker-compose logs -f [service-name]

# Logs système
sudo tail -f /var/log/syslog
```

### Contacts
- Documentation technique: OBSERVABILITY.md
- Guide de démo: DEMO-GUIDE.md
- Runbook opérationnel: RUNBOOK.md

### Ressources externes
- GitHub Actions Documentation
- Docker Compose Documentation  
- Spring Boot Actuator Documentation