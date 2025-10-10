# BrokerX Microservices - Observability Stack

## Vue d'ensemble

Cette documentation couvre la stack d'observabilité complète mise en place pour la plateforme de trading BrokerX, incluant la documentation API (OpenAPI/Swagger), les métriques (Prometheus), et le monitoring (Grafana).

## Architecture d'observabilité

```
┌─────────────────────────────────────────────────────────────────┐
│                    BrokerX Observability Stack                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐     │
│  │   Gateway    │    │     Auth     │    │   Client     │     │
│  │   :8080      │    │   Service    │    │   Service    │     │
│  │              │    │   :8081      │    │   :8082      │     │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘     │
│         │                   │                   │             │
│  ┌──────────────┐    ┌──────────────┐          │             │
│  │   Wallet     │    │    Order     │          │             │
│  │   Service    │    │   Service    │          │             │
│  │   :8083      │    │   :8084      │          │             │
│  └──────┬───────┘    └──────┬───────┘          │             │
│         │                   │                  │             │
│         └───────────────────┼──────────────────┘             │
│                             │                                │
│         ┌─────────────────────────────────────────┐          │
│         │            Observability Layer          │          │
│         ├─────────────────────────────────────────┤          │
│         │                                         │          │
│         │  ┌─────────────┐    ┌─────────────┐    │          │
│         │  │ Prometheus  │───▶│   Grafana   │    │          │
│         │  │   :9090     │    │    :3000    │    │          │
│         │  └─────────────┘    └─────────────┘    │          │
│         │                                         │          │
│         │  ┌─────────────────────────────────┐    │          │
│         │  │      OpenAPI/Swagger Docs      │    │          │
│         │  │    (Available on each service)  │    │          │
│         │  └─────────────────────────────────┘    │          │
│         └─────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

## Services et Ports

### Microservices BrokerX
- **Gateway Service**: `http://localhost:8080` - Point d'entrée principal
- **Auth Service**: `http://localhost:8081` - Service d'authentification
- **Client Service**: `http://localhost:8082` - Gestion des clients
- **Wallet Service**: `http://localhost:8083` - Gestion des portefeuilles
- **Order Service**: `http://localhost:8084` - Gestion des ordres

### Stack d'Observabilité
- **Prometheus**: `http://localhost:9090` - Collecte des métriques
- **Grafana**: `http://localhost:3000` - Visualisation et dashboards (admin/admin)

## Documentation API (OpenAPI/Swagger)

Chaque microservice expose sa documentation API via Swagger UI :

### URLs de Documentation
- **Gateway**: `http://localhost:8080/swagger-ui.html`
- **Auth Service**: `http://localhost:8081/swagger-ui.html`
- **Client Service**: `http://localhost:8082/swagger-ui.html`
- **Wallet Service**: `http://localhost:8083/swagger-ui.html`
- **Order Service**: `http://localhost:8084/swagger-ui.html`

### APIs JSON
- **Gateway**: `http://localhost:8080/api-docs`
- **Auth Service**: `http://localhost:8081/api-docs`
- **Client Service**: `http://localhost:8082/api-docs`
- **Wallet Service**: `http://localhost:8083/api-docs`
- **Order Service**: `http://localhost:8084/api-docs`

### Fonctionnalités Swagger
- Documentation complète des endpoints
- Modèles de données interactifs
- Test des APIs directement depuis l'interface
- Support de l'authentification JWT Bearer
- Accès via Gateway ou direct aux services

## Monitoring et Métriques

### Endpoints de Santé
Chaque service expose des endpoints de monitoring via Spring Boot Actuator :

```
http://localhost:[PORT]/actuator/health
http://localhost:[PORT]/actuator/metrics
http://localhost:[PORT]/actuator/prometheus
```

### Métriques Collectées
- **Performance HTTP**: Taux de requêtes, latence, codes de status
- **JVM**: Utilisation mémoire, garbage collection, threads
- **Business**: Métriques spécifiques au domaine (transactions, ordres, etc.)
- **Infrastructure**: CPU, mémoire, connexions base de données

### Prometheus Configuration
Prometheus collecte automatiquement les métriques de tous les services :
- Intervalle de scraping: 5 secondes
- Configuration: `monitoring/prometheus/prometheus.yml`
- Interface: `http://localhost:9090`

### Grafana Dashboards
Dashboards préconfigurés disponibles :
- **BrokerX Microservices Overview**: Vue d'ensemble des performances
- **Individual Service Metrics**: Métriques détaillées par service
- **Business Metrics**: KPIs métier et transactions

## Démarrage de la Stack

### Démarrage Automatique
```bash
# Windows
start-brokerx-stack.bat

# Ou manuellement
docker-compose -f docker-compose.monitoring.yml up -d
```

### Démarrage Manuel des Services
```bash
# 1. Monitoring stack
docker-compose -f docker-compose.monitoring.yml up -d

# 2. Gateway Service
cd gatewayService && mvnw spring-boot:run

# 3. Auth Service
cd authService/authService && mvnw spring-boot:run

# 4. Client Service
cd clientService/clientService && mvnw spring-boot:run

# 5. Wallet Service
cd walletService/walletService && mvnw spring-boot:run

# 6. Order Service
cd orderService/orderService && mvnw spring-boot:run
```

### Arrêt de la Stack
```bash
# Windows
stop-brokerx-stack.bat

# Ou manuellement
docker-compose -f docker-compose.monitoring.yml down
```

## Vérification du Déploiement

### 1. Vérifier les Services
```bash
# Health checks
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Auth
curl http://localhost:8082/actuator/health  # Client
curl http://localhost:8083/actuator/health  # Wallet
curl http://localhost:8084/actuator/health  # Order
```

### 2. Vérifier Prometheus
- Accéder à `http://localhost:9090`
- Vérifier les targets dans Status > Targets
- Toutes les cibles doivent être "UP"

### 3. Vérifier Grafana
- Accéder à `http://localhost:3000` (admin/admin)
- Vérifier la datasource Prometheus
- Consulter le dashboard "BrokerX Microservices Overview"

### 4. Tester la Documentation API
- Accéder aux URLs Swagger de chaque service
- Tester l'authentification avec un token JWT
- Valider les endpoints critiques

## Configuration Avancée

### Personnalisation des Métriques
Ajout de métriques custom dans les services :

```java
@Component
public class BusinessMetrics {
    private final Counter orderCounter;
    private final Timer transactionTimer;
    
    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.orderCounter = Counter.builder("brokerx.orders.total")
            .description("Total orders processed")
            .tag("service", "order")
            .register(meterRegistry);
            
        this.transactionTimer = Timer.builder("brokerx.transaction.duration")
            .description("Transaction processing time")
            .register(meterRegistry);
    }
}
```

### Alerting (Extension)
Pour ajouter des alertes Prometheus :

```yaml
# prometheus/rules.yml
groups:
  - name: brokerx.rules
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
```

## Troubleshooting

### Services ne démarrent pas
1. Vérifier les ports disponibles
2. Contrôler les logs dans les consoles ouvertes
3. Vérifier la configuration des bases de données H2

### Prometheus ne collecte pas les métriques
1. Vérifier que les services exposent `/actuator/prometheus`
2. Contrôler la configuration dans `prometheus.yml`
3. Vérifier la connectivité réseau (host.docker.internal)

### Grafana ne affiche pas de données
1. Vérifier la datasource Prometheus
2. Contrôler que Prometheus collecte bien les métriques
3. Vérifier les requêtes dans les dashboards

## Évolutions Futures

### Intégrations Possibles
- **Jaeger/Zipkin**: Tracing distribué
- **ELK Stack**: Centralisation des logs
- **AlertManager**: Gestion avancée des alertes
- **Service Mesh**: Istio pour la gestion du trafic

### Optimisations
- Mise en cache des métriques
- Agrégation des données historiques
- Dashboards métier spécialisés
- Monitoring de la sécurité

## Contact et Support

Pour toute question concernant la stack d'observabilité :
- **Équipe DevOps**: devops@brokerx.com
- **Documentation**: `/docs/observability/`
- **Issues**: GitHub Issues du projet