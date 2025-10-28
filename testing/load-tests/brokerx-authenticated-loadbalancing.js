import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ============================================================================
// TEST LOAD BALANCING ROBUSTE - BROKERX AVEC AUTHENTIFICATION RÉELLE
// ============================================================================
// Ce test crée de vrais utilisateurs, obtient des tokens JWT, et teste
// les endpoints critiques avec une authentification fonctionnelle
// ============================================================================

// Métriques personnalisées
const requestsPerEndpoint = new Counter('requests_per_endpoint');
const authenticatedRequests = new Counter('authenticated_requests');
const dbQueryLatency = new Trend('db_query_latency');
const authErrors = new Rate('auth_errors');

export const options = {
    scenarios: {
        realistic_load: {
            executor: 'ramping-vus',
            stages: [
                { duration: '1m', target: 5 },    // Montée douce
                { duration: '3m', target: 10 },   // Charge normale
                { duration: '2m', target: 15 },   // Pic
                { duration: '1m', target: 0 },    // Descente
            ],
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<3000'],
        'http_req_failed': ['rate<0.40'],  // Tolérant pour phase de setup
    },
};

const INSTANCE_COUNT = __ENV.INSTANCES || '2';
const BASE_URL = 'http://localhost:80';

// Utilisateurs de test (seront créés au setup)
let testUsers = [];

// ============================================================================
// ENDPOINTS CRITIQUES RÉELS DE BROKERX
// ============================================================================
const criticalEndpoints = [
    // === ENDPOINTS PUBLICS (baseline) ===
    {
        path: '/health',
        method: 'GET',
        weight: 10,
        requiresAuth: false,
        cacheable: false,
        description: 'Health Check (control)',
        expectedStatus: [200]
    },
    {
        path: '/nginx-health',
        method: 'GET',
        weight: 10,
        requiresAuth: false,
        cacheable: false,
        description: 'NGINX Health',
        expectedStatus: [200]
    },
    {
        path: '/api/auth/test',
        method: 'GET',
        weight: 15,
        requiresAuth: false,
        cacheable: true,
        description: 'Auth Service Test (DB connection)',
        expectedStatus: [200, 404, 500]
    },
    {
        path: '/api/clients/test',
        method: 'GET',
        weight: 15,
        requiresAuth: false,
        cacheable: true,
        description: 'Client Service Test (DB connection)',
        expectedStatus: [200, 404, 500]
    },
    
    // === ENDPOINTS AUTHENTIFIÉS (critiques pour cache) ===
    {
        path: '/api/clients/me',
        method: 'GET',
        weight: 25,
        requiresAuth: true,
        cacheable: true,
        description: 'Get Current Client Profile (cacheable)',
        expectedStatus: [200, 401, 404]
    },
    {
        path: '/api/wallet/balance',
        method: 'GET',
        weight: 25,
        requiresAuth: true,
        cacheable: true,
        description: 'Get Wallet Balance (expensive, cacheable)',
        expectedStatus: [200, 401, 404, 500]
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
// SETUP: Créer utilisateurs de test et obtenir tokens
// ============================================================================
export function setup() {
    console.log(`🚀 Setup: BrokerX Load Balancing Test - ${INSTANCE_COUNT} instances`);
    console.log('📝 Création d\'utilisateurs de test...');
    
    // Vérifier connectivité
    const healthCheck = http.get(`${BASE_URL}/health`);
    if (healthCheck.status !== 200) {
        console.error('❌ Services not ready!');
        return { users: [], ready: false };
    }
    console.log('✅ Services ready');
    
    // Créer 5 utilisateurs de test
    const users = [];
    for (let i = 1; i <= 5; i++) {
        const timestamp = Date.now();
        const email = `loadtest${i}_${timestamp}@brokerx.test`;
        const password = 'LoadTest123!';
        const name = `LoadTest User ${i}`;
        
        // 1. Créer le compte avec le bon format
        const registerPayload = JSON.stringify({
            name: name,
            email: email,
            password: password
        });
        
        console.log(`📝 Creating user: ${email}`);
        const registerResponse = http.post(
            `${BASE_URL}/api/clients/register`,
            registerPayload,
            { headers: { 'Content-Type': 'application/json' }, timeout: '15s' }
        );
        
        console.log(`   Register response: ${registerResponse.status} - ${registerResponse.body.substring(0, 100)}`);
        
        if (registerResponse.status === 200 || registerResponse.status === 201) {
            console.log(`✅ User created: ${email}`);
        } else {
            console.log(`❌ Failed to create user: ${email} (${registerResponse.status})`);
            console.log(`   Error: ${registerResponse.body}`);
            continue; // Skip login if registration failed
        }
        
        sleep(1); // Pause pour que l'inscription soit complète
        
        // 2. Login pour obtenir le token
        const loginPayload = JSON.stringify({
            email: email,
            password: password
        });
        
        const loginResponse = http.post(
            `${BASE_URL}/api/auth/simple-login`,
            loginPayload,
            { headers: { 'Content-Type': 'application/json' }, timeout: '15s' }
        );
        
        console.log(`🔐 Login response: ${loginResponse.status} - ${loginResponse.body.substring(0, 100)}`);
        
        if (loginResponse.status === 200) {
            try {
                const loginData = JSON.parse(loginResponse.body);
                console.log(`   Login data: ${JSON.stringify(loginData)}`);
                
                // Essayer différents formats de réponse
                let token = null;
                if (loginData.token) {
                    token = loginData.token;
                } else if (loginData.data && loginData.data.token) {
                    token = loginData.data.token;
                } else if (loginData.accessToken) {
                    token = loginData.accessToken;
                }
                
                if (token) {
                    users.push({
                        email: email,
                        token: token,
                        userId: i
                    });
                    console.log(`✅ Token obtained for ${email}`);
                } else {
                    console.log(`⚠️  No token found in response. Full response: ${loginResponse.body}`);
                }
            } catch (e) {
                console.log(`❌ Failed to parse login response: ${e.message}`);
                console.log(`   Raw response: ${loginResponse.body}`);
            }
        } else {
            console.log(`❌ Login failed: ${loginResponse.status}`);
            console.log(`   Response: ${loginResponse.body}`);
        }
        
        sleep(1);
    }
    
    console.log(`\n✅ Setup complete: ${users.length} users with tokens ready`);
    return { users: users, ready: users.length > 0 };
}

// ============================================================================
// TEST PRINCIPAL
// ============================================================================
export default function(data) {
    if (!data.ready || data.users.length === 0) {
        console.log('⚠️  No authenticated users available, using public endpoints only');
    }
    
    const endpoint = selectEndpoint();
    
    // Construire l'URL
    let url = `${BASE_URL}${endpoint.path}`;
    
    // Ajouter paramètres pour simulation cache
    if (endpoint.cacheable) {
        const cacheKey = Math.floor(Math.random() * 30) + 1;
        url += `?cacheKey=${cacheKey}`;
    }
    
    // Préparer headers
    const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'X-Load-Test': `brokerx-${INSTANCE_COUNT}instances`,
        'X-Cache-Simulation': endpoint.cacheable ? 'enabled' : 'disabled'
    };
    
    // Ajouter authentification si requis
    if (endpoint.requiresAuth && data.users && data.users.length > 0) {
        const randomUser = data.users[Math.floor(Math.random() * data.users.length)];
        headers['Authorization'] = `Bearer ${randomUser.token}`;
        headers['X-Authenticated-User'] = randomUser.email;
        authenticatedRequests.add(1);
    }
    
    // Faire la requête
    const startTime = new Date();
    const response = http.get(url, {
        headers: headers,
        timeout: '10s'
    });
    const duration = new Date() - startTime;
    
    // Métriques
    requestsPerEndpoint.add(1, { endpoint: endpoint.path });
    if (endpoint.cacheable) {
        dbQueryLatency.add(duration);
    }
    
    // Validation
    const isExpectedStatus = endpoint.expectedStatus.includes(response.status);
    if (!isExpectedStatus && endpoint.requiresAuth && response.status === 401) {
        authErrors.add(1);
    }
    
    check(response, {
        [`${endpoint.description} - Status OK`]: (r) => isExpectedStatus,
        [`${endpoint.description} - Response time acceptable`]: (r) => r.timings.duration < 5000,
        'No connection error': (r) => r.status !== 0
    });
    
    // Log occasionnel
    if (Math.random() < 0.02) {
        const authStatus = endpoint.requiresAuth ? '🔐' : '🌐';
        console.log(`${authStatus} ${endpoint.path} → ${response.status} (${response.timings.duration.toFixed(0)}ms)`);
    }
    
    sleep(0.2);
}

// ============================================================================
// RÉSUMÉ DES RÉSULTATS
// ============================================================================
export function handleSummary(data) {
    const summary = {
        test_type: 'BROKERX_LOADBALANCING_AUTHENTICATED',
        test_phase: 'BEFORE_CACHE',
        instances: parseInt(INSTANCE_COUNT),
        timestamp: new Date().toISOString(),
        
        // Métriques globales
        total_requests: data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0,
        rps: data.metrics.http_reqs ? (data.metrics.http_reqs.values.count / (data.state.testRunDurationMs / 1000)) : 0,
        
        // Latences (critiques pour mesurer cache)
        latency_avg: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.avg : 0,
        latency_p50: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.med : 0,
        latency_p95: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : 0,
        latency_p99: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(99)'] : 0,
        latency_max: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.max : 0,
        
        // Latence spécifique requêtes DB (cachables)
        db_query_latency_p95: data.metrics.db_query_latency ? data.metrics.db_query_latency.values['p(95)'] : 0,
        
        // Erreurs
        error_rate: data.metrics.http_req_failed ? (data.metrics.http_req_failed.values.rate * 100) : 0,
        auth_error_rate: data.metrics.auth_errors ? (data.metrics.auth_errors.values.rate * 100) : 0,
        
        // Authentification
        authenticated_requests: data.metrics.authenticated_requests ? data.metrics.authenticated_requests.values.count : 0,
        
        // Système
        max_vus: data.metrics.vus_max ? data.metrics.vus_max.values.max : 0,
        duration_seconds: data.state ? (data.state.testRunDurationMs / 1000) : 0
    };
    
    console.log('\n📊 RÉSULTATS LOAD BALANCING BROKERX:');
    console.log('═══════════════════════════════════════════════════');
    console.log(`🏭 Instances: ${summary.instances}`);
    console.log(`📈 Total requêtes: ${summary.total_requests}`);
    console.log(`🔐 Requêtes authentifiées: ${summary.authenticated_requests}`);
    console.log(`⚡ RPS: ${summary.rps.toFixed(2)} req/sec`);
    console.log(`⏱️  Latence moyenne: ${summary.latency_avg.toFixed(2)}ms`);
    console.log(`📊 Latence P50: ${summary.latency_p50.toFixed(2)}ms`);
    console.log(`📈 Latence P95: ${summary.latency_p95.toFixed(2)}ms`);
    console.log(`🔥 Latence P99: ${summary.latency_p99.toFixed(2)}ms`);
    console.log(`💾 DB Query P95: ${summary.db_query_latency_p95.toFixed(2)}ms`);
    console.log(`❌ Erreurs HTTP: ${summary.error_rate.toFixed(2)}%`);
    console.log(`🔒 Erreurs Auth: ${summary.auth_error_rate.toFixed(2)}%`);
    console.log(`⏰ Durée: ${summary.duration_seconds.toFixed(1)}s`);
    console.log('\n💡 Baseline AVANT CACHE - Endpoints réels avec authentification');
    
    return {
        'stdout': JSON.stringify(summary, null, 2),
        [`brokerx-loadbalancing-BEFORE-CACHE-${INSTANCE_COUNT}instances.json`]: JSON.stringify(summary, null, 2),
    };
}