# BrokerX Microservices - Runbook Essentiel

## 📋 Vue d'ensemble

Procédures essentielles pour démarrer, surveiller et dépanner les microservices BrokerX.

**Services :**
- Gateway (8080) - Point d'entrée principal
- AuthService (8081) - Authentification
- ClientService (8082) - Gestion clients
- WalletService (8083) - Portefeuilles
- OrderService (8084) - Ordres de trading

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
# 1. Gateway (OBLIGATOIRE EN PREMIER)
cd gatewayService && mvnw spring-boot:run

# 2. Services (ordre flexible)
cd authService/authService && mvnw spring-boot:run
cd clientService/clientService && mvnw spring-boot:run
cd walletService/walletService && mvnw spring-boot:run
cd orderService/orderService && mvnw spring-boot:run
```

### Option 3: Docker
```bash
docker-compose up -d
```

## 🏥 Vérification de la Santé

### URLs Essentielles
- **Gateway**: http://localhost:8080/actuator/health
- **AuthService**: http://localhost:8081/actuator/health
- **ClientService**: http://localhost:8082/actuator/health
- **WalletService**: http://localhost:8083/actuator/health
- **OrderService**: http://localhost:8084/actuator/health

### Vérification Rapide
```bash
# Test tous les services
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

**Réponse attendue :** `{"status":"UP"}`

## 🔧 Dépannage Rapide

### Problème 1: Service ne Démarre Pas

**Vérifier :**
```bash
# Port occupé ?
netstat -an | findstr :8080

# Logs d'erreur ?
# Regarder la console où le service a été lancé
```

**Solutions :**
```bash
# Tuer processus sur port
taskkill /F /PID [PID_NUMBER]

# Ou redémarrer avec un autre port
server.port=8085
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

## 💾 Sauvegarde Simple

### Sauvegarder les Données
```bash
# Créer dossier de sauvegarde
mkdir backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%

# Copier les données H2
xcopy data backup_%date:~-4,4%%date:~-10,2%%date:~-7,2%\data /E /I
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

## 🔄 Redémarrage des Services

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

## 📊 URLs Utiles

### Documentation API (Swagger)
- **Gateway**: http://localhost:8080/swagger-ui.html
- **AuthService**: http://localhost:8081/swagger-ui.html
- **ClientService**: http://localhost:8082/swagger-ui.html
- **WalletService**: http://localhost:8083/swagger-ui.html
- **OrderService**: http://localhost:8084/swagger-ui.html

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
- **Équipe DevOps**: devops@brokerx.com
- **Documentation**: Consulter OBSERVABILITY.md pour plus de détails

## 📚 Commandes Essentielles

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

---

**📝 Note :** Gardez ce runbook à portée de main. Pour plus de détails, consultez OBSERVABILITY.md