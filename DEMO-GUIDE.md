# BrokerX - Guide de Démo Simple

## Prérequis
- Java 21 installé
- Docker Desktop en cours d'exécution
- Ports 8080-8085 et 5173 libres

## Étape 1: Démarrage de l'Application

### Option A: Script Rapide (Recommandé)
```batch
# Windows
start-brokerx-stack.bat
```

### Option B: Docker
```bash
# Démarrer le monitoring
docker-compose -f docker-compose.monitoring.yml up -d

# Démarrer les services
docker-compose up -d
```

## Étape 2: Vérification que Tout Fonctionne

### Test de Santé des Services
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
```

**Résultat attendu:** `{"status":"UP"}` pour tous

### Interface Web
Ouvrir dans le navigateur: http://localhost:5173

## Étape 3: Scénario de Démo Complet

### 3.1 Inscription d'un Utilisateur
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "demo@brokerx.com",
    "password": "DemoPassword123"
  }'
```

### 3.2 Connexion
```bash
curl -X POST http://localhost:8080/api/auth/simple-login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "demo@brokerx.com",
    "password": "DemoPassword123"
  }'
```

**Copier le token JWT retourné pour les étapes suivantes**

### 3.3 Création du Profil Client
```bash
curl -X POST http://localhost:8080/api/clients/register \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nom": "Demo",
    "prenom": "User",
    "email": "demo@brokerx.com"
  }'
```

### 3.4 Dépôt de Fonds
```bash
curl -X POST http://localhost:8080/api/wallet/depot \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "montant": 1000.00
  }'
```

### 3.5 Vérification du Solde
```bash
curl -X GET http://localhost:8080/api/wallet/balance \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 3.6 Achat d'Actions
```bash
curl -X POST http://localhost:8080/api/orders/acheter \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "symbole": "AAPL",
    "quantite": 10,
    "prixUnitaire": 150.00
  }'
```

### 3.7 Vente d'Actions
```bash
curl -X POST http://localhost:8080/api/orders/vendre \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "symbole": "AAPL",
    "quantite": 5,
    "prixUnitaire": 155.00
  }'
```

## Étape 4: Monitoring et Observabilité

### Grafana Dashboard
- URL: http://localhost:3000
- Login: admin / admin
- Voir les métriques en temps réel

### Prometheus Métriques
- URL: http://localhost:9090
- Consulter les métriques des microservices

### Swagger API Documentation
- Gateway: http://localhost:8080/swagger-ui.html
- AuthService: http://localhost:8081/swagger-ui.html
- ClientService: http://localhost:8082/swagger-ui.html
- WalletService: http://localhost:8083/swagger-ui.html
- OrderService: http://localhost:8084/swagger-ui.html

## Étape 5: Tests de Performance (Optionnel)

### Test de Charge Simple
```bash
# Si k6 est installé
k6 run --vus 10 --duration 30s test-scripts/load-test.js
```

### Surveillance en Temps Réel
```bash
# Logs en temps réel
docker-compose logs -f

# Logs d'un service spécifique
docker-compose logs -f gateway
```

## Points de Démonstration Clés

### Architecture Microservices
- 6 microservices indépendants
- Communication via API REST
- Gateway comme point d'entrée unique
- Authentification JWT centralisée

### Fonctionnalités Business
- Inscription/Connexion utilisateur
- Gestion des portefeuilles
- Achat/Vente d'actions
- Matching automatique des ordres

### Observabilité
- Health checks sur tous les services
- Métriques Prometheus
- Dashboards Grafana
- Logs centralisés

### Sécurité
- Authentification JWT
- Validation des autorisations
- Chiffrement des mots de passe

## Arrêt de la Démo

```batch
# Windows
stop-brokerx-stack.bat

# Ou Docker
docker-compose down
docker-compose -f docker-compose.monitoring.yml down
```

## Résolution de Problèmes Rapides

### Service ne Répond Pas
```bash
# Vérifier les logs
docker-compose logs [service-name]

# Redémarrer un service
docker-compose restart [service-name]
```

### Erreur d'Authentification
```bash
# Vérifier JWT_SECRET
echo $env:JWT_SECRET

# Redémarrer tous les services
stop-brokerx-stack.bat
start-brokerx-stack.bat
```

### Ports Occupés
```bash
# Vérifier les ports utilisés
netstat -an | findstr :8080

# Arrêter les processus Java
taskkill /f /im "java.exe"
```

## Durée Estimée de la Démo
- Démarrage: 2-3 minutes
- Scénario complet: 10-15 minutes
- Questions/Réponses: 10 minutes
- **Total: 25-30 minutes**