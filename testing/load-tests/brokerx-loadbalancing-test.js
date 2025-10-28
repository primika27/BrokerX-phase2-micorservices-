import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ============================================================================
// TEST LOAD BALANCING ROBUSTE - BROKERX MICROSERVICES
// ============================================================================
// Ce test utilise les VRAIS endpoints critiques de BrokerX pour mesurer :
// 1. Performance du load balancing (N=1,2,3,4 instances)
// 2. Baseline AVANT cache pour comparaison
// 3. Métriques pour dashboards Grafana
// ============================================================================

// Métriques personnalisées
const requestsPerEndpoint = new Counter('requests_per_endpoint');
const authErrors = new Rate('auth_errors');
const dbQueryLatency = new Trend('db_query_latency');

export const options = {
    scenarios: {
        realistic_load: {
            executor: 'ramping-vus',
            stages: [
                { duration: '2m', target: 10 },   // Montée progressive
                { duration: '5m', target: 20 },   // Charge normale soutenue
                { duration: '2m', target: 30 },   // Pic de charge
                { duration: '1m', target: 0 },    // Descente
            ],
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<2000'],  // 95% des requêtes < 2s
        'http_req_failed': ['rate<0.30'],      // Accepter 30% échec (sans auth)
        'db_query_latency': ['p(95)<1000'],    // Requêtes DB < 1s
    },
};

const INSTANCE_COUNT = __ENV.INSTANCES || '2';
const BASE_URL = 'http://localhost:80';

// ============================================================================
// ENDPOINTS STRATÉGIQUES BASÉS SUR L'ARCHITECTURE RÉELLE
// ============================================================================
const criticalEndpoints = [
    // === ENDPOINTS PUBLICS (pas d'auth) - Pour baseline load balancing ===
    {
        path: '/health',
        method: 'GET',
        weight: 5,
        requiresAuth: false,
        cacheable: false,
        category: 'health',
        description: 'Health Check',
        expectedStatus: [200]
    },
    {
        path: '/api/auth/test',
        method: 'GET',
        weight: 10,
        requiresAuth: false,
        cacheable: false,
        category: 'service_test',
        description: 'Auth Service Test',
        expectedStatus: [200, 500]
    },
    {
        path: '/api/clients/test',
        method: 'GET',
        weight: 10,
        requiresAuth: false,
        cacheable: false,
        category: 'service_test',
        description: 'Client Service Test',
        expectedStatus: [200, 500]
    },
    
    // === ENDPOINTS DB QUERIES (publics, coûteux, CACHABLES) ===
    {
        path: '/api/clients/getByEmail',
        method: 'GET',
        weight: 25,
        requiresAuth: false,
        cacheable: true,
        category: 'db_query',
        description: 'Client Lookup by Email (DB query)',
        expectedStatus: [200, 400, 404],
        params: () => {
            const emails = [
                'user1@test.com', 'user2@test.com', 'user3@test.com',
                'admin@brokerx.com', 'test@example.com', 'trader@brokerx.com',
                'investor1@brokerx.com', 'investor2@brokerx.com'
            ];
            return { email: emails[Math.floor(Math.random() * emails.length)] };
        }
    },
    {
        path: '/api/clients/getEmailById',
        method: 'GET',
        weight: 25,
        requiresAuth: false,
        cacheable: true,
        category: 'db_query',
        description: 'Email Lookup by ClientID (DB query)',
        expectedStatus: [200, 404],
        params: () => {
            // Simuler 20 clients différents
            return { clientId: Math.floor(Math.random() * 20) + 1 };
        }
    },
    
    // === ENDPOINTS AUTHENTIFIÉS (pour tests futurs avec tokens) ===
    {
        path: '/api/wallet/balance',
        method: 'GET',
        weight: 15,
        requiresAuth: true,
        cacheable: true,
        category: 'authenticated',
        description: 'Wallet Balance (calculations - TRÈS CACHABLE)',
        expectedStatus: [200, 401, 404],
        params: () => {
            // Simuler différents utilisateurs
            const emails = ['user1@test.com', 'user2@test.com', 'trader@brokerx.com'];
            return { ownerEmail: emails[Math.floor(Math.random() * emails.length)] };
        }
    },
    {
        path: '/api/orders/holdings',
        method: 'GET',
        weight: 10,
        requiresAuth: true,
        cacheable: true,
        category: 'authenticated',
        description: 'Order Holdings (complex query - TRÈS CACHABLE)',
        expectedStatus: [200, 401, 404]
    }
];

// Sélection pondérée d'endpoint
function selectEndpoint() {
    const totalWeight = criticalEndpoints.reduce((sum, ep) => sum + ep.weight, 0);
    const random = Math.random() * totalWeight;
    
    let cumulativeWeight = 0;
    for (const endpoint of criticalEndpoints) {
        cumulativeWeight += endpoint.weight;
        if (random <= cumulativeWeight) {
            return endpoint;
        }
    }
    return criticalEndpoints[0];
}

// ============================================================================
// SETUP - Vérification de connectivité
// ============================================================================
export function setup() {
    console.log('╔════════════════════════════════════════════════════════════════╗');
    console.log('║     BROKERX LOAD BALANCING TEST - BASELINE AVANT CACHE        ║');
    console.log('╚════════════════════════════════════════════════════════════════╝');
    console.log(`🏭 Instances: ${INSTANCE_COUNT}`);
    console.log(`🎯 Endpoints: ${criticalEndpoints.length} endpoints critiques`);
    console.log(`📊 Focus: Requêtes DB cachables + endpoints publics`);
    console.log('');
    
    // Vérifier connectivité de base
    console.log('🔍 Vérification de la connectivité...');
    
    const healthCheck = http.get(`${BASE_URL}/health`);
    console.log(`  ✅ Health: ${healthCheck.status}`);
    
    const authTest = http.get(`${BASE_URL}/api/auth/test`);
    console.log(`  ${authTest.status === 200 ? '✅' : '⚠️'} AuthService: ${authTest.status}`);
    
    const clientTest = http.get(`${BASE_URL}/api/clients/test`);
    console.log(`  ${clientTest.status === 200 ? '✅' : '⚠️'} ClientService: ${clientTest.status}`);
    
    console.log('');
    console.log('🚀 Démarrage du test de charge...');
    console.log('');
    
    return { 
        startTime: Date.now(),
        testId: `loadbalancing-${INSTANCE_COUNT}instances-${Date.now()}`
    };
}

// ============================================================================
// MAIN TEST FUNCTION
// ============================================================================
export default function() {
    const endpoint = selectEndpoint();
    
    // Construire l'URL
    let url = `${BASE_URL}${endpoint.path}`;
    
    // Ajouter paramètres si nécessaire
    if (endpoint.params) {
        const params = endpoint.params();
        const queryString = Object.entries(params)
            .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
            .join('&');
        url += `?${queryString}`;
    }
    
    // Headers de base
    const headers = {
        'Accept': 'application/json',
        'User-Agent': 'k6-brokerx-loadtest',
        'X-Test-ID': `lb-${INSTANCE_COUNT}instances`,
        'X-Endpoint-Category': endpoint.category
    };
    
    // Ajouter header d'auth si requis (pour le moment sans token, testera 401)
    if (endpoint.requiresAuth) {
        // Pour baseline: laisser sans token pour mesurer sans cache
        // Après cache: ajouter vrais tokens
        headers['X-Authenticated-User'] = 'test-user@brokerx.com';
    }
    
    // Faire la requête
    const startTime = Date.now();
    const response = http.get(url, {
        headers: headers,
        timeout: '10s',
        tags: {
            endpoint: endpoint.path,
            category: endpoint.category,
            cacheable: endpoint.cacheable.toString()
        }
    });
    const duration = Date.now() - startTime;
    
    // Métriques personnalisées
    requestsPerEndpoint.add(1, { endpoint: endpoint.path });
    
    if (endpoint.category === 'db_query') {
        dbQueryLatency.add(duration);
    }
    
    if (endpoint.requiresAuth && response.status === 401) {
        authErrors.add(1);
    }
    
    // Validation
    const isAcceptableStatus = endpoint.expectedStatus.includes(response.status);
    
    check(response, {
        [`${endpoint.description} - Status acceptable`]: (r) => isAcceptableStatus,
        [`${endpoint.description} - Response time OK`]: (r) => r.timings.duration < 10000,
        'No network error': (r) => r.status !== 0,
    });
    
    // Logging occasionnel pour debug (1% des requêtes)
    if (Math.random() < 0.01) {
        const emoji = endpoint.cacheable ? '💾' : 
                     endpoint.requiresAuth ? '🔐' : '🌐';
        const statusEmoji = isAcceptableStatus ? '✅' : '❌';
        console.log(`${emoji} ${statusEmoji} ${endpoint.path} → ${response.status} (${duration}ms)`);
    }
    
    // Pause réaliste entre requêtes
    sleep(0.1);
}

// ============================================================================
// SUMMARY - Rapport détaillé pour analyse
// ============================================================================
export function handleSummary(data) {
    const summary = {
        test_metadata: {
            test_type: "LOAD_BALANCING_BASELINE",
            instances: parseInt(INSTANCE_COUNT),
            timestamp: new Date().toISOString(),
            test_duration_seconds: data.state ? (data.state.testRunDurationMs / 1000) : 0
        },
        
        performance_metrics: {
            total_requests: data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0,
            requests_per_second: data.metrics.http_reqs ? 
                (data.metrics.http_reqs.values.count / (data.state.testRunDurationMs / 1000)) : 0,
            
            latency_ms: {
                avg: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.avg : 0,
                p50: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.med : 0,
                p90: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(90)'] : 0,
                p95: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : 0,
                p99: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(99)'] : 0,
                max: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.max : 0
            },
            
            db_query_latency_ms: {
                avg: data.metrics.db_query_latency ? data.metrics.db_query_latency.values.avg : 0,
                p95: data.metrics.db_query_latency ? data.metrics.db_query_latency.values['p(95)'] : 0
            }
        },
        
        error_metrics: {
            http_error_rate_percent: data.metrics.http_req_failed ? 
                (data.metrics.http_req_failed.values.rate * 100) : 0,
            auth_error_rate_percent: data.metrics.auth_errors ? 
                (data.metrics.auth_errors.values.rate * 100) : 0
        },
        
        system_metrics: {
            max_virtual_users: data.metrics.vus_max ? data.metrics.vus_max.values.max : 0,
            data_received_mb: data.metrics.data_received ? 
                (data.metrics.data_received.values.count / 1024 / 1024) : 0,
            data_sent_mb: data.metrics.data_sent ? 
                (data.metrics.data_sent.values.count / 1024 / 1024) : 0
        }
    };
    
    // Affichage console formaté
    console.log('\n╔════════════════════════════════════════════════════════════════╗');
    console.log('║                   RÉSULTATS DU TEST                            ║');
    console.log('╚════════════════════════════════════════════════════════════════╝\n');
    
    console.log('📊 MÉTRIQUES DE PERFORMANCE:');
    console.log(`   🏭 Instances: ${summary.test_metadata.instances}`);
    console.log(`   📈 Total requêtes: ${summary.performance_metrics.total_requests.toLocaleString()}`);
    console.log(`   ⚡ RPS: ${summary.performance_metrics.requests_per_second.toFixed(2)} req/sec`);
    console.log('');
    
    console.log('⏱️  LATENCES:');
    console.log(`   📊 Moyenne: ${summary.performance_metrics.latency_ms.avg.toFixed(2)}ms`);
    console.log(`   📈 P50: ${summary.performance_metrics.latency_ms.p50.toFixed(2)}ms`);
    console.log(`   📈 P95: ${summary.performance_metrics.latency_ms.p95.toFixed(2)}ms`);
    console.log(`   📈 P99: ${summary.performance_metrics.latency_ms.p99.toFixed(2)}ms`);
    console.log(`   🔥 Max: ${summary.performance_metrics.latency_ms.max.toFixed(2)}ms`);
    console.log('');
    
    console.log('💾 REQUÊTES DB (cachables):');
    console.log(`   📊 Latence moyenne: ${summary.performance_metrics.db_query_latency_ms.avg.toFixed(2)}ms`);
    console.log(`   📈 Latence P95: ${summary.performance_metrics.db_query_latency_ms.p95.toFixed(2)}ms`);
    console.log('');
    
    console.log('❌ ERREURS:');
    console.log(`   🌐 Erreurs HTTP: ${summary.error_metrics.http_error_rate_percent.toFixed(2)}%`);
    console.log(`   🔐 Erreurs Auth: ${summary.error_metrics.auth_error_rate_percent.toFixed(2)}%`);
    console.log('');
    
    console.log('⏰ DURÉE: ' + summary.test_metadata.test_duration_seconds.toFixed(1) + 's');
    console.log('');
    console.log('💡 Ces métriques servent de BASELINE pour comparaison après cache');
    console.log('');
    
    // Sauvegarder les résultats
    return {
        'stdout': JSON.stringify(summary, null, 2),
        [`results-baseline-${INSTANCE_COUNT}instances.json`]: JSON.stringify(summary, null, 2),
    };
}