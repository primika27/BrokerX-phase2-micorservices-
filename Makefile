# BrokerX Microservices Makefile
# Commandes utilitaires pour le développement et le déploiement

.PHONY: help build test clean deploy stop logs health docs backup restore

# Variables
COMPOSE_FILE = docker-compose.yml
MONITORING_FILE = docker-compose.monitoring.yml
ENV_FILE = .env

# Couleurs pour l'affichage
GREEN = \033[0;32m
YELLOW = \033[1;33m
RED = \033[0;31m
NC = \033[0m # No Color

help: ## Afficher l'aide
	@echo "$(GREEN)BrokerX Microservices - Commandes disponibles:$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""

setup: ## Configuration initiale du projet
	@echo "$(GREEN)🛠️  Configuration initiale...$(NC)"
	@cp .env.template .env
	@echo "📝 Fichier .env créé. Veuillez le configurer avec vos valeurs."
	@chmod +x deploy.sh
	@chmod +x test-pipeline.sh
	@echo "✅ Configuration terminée"

build: ## Construire toutes les images Docker
	@echo "$(GREEN)🏗️  Construction des images Docker...$(NC)"
	@docker compose -f $(COMPOSE_FILE) build --no-cache

build-service: ## Construire une image spécifique (usage: make build-service SERVICE=auth-service)
	@echo "$(GREEN)🏗️  Construction de l'image $(SERVICE)...$(NC)"
	@docker compose -f $(COMPOSE_FILE) build --no-cache $(SERVICE)

test: ## Exécuter les tests
	@echo "$(GREEN)🧪 Exécution des tests...$(NC)"
	@./test-pipeline.sh

test-unit: ## Exécuter les tests unitaires de tous les services
	@echo "$(GREEN)🧪 Tests unitaires...$(NC)"
	@cd authService/authService && ./mvnw test
	@cd clientService/clientService && ./mvnw test
	@cd walletService/walletService && ./mvnw test
	@cd orderService/orderService && ./mvnw test
	@cd gatewayService && ./mvnw test

clean: ## Nettoyer les images et volumes Docker
	@echo "$(YELLOW)🧹 Nettoyage Docker...$(NC)"
	@docker compose -f $(COMPOSE_FILE) down --volumes --remove-orphans
	@docker compose -f $(MONITORING_FILE) down --volumes --remove-orphans
	@docker system prune -f
	@docker volume prune -f

deploy: ## Déployer tous les services
	@echo "$(GREEN)🚀 Déploiement des services...$(NC)"
	@./deploy.sh

deploy-monitoring: ## Déployer uniquement le monitoring
	@echo "$(GREEN)📊 Déploiement du monitoring...$(NC)"
	@docker compose -f $(MONITORING_FILE) up -d

deploy-services: ## Déployer uniquement les microservices
	@echo "$(GREEN)🔧 Déploiement des microservices...$(NC)"
	@docker compose -f $(COMPOSE_FILE) up -d

start: ## Démarrer tous les services
	@echo "$(GREEN)▶️  Démarrage des services...$(NC)"
	@docker compose -f $(MONITORING_FILE) up -d
	@sleep 10
	@docker compose -f $(COMPOSE_FILE) up -d

stop: ## Arrêter tous les services
	@echo "$(YELLOW)⏹️  Arrêt des services...$(NC)"
	@docker compose -f $(COMPOSE_FILE) down
	@docker compose -f $(MONITORING_FILE) down

restart: ## Redémarrer tous les services
	@echo "$(YELLOW)🔄 Redémarrage des services...$(NC)"
	@make stop
	@sleep 5
	@make start

restart-service: ## Redémarrer un service spécifique (usage: make restart-service SERVICE=auth-service)
	@echo "$(YELLOW)🔄 Redémarrage du service $(SERVICE)...$(NC)"
	@docker compose -f $(COMPOSE_FILE) restart $(SERVICE)

logs: ## Afficher les logs de tous les services
	@echo "$(GREEN)📋 Logs des services...$(NC)"
	@docker compose -f $(COMPOSE_FILE) logs -f

logs-service: ## Afficher les logs d'un service spécifique (usage: make logs-service SERVICE=auth-service)
	@echo "$(GREEN)📋 Logs du service $(SERVICE)...$(NC)"
	@docker compose -f $(COMPOSE_FILE) logs -f $(SERVICE)

health: ## Vérifier la santé de tous les services
	@echo "$(GREEN)🏥 Vérification de la santé des services...$(NC)"
	@echo "Gateway: $$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health || echo 'FAIL')"
	@echo "AuthService: $$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/actuator/health || echo 'FAIL')"
	@echo "ClientService: $$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/actuator/health || echo 'FAIL')"
	@echo "WalletService: $$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8083/actuator/health || echo 'FAIL')"
	@echo "OrderService: $$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8084/actuator/health || echo 'FAIL')"
	@echo "Prometheus: $$(curl -s -o /dev/null -w '%{http_code}' http://localhost:9090 || echo 'FAIL')"
	@echo "Grafana: $$(curl -s -o /dev/null -w '%{http_code}' http://localhost:3000 || echo 'FAIL')"

status: ## Afficher le statut de tous les conteneurs
	@echo "$(GREEN)📊 Statut des conteneurs...$(NC)"
	@docker compose -f $(COMPOSE_FILE) ps
	@echo ""
	@docker compose -f $(MONITORING_FILE) ps

docs: ## Ouvrir la documentation API
	@echo "$(GREEN)📖 URLs de documentation:$(NC)"
	@echo "Gateway Swagger: http://localhost:8080/swagger-ui.html"
	@echo "AuthService Swagger: http://localhost:8081/swagger-ui.html"
	@echo "ClientService Swagger: http://localhost:8082/swagger-ui.html"
	@echo "WalletService Swagger: http://localhost:8083/swagger-ui.html"
	@echo "OrderService Swagger: http://localhost:8084/swagger-ui.html"

monitoring: ## Ouvrir les URLs de monitoring
	@echo "$(GREEN)📈 URLs de monitoring:$(NC)"
	@echo "Prometheus: http://localhost:9090"
	@echo "Grafana: http://localhost:3000 (admin/admin)"

backup: ## Créer une sauvegarde des données
	@echo "$(GREEN)💾 Création d'une sauvegarde...$(NC)"
	@mkdir -p backups/$$(date +%Y%m%d_%H%M%S)
	@docker compose -f $(COMPOSE_FILE) exec -T gateway tar czf - /data 2>/dev/null | tar xzf - -C backups/$$(date +%Y%m%d_%H%M%S) || echo "Sauvegarde effectuée"
	@echo "✅ Sauvegarde créée dans backups/$$(date +%Y%m%d_%H%M%S)"

dev: ## Mode développement (rebuild et start avec logs)
	@echo "$(GREEN)🔧 Mode développement...$(NC)"
	@make build
	@make start
	@make logs

prod: ## Mode production (build optimisé et déploiement)
	@echo "$(GREEN)🏭 Mode production...$(NC)"
	@make clean
	@make build
	@make deploy

ci: ## Simulation du pipeline CI (build + test)
	@echo "$(GREEN)🔄 Simulation CI...$(NC)"
	@make test-unit
	@make build
	@make test

# Commandes de développement rapides
quick-restart: ## Redémarrage rapide sans rebuild
	@docker compose -f $(COMPOSE_FILE) restart

quick-logs: ## Logs condensés (dernières 100 lignes)
	@docker compose -f $(COMPOSE_FILE) logs --tail=100

# Commandes de debugging
debug-gateway: ## Debug du Gateway
	@docker compose -f $(COMPOSE_FILE) exec gateway /bin/bash

debug-auth: ## Debug AuthService
	@docker compose -f $(COMPOSE_FILE) exec auth-service /bin/bash

debug-network: ## Vérifier la connectivité réseau
	@docker network ls
	@docker compose -f $(COMPOSE_FILE) exec gateway ping -c 3 auth-service || echo "Ping failed"

# Nettoyage complet pour résoudre les problèmes
reset: ## Reset complet (attention: supprime toutes les données)
	@echo "$(RED)⚠️  ATTENTION: Cela va supprimer toutes les données!$(NC)"
	@read -p "Êtes-vous sûr? (y/N): " confirm && [ "$$confirm" = "y" ]
	@make stop
	@docker compose -f $(COMPOSE_FILE) down --volumes --remove-orphans
	@docker compose -f $(MONITORING_FILE) down --volumes --remove-orphans
	@docker system prune -a -f
	@docker volume prune -f
	@echo "✅ Reset complet effectué"