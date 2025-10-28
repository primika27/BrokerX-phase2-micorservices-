# BrokerX Microservices - Runbook Essentiel

## Vue d'ensemble

Procédures essentielles pour démarrer, surveiller et dépanner les microservices BrokerX.

**Services :**
- Gateway (8080) - Point d'entrée principal
- AuthService (8081) - Authentification
- ClientService (8082) - Gestion clients
- WalletService (8083) - Portefeuilles
- OrderService (8084) - Ordres de trading
- MatchingService (8085) - Matching des ordres
- Frontend (5173) - Interface utilisateur

## Démarrage des Services

### Option 1: Script Automatique (Recommandé)
```batch
# Windows
start-brokerx-stack.bat

# Linux/Mac
./deploy.sh
```

### Option 2: Démarrage Manuel
```bash
# 1. Services backend (tous requis avant Gateway)
cd authService/authService && mvnw spring-boot:run
cd clientService/clientService && mvnw spring-boot:run  
cd walletService/walletService && mvnw spring-boot:run
cd orderService/orderService && mvnw spring-boot:run
cd matchingService/matchingService && mvnw spring-boot:run

# 2. Gateway (EN DERNIER)
cd gatewayService && mvnw spring-boot:run
```

### Option 3: Docker (Production)
```bash
# Démarrage avec monitoring
docker-compose -f docker-compose.monitoring.yml up -d
docker-compose up -d

# Ou utiliser le Makefile
make start
```

## Vérification de la Santé

### URLs Essentielles
- **Gateway**: http://localhost:8080/actuator/health
- **AuthService**: http://localhost:8081/actuator/health
- **ClientService**: http://localhost:8082/actuator/health
- **WalletService**: http://localhost:8083/actuator/health
- **OrderService**: http://localhost:8084/actuator/health
- **MatchingService**: http://localhost:8085/actuator/health
- **Frontend**: http://localhost:5173

### Vérification Rapide
```bash
# Test tous les services
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
```

**Réponse attendue :** `{"status":"UP"}`

## Dépannage Rapide

### Problème 1: Service ne Démarre Pas

**Vérifier :**
```bash
# Port occupé ? (Windows)
netstat -an | findstr :8080

# Processus Java actifs
tasklist | findstr java

# Logs Docker (si mode conteneur)
docker-compose logs [service-name]
```

**Solutions :**
```bash
# Tuer processus sur port (Windows)
taskkill /F /PID [PID_NUMBER]

# Tuer tous les processus Java
taskkill /f /im "java.exe"

# Redémarrer avec Docker
docker-compose restart [service-name]
```

### Problème 2: Erreur 500

**Vérifier :**
```bash
# JWT_SECRET configuré ?
echo %JWT_SECRET%

# Base de données accessible ?
dir data\
```

**Solutions :**
```bash
# Configurer JWT_SECRET
set JWT_SECRET=YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU=

# Supprimer base corrompue
del data\*.db
```

### Problème 3: Gateway ne Route Pas

**Test :**
```bash
# Direct
curl http://localhost:8081/actuator/health

# Via Gateway
curl http://localhost:8080/api/auth/actuator/health
```

**Solution :** Vérifier que tous les services sont UP avant le Gateway.

### Problème 4: Erreurs d'Authentification JWT

**Test :**
```bash
# Vérifier JWT_SECRET
echo $env:JWT_SECRET  # Windows PowerShell
echo %JWT_SECRET%     # Windows CMD

# Test d'authentification
curl -X POST http://localhost:8080/api/auth/simple-login -H "Content-Type: application/json" -d "{\"email\":\"test@test.com\",\"password\":\"password\"}"
```

**Solutions :**
```bash
# Configurer JWT_SECRET (même pour tous les services)
set JWT_SECRET=YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU=

# Redémarrer tous les services après configuration
stop-brokerx-stack.bat && start-brokerx-stack.bat
```

### Problème 5: Database H2 Corrompue

**Symptômes :** Erreurs de démarrage, données incohérentes

**Solution :**
```bash
# Arrêter les services
stop-brokerx-stack.bat

# Supprimer les bases H2 corrompues
del data\*.db
rmdir /s data

# Redémarrer (bases seront recréées)
start-brokerx-stack.bat
```

## Outils de Monitoring et Observabilité

### Commandes Make Utiles
```bash
# Vérifier santé de tous les services
make health

# Voir les logs en temps réel
make logs

# Redémarrer un service spécifique
make restart-service SERVICE=auth-service

# Statut de tous les conteneurs
make status
```

### Tests de Performance
```bash
# Test de charge simple avec k6 (si installé)
k6 run test-scripts/load-test.js

# Monitoring en temps réel
# Grafana: http://localhost:3000 (admin/admin)
# Prometheus: http://localhost:9090
```

## Sauvegarde Simple

### Sauvegarder les Données
```bash
# Sauvegarde automatique avec Makefile
make backup

# Sauvegarde manuelle (Windows)
mkdir backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%
xcopy data backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%\data /E /I

# Sauvegarde Docker volumes
docker-compose down
docker run --rm -v brokerx_data:/data -v %cd%:/backup alpine tar czf /backup/backup.tar.gz -C /data .
```

### Restauration
```bash
# Arrêter les services
stop-brokerx-stack.bat

# Restaurer les données
rmdir /s data
xcopy backup_YYYYMMDD\data data /E /I

# Redémarrer
start-brokerx-stack.bat
```

## Configuration des Variables d'Environnement

### Variables Obligatoires
```bash
# JWT Secret (CRITIQUE - même valeur pour tous les services)
set JWT_SECRET=YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU=

# Profile Spring Boot
set SPRING_PROFILES_ACTIVE=dev

# Ports par défaut (modifiables si conflits)
# Gateway: 8080, Auth: 8081, Client: 8082, Wallet: 8083, Order: 8084, Matching: 8085
```

### Fichier .env (Docker)
```env
JWT_SECRET=YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU=
SPRING_PROFILES_ACTIVE=docker
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password
POSTGRES_DB=brokerx
```

### Vérification Configuration
```bash
# Vérifier JWT_SECRET sur tous les services
curl http://localhost:8081/actuator/env | findstr JWT_SECRET
curl http://localhost:8082/actuator/env | findstr JWT_SECRET
curl http://localhost:8083/actuator/env | findstr JWT_SECRET
```

## Commandes de Diagnostic Avancé

### Logs Détaillés
```bash
# Logs par service avec horodatage
docker-compose logs -f --timestamps gateway
docker-compose logs -f --timestamps auth-service

# Logs filtrés par niveau
docker-compose logs gateway | findstr ERROR
docker-compose logs auth-service | findstr WARN
```

### Métriques système
```bash
# Utilisation CPU/Mémoire des conteneurs
docker stats

# Inspection détaillée d'un conteneur
docker inspect brokerx_gateway_1

# Variables d'environnement d'un conteneur
docker exec brokerx_gateway_1 env | findstr JWT
```

### Tests de Connectivité Réseau
```bash
# Test ping entre conteneurs
docker exec brokerx_gateway_1 ping auth-service
docker exec brokerx_gateway_1 ping client-service

# Test des ports internes
docker exec brokerx_gateway_1 telnet auth-service 8081
```

## Redémarrage des Services

### Redémarrage Complet
```bash
# Arrêter
stop-brokerx-stack.bat

# Attendre 10 secondes
# Redémarrer
start-brokerx-stack.bat
```

### Redémarrage d'un Service Spécifique
```bash
# Trouver le processus
tasklist | findstr java

# Arrêter le processus
taskkill /F /PID [PID]

# Redémarrer le service
cd [serviceDirectory]
mvnw spring-boot:run
```

## URLs Utiles

### Documentation API (Swagger)
- **Gateway**: http://localhost:8080/swagger-ui.html
- **AuthService**: http://localhost:8081/swagger-ui.html
- **ClientService**: http://localhost:8082/swagger-ui.html
- **WalletService**: http://localhost:8083/swagger-ui.html
- **OrderService**: http://localhost:8084/swagger-ui.html
- **MatchingService**: http://localhost:8085/swagger-ui.html

### Monitoring (si activé)
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

## � En Cas de Problème Majeur

### Étapes d'Urgence
1. **Arrêter tous les services**
   ```bash
   stop-brokerx-stack.bat
   ```

2. **Sauvegarder les données actuelles**
   ```bash
   xcopy data backup_emergency_%time:~0,2%%time:~3,2% /E /I
   ```

3. **Restaurer la dernière sauvegarde connue**
   ```bash
   # Voir section Sauvegarde Simple
   ```

4. **Redémarrer les services**
   ```bash
   start-brokerx-stack.bat
   ```

### Contact d'Urgence
- **Documentation**: Consulter OBSERVABILITY.md pour plus de détails

## Commandes Essentielles

### Démarrage/Arrêt
```bash
# Démarrer tout
start-brokerx-stack.bat

# Arrêter tout
stop-brokerx-stack.bat

# Vérifier si les services fonctionnent
curl http://localhost:8080/actuator/health
```

### En Cas de Problème
```bash
# Voir les processus Java
tasklist | findstr java

# Arrêter un processus spécifique
taskkill /F /PID [PID_NUMBER]

# Vérifier les ports utilisés
netstat -an | findstr :808
```

## Sécurité et Bonnes Pratiques

### Checklist de Sécurité
- [ ] JWT_SECRET configuré et identique sur tous les services
- [ ] Profils Spring Boot corrects (dev/docker/prod)
- [ ] Ports exposés uniquement quand nécessaire
- [ ] Health checks activés sur tous les services
- [ ] Logs configurés sans exposer de secrets

### Variables Sensibles à Protéger
```bash
# Ne jamais logger en clair :
JWT_SECRET, POSTGRES_PASSWORD, API_KEYS

# Utiliser des profils pour différencier :
dev: logs détaillés, sécurité relâchée
prod: logs minimaux, sécurité renforcée
```

## Checklist de Déploiement

### Avant Déploiement
- [ ] Tous les services compilent sans erreur
- [ ] Tests unitaires passent (`make test-unit`)
- [ ] Variables d'environnement configurées
- [ ] Sauvegarde des données existantes créée
- [ ] Health checks fonctionnels

### Après Déploiement
- [ ] Tous les services sont UP (status code 200)
- [ ] Gateway route correctement vers tous les services
- [ ] Authentification JWT fonctionne
- [ ] Monitoring accessible (Grafana/Prometheus)
- [ ] Tests de fumée passent

### Tests de Fumée Rapides
```bash
# 1. Health checks
make health

# 2. Test d'authentification
curl -X POST http://localhost:8080/api/auth/simple-login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"password"}'

# 3. Test de routage Gateway
curl http://localhost:8080/api/auth/actuator/health
curl http://localhost:8080/api/clients/actuator/health
curl http://localhost:8080/api/wallet/actuator/health
curl http://localhost:8080/api/orders/actuator/health
```

## Contacts et Support

### Documentation Complémentaire
- **Observabilité détaillée**: `OBSERVABILITY.md`
- **Pipeline CI/CD**: `CI-CD-README.md`
- **API Swagger**: http://localhost:8080/swagger-ui.html

### Logs d'Audit
```bash
# Historique des déploiements
docker-compose logs --timestamps | findstr "Started"

# Historique des erreurs
docker-compose logs --timestamps | findstr "ERROR\|FATAL"
```

---

**Note Importante :** 
- Gardez ce runbook à jour avec les changements d'architecture
- Testez régulièrement les procédures de récupération
- Documentez les nouveaux problèmes rencontrés et leurs solutions
- Pour plus de détails techniques, consultez `OBSERVABILITY.md`

**Version du Runbook :** 2.1 - Mis à jour le $(date)