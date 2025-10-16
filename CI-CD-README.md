# BrokerX Microservices - CI/CD Pipeline

Ce document décrit le pipeline CI/CD pour l'architecture microservices BrokerX utilisant GitHub Actions avec un self-hosted runner.

## Architecture du Pipeline

### 1. Test Job (Ubuntu Latest)
- **Déclencheurs**: Push sur `main`/`master`, Pull Requests
- **Services**: PostgreSQL pour les tests d'intégration
- **Actions**:
  - Configuration JDK 21
  - Cache des dépendances Maven
  - Tests unitaires pour chaque microservice
  - Build de tous les services
  - Upload des artifacts (JAR files)

### 2. Security Scan Job
- **Dépendance**: Job `test`
- **Actions**:
  - Scan OWASP Dependency Check
  - Génération de rapports de sécurité
  - Upload des rapports de sécurité

### 3. Deploy Job (Self-Hosted Runner)
- **Dépendances**: Jobs `test` et `security-scan`
- **Conditions**: Push sur `main`/`master` uniquement
- **Actions**:
  - Download des artifacts
  - Configuration de l'environnement de production
  - Déploiement Docker Compose
  - Health checks
  - Notifications

## Configuration du Self-Hosted Runner

### Prérequis sur la VM
```bash
# Installation Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker gha-runner

# Installation Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Vérification
docker --version
docker-compose --version
```

### Configuration du Runner
1. Aller dans Settings > Actions > Runners
2. Cliquer "New self-hosted runner"
3. Sélectionner Linux x64
4. Suivre les instructions d'installation

```bash
# Sur la VM
mkdir actions-runner && cd actions-runner
curl -o actions-runner-linux-x64-2.311.0.tar.gz -L https://github.com/actions/runner/releases/download/v2.311.0/actions-runner-linux-x64-2.311.0.tar.gz
tar xzf ./actions-runner-linux-x64-2.311.0.tar.gz

# Configuration
./config.sh --url https://github.com/[USERNAME]/[REPO] --token [TOKEN]

# Labels recommandés: self-hosted,linux,vm-lab

# Démarrage en tant que service
sudo ./svc.sh install
sudo ./svc.sh start
```

## Secrets GitHub Required

Configurer ces secrets dans GitHub (Settings > Secrets and variables > Actions):

```
JWT_SECRET=YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU=
EMAIL_USER=your-email@example.com
EMAIL_PASS=your-app-password
GRAFANA_ADMIN_PASSWORD=your-secure-password
```

## Structure des Répertoires sur la VM

```
/home/gha-runner/
├── actions-runner/          # GitHub Actions Runner
├── brokerx-microservices/   # Code source déployé
├── brokerx-data/           # Données persistantes des services
│   ├── authdb.*
│   ├── clientdb.*
│   ├── walletdb.*
│   └── orderdb.*
└── brokerx-backups/        # Sauvegardes automatiques
    └── YYYYMMDD_HHMMSS/
```

## Déploiement

### Déploiement Automatique
Le déploiement se déclenche automatiquement lors d'un push sur `main`/`master`.

### Déploiement Manuel
```bash
# Sur la VM
cd /home/gha-runner/brokerx-microservices
./deploy.sh
```

### Vérification du Déploiement
```bash
# Status des services
docker compose ps

# Logs des services
docker compose logs -f [service-name]

# Health checks
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # AuthService
curl http://localhost:8082/actuator/health  # ClientService
curl http://localhost:8083/actuator/health  # WalletService
curl http://localhost:8084/actuator/health  # OrderService
```

## Monitoring et Observabilité

### URLs de Monitoring
- **Prometheus**: http://[VM-IP]:9090
- **Grafana**: http://[VM-IP]:3000 (admin/[GRAFANA_ADMIN_PASSWORD])

### URLs d'API Documentation
- **Gateway**: http://[VM-IP]:8080/swagger-ui.html
- **AuthService**: http://[VM-IP]:8081/swagger-ui.html
- **ClientService**: http://[VM-IP]:8082/swagger-ui.html
- **WalletService**: http://[VM-IP]:8083/swagger-ui.html
- **OrderService**: http://[VM-IP]:8084/swagger-ui.html

### Métriques Disponibles
- **Health checks**: `/actuator/health`
- **Métriques Prometheus**: `/actuator/prometheus`
- **Info de l'application**: `/actuator/info`

## Dépannage

### Pipeline Fails

1. **Tests Failed**:
   ```bash
   # Vérifier les logs du job test
   # Corriger les tests en échec
   git commit -am "Fix failing tests"
   git push
   ```

2. **Security Scan Failed**:
   ```bash
   # Vérifier les vulnérabilités détectées
   # Mettre à jour les dépendances vulnérables
   ```

3. **Deployment Failed**:
   ```bash
   # Sur la VM, vérifier les logs
   cd /home/gha-runner/brokerx-microservices
   docker compose logs
   
   # Redéployer manuellement
   ./deploy.sh
   ```

### Services Not Starting

```bash
# Vérifier l'espace disque
df -h

# Vérifier les ports
netstat -tlnp | grep :8080

# Nettoyer Docker
docker system prune -a

# Redémarrer les services
docker compose restart
```

### Rollback

```bash
# Restaurer depuis une sauvegarde
cd /home/gha-runner/brokerx-backups/[BACKUP_DATE]
cp -r data_backup/* /home/gha-runner/brokerx-data/

# Redéployer une version précédente
git checkout [PREVIOUS_COMMIT]
./deploy.sh
```

## 📈 Améliorations Futures

1. **Tests d'Intégration**: Ajouter des tests end-to-end automatisés
2. **Blue-Green Deployment**: Implémentation d'un déploiement sans interruption
3. **Auto-scaling**: Configuration d'un auto-scaling basé sur les métriques
4. **Notifications**: Intégration Slack/Teams pour les notifications de déploiement
5. **Database Migration**: Scripts automatisés de migration de base de données

## 🏷️ Tags et Versions

Pour déclencher un déploiement avec tag:
```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

Le pipeline peut être modifié pour déclencher des déploiements sur des tags spécifiques.