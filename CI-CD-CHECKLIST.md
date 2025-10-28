# Checklist CI/CD GitHub Actions - BrokerX Microservices

## Statut de Configuration

### 1. Repository GitHub
- [ ] Repository créé et accessible
- [ ] Branche principale (main/master) configurée
- [ ] Actions GitHub activées
- [ ] Workflow ci-cd.yml présent dans .github/workflows/

### 2. Secrets GitHub Repository
Aller sur GitHub → Repository → Settings → Secrets and variables → Actions

#### Secrets obligatoires à configurer:
```
JWT_SECRET: YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU=
EMAIL_USER: votre-email@gmail.com
EMAIL_PASS: votre-mot-de-passe-application
GRAFANA_ADMIN_PASSWORD: admin123
```

#### Vérification des secrets:
- [ ] JWT_SECRET configuré (longueur 88 caractères base64)
- [ ] EMAIL_USER configuré (format email valide) 
- [ ] EMAIL_PASS configuré (mot de passe d'application Gmail/Outlook)
- [ ] GRAFANA_ADMIN_PASSWORD configuré (minimum 8 caractères)

### 3. Self-Hosted Runner
- [ ] Serveur Linux disponible (Ubuntu 20.04+)
- [ ] Utilisateur gha-runner créé
- [ ] Docker installé et configuré
- [ ] Runner GitHub Actions installé
- [ ] Runner enregistré avec labels: [self-hosted, linux, vm-lab]
- [ ] Service runner actif (sudo systemctl status actions.runner.*)

### 4. Environnement Serveur

#### Répertoires requis:
- [ ] /home/gha-runner/brokerx-microservices (déploiement)
- [ ] /home/gha-runner/brokerx-data (données)
- [ ] /home/gha-runner/brokerx-backups (sauvegardes)

#### Ports requis libres:
- [ ] 8080 (Gateway)
- [ ] 8081 (AuthService) 
- [ ] 8082 (ClientService)
- [ ] 8083 (WalletService)
- [ ] 8084 (OrderService)
- [ ] 8085 (MatchingService)
- [ ] 5173 (Frontend)
- [ ] 9090 (Prometheus)
- [ ] 3000 (Grafana)

#### Services système:
- [ ] Docker daemon actif
- [ ] Runner GitHub Actions actif
- [ ] Connexion Internet vers GitHub
- [ ] Connexion Internet vers Docker Hub

### 5. Validation Environnement

#### Script de validation:
```bash
# Exécuter le script de validation
./validate-runner.sh

# Vérifier tous les points sont verts
# Corriger les points rouges avant de continuer
```

#### Test du pipeline:
```bash
# Exécuter le test local du pipeline  
./test-cicd-pipeline.sh

# Vérifier que tous les tests passent
```

### 6. Test de Déploiement

#### Premier déploiement:
- [ ] Commit + Push sur branche main/master
- [ ] GitHub Actions se déclenche automatiquement
- [ ] Job 'test' réussit (tous les tests unitaires passent)
- [ ] Job 'security-scan' réussit (pas de vulnérabilités critiques)
- [ ] Job 'deploy' réussit (déploiement sur le serveur)

#### Vérification post-déploiement:
```bash
# Health checks
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health  
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health

# Réponse attendue pour tous: {"status":"UP"}
```

#### Accès aux interfaces:
- [ ] Application: http://localhost:5173
- [ ] API Gateway: http://localhost:8080
- [ ] Grafana: http://localhost:3000 (admin/GRAFANA_ADMIN_PASSWORD)
- [ ] Prometheus: http://localhost:9090

### 7. Monitoring et Logging

#### Logs GitHub Actions:
- [ ] Logs détaillés accessibles dans GitHub Actions tab
- [ ] Pas d'erreurs dans les étapes de build
- [ ] Pas d'erreurs dans les étapes de test  
- [ ] Pas d'erreurs dans les étapes de deploy

#### Logs serveur:
```bash
# Logs du runner
sudo journalctl -u actions.runner.* -f

# Logs des conteneurs
docker-compose logs -f

# Logs d'un service spécifique
docker-compose logs -f gateway
```

### 8. Sécurité

#### Configuration firewall:
- [ ] Ports nécessaires ouverts uniquement
- [ ] SSH sécurisé (clés, pas de root)
- [ ] Accès limité au serveur de production

#### Secrets et credentials:
- [ ] Pas de secrets hardcodés dans le code
- [ ] JWT_SECRET identique sur tous les services
- [ ] Mots de passe forts pour Grafana
- [ ] Credentials email sécurisés

### 9. Sauvegarde et Récupération

#### Stratégie de sauvegarde:
- [ ] Sauvegarde automatique des données avant déploiement
- [ ] Scripts de restauration disponibles
- [ ] Test de restauration effectué

#### Rollback:
```bash
# En cas de problème, rollback rapide:
cd /home/gha-runner/brokerx-microservices
docker-compose down
# Restaurer la sauvegarde précédente
# Redémarrer les services
```

### 10. Documentation

- [ ] RUNBOOK.md à jour
- [ ] DEMO-GUIDE.md testé
- [ ] CI-CD-RUNNER-SETUP.md suivi
- [ ] Cette checklist complétée

## Commandes de Validation Rapide

### Vérification complète:
```bash
# 1. Statut du runner
sudo systemctl status actions.runner.*

# 2. Santé des services
make health

# 3. Logs en temps réel
docker-compose logs -f --tail=50

# 4. Ressources système
df -h
free -h
docker system df
```

### Test complet de bout-en-bout:
```bash
# 1. Test d'authentification
curl -X POST http://localhost:8080/api/auth/simple-login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"password"}'

# 2. Test de routage via Gateway
curl http://localhost:8080/api/auth/actuator/health
curl http://localhost:8080/api/clients/actuator/health
curl http://localhost:8080/api/wallet/actuator/health
curl http://localhost:8080/api/orders/actuator/health

# 3. Test du monitoring
curl http://localhost:9090/-/healthy
curl http://localhost:3000/api/health
```

## Résolution de Problèmes Fréquents

### Runner ne se connecte pas:
```bash
sudo systemctl restart actions.runner.*
sudo journalctl -u actions.runner.* -n 50
```

### Services ne démarrent pas:
```bash
docker-compose down
docker-compose up -d
docker-compose logs
```

### Tests échouent:
```bash
# Vérifier les variables d'environnement
echo $JWT_SECRET
# Redémarrer avec les bonnes variables
```

### Problèmes de permissions:
```bash
sudo chown -R gha-runner:gha-runner /home/gha-runner/
sudo usermod -aG docker gha-runner
```

## Validation Finale

✅ Tous les points de cette checklist sont cochés
✅ Pipeline GitHub Actions s'exécute sans erreur  
✅ Tous les microservices sont UP et répondent
✅ Monitoring opérationnel (Grafana + Prometheus)
✅ Documentation complète et à jour

**CI/CD prêt pour la production!**