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

const INSTANCE_COUNT = __ENV.INSTANCES || '2';
const BASE_URL = 'http://localhost:8080';

// ============================================================================
// SYSTÈME D'AUTHENTIFICATION JWT
// ============================================================================
let authToken = null;
const TEST_USER = {
    username: 'loadtest',
    email: 'loadtest@brokerx.com',
    password: 'LoadTest2024!'
};

function createTestUser() {
    const payload = JSON.stringify(TEST_USER);
    
    const response = http.post(`${BASE_URL}/api/auth/register`, payload, {
        headers: { 'Content-Type': 'application/json' }
    });
    
    console.log(`User creation: ${response.status}`);
    return response.status;
}

function authenticate() {
    const payload = JSON.stringify({
        username: TEST_USER.username,
        password: TEST_USER.password
    });
    
    const response = http.post(`${BASE_URL}/api/auth/login`, payload, {
        headers: { 'Content-Type': 'application/json' }
    });
    
    if (response.status === 200) {
        const body = JSON.parse(response.body);
        authToken = body.token;
        console.log(`✅ Authentication successful - Token: ${authToken ? authToken.substring(0, 20) + '...' : 'null'}`);
        return true;
    } else {
        console.log(`❌ Authentication failed: ${response.status} - ${response.body}`);
        return false;
    }
}

function getAuthHeaders() {
    if (authToken) {
        return {
            'Authorization': `Bearer ${authToken}`,
            'Content-Type': 'application/json'
        };
    }
    return {
        'Content-Type': 'application/json'
    };
}

export const options = {
    scenarios: {
        realistic_load: {
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: 10 },   // Montée progressive
                { duration: '1m30s', target: 20 }, // Charge normale soutenue
                { duration: '30s', target: 30 },   // Pic de charge
                { duration: '30s', target: 0 },    // Descente
            ],
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<2000'],  // 95% des requêtes < 2s
        'http_req_failed': ['rate<0.30'],      // Accepter 30% échec (sans auth)
        'db_query_latency': ['p(95)<1000'],    // Requêtes DB < 1s
    },
    // Pas de configuration Prometheus dans le script - on utilisera les arguments k6
    // Les métriques seront envoyées via --out experimental-prometheus-rw
};

// Configuration des tags Prometheus pour identifier les tests
const TEST_TAGS = {
    instance_count: INSTANCE_COUNT,
    test_type: 'load_balancing_baseline',
    environment: 'development',
    test_id: `lb-${INSTANCE_COUNT}instances-${Date.now()}`
};

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
    
    // === ENDPOINTS DB QUERIES (avec auth, coûteux, CACHABLES) ===
    {
        path: '/api/clients/getByEmail',
        method: 'GET',
        weight: 25,
        requiresAuth: true,
        cacheable: true,
        category: 'db_query',
        description: 'Client Lookup by Email (DB query)',
        expectedStatus: [200, 400, 404, 500],
        params: () => {
            const emails = [
                'user1@test.com', 'user2@test.com', 'user3@test.com',
                'admin@brokerx.com', 'test@example.com', 'trader@brokerx.com',
                'investor1@brokerx.com', 'investor2@brokerx.com', 'nonexistent@test.com'
            ];
            return { email: emails[Math.floor(Math.random() * emails.length)] };
        }
    },
    {
        path: '/api/clients/getEmailById',
        method: 'GET',
        weight: 25,
        requiresAuth: true,
        cacheable: true,
        category: 'db_query',
        description: 'Email Lookup by ClientID (DB query)',
        expectedStatus: [200, 404, 500],
        params: () => {
            // Simuler différents clients, incluant des IDs qui n'existent pas
            return { clientId: Math.floor(Math.random() * 50) + 1 };
        }
    },
    
    // === ENDPOINTS PUBLICS SUPPLÉMENTAIRES (sans auth pour baseline) ===
    {
        path: '/api/wallet/test',
        method: 'GET',
        weight: 15,
        requiresAuth: true,
        cacheable: false,
        category: 'service_test',
        description: 'Wallet Service Test',
        expectedStatus: [200, 500]
    },
    
    // === ENDPOINTS AVEC PARAMÈTRES (publics) ===
    {
        path: '/api/wallet/balance',
        method: 'GET',
        weight: 10,
        requiresAuth: false,
        cacheable: true,
        category: 'db_query',
        description: 'Wallet Balance Query (expensive calculation)',
        expectedStatus: [200, 400, 404, 500],
        params: () => {
            // Tester avec différents emails (certains valides, d'autres non)
            const emails = ['user1@test.com', 'user2@test.com', 'trader@brokerx.com', 'nonexistent@test.com'];
            return { ownerEmail: emails[Math.floor(Math.random() * emails.length)] };
        }
    },
    {
        path: '/api/orders/holdings',
        method: 'GET',
        weight: 5,
        requiresAuth: false,
        cacheable: true,
        category: 'db_query',
        description: 'Order Holdings Query (complex query)',
        expectedStatus: [200, 400, 401, 404, 500],
        params: () => {
            // Tester avec différents emails
            const emails = ['user1@test.com', 'trader@brokerx.com', 'investor@test.com'];
            return { ownerEmail: emails[Math.floor(Math.random() * emails.length)] };
        }
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
    
    // Authentification
    console.log('');
    console.log('🔑 Configuration de l\'authentification...');
    
    // Créer utilisateur de test si nécessaire
    createTestUser();
    
    // S'authentifier
    const authSuccess = authenticate();
    if (!authSuccess) {
        console.log('⚠️ Authentification échouée - certains tests pourraient échouer');
    }
    
    console.log('');
    console.log('🚀 Démarrage du test de charge...');
    console.log('');
    
    return { 
        startTime: Date.now(),
        testId: `loadbalancing-${INSTANCE_COUNT}instances-${Date.now()}`,
        authToken: authToken
    };
}

// ============================================================================
// MAIN TEST FUNCTION
// ============================================================================
export default function(data) {
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

    // Headers avec authentification
    let headers = {
        'Accept': 'application/json',
        'User-Agent': 'k6-brokerx-loadtest',
        'X-Test-ID': `lb-${INSTANCE_COUNT}instances`,
        'X-Endpoint-Category': endpoint.category
    };

    // Ajouter token d'authentification si requis
    if (endpoint.requiresAuth && data && data.authToken) {
        headers['Authorization'] = `Bearer ${data.authToken}`;
    }
    
    // Faire la requête
    const startTime = Date.now();
    const response = http.get(url, {
        headers: headers,
        timeout: '10s',
        tags: {
            endpoint: endpoint.path,
            category: endpoint.category,
            cacheable: endpoint.cacheable.toString(),
            instance_count: INSTANCE_COUNT,
            test_type: 'load_balancing',
            service: endpoint.path.split('/')[2] || 'unknown'  // Extract service name
        }
    });
    const duration = Date.now() - startTime;
    
    // Métriques personnalisées avec tags pour Grafana
    requestsPerEndpoint.add(1, { 
        endpoint: endpoint.path,
        instance_count: INSTANCE_COUNT,
        service: endpoint.path.split('/')[2] || 'unknown'
    });
    
    if (endpoint.category === 'db_query') {
        dbQueryLatency.add(duration, {
            instance_count: INSTANCE_COUNT,
            endpoint: endpoint.path
        });
    }
    
    if (endpoint.requiresAuth && response.status === 401) {
        authErrors.add(1, {
            instance_count: INSTANCE_COUNT,
            endpoint: endpoint.path
        });
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
            instances: Number.parseInt(INSTANCE_COUNT),
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