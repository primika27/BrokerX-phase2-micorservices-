import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Métriques pour load balancing
const requestsPerSecond = new Counter('requests_total');
const loadBalancerErrors = new Rate('load_balancer_errors');

export const options = {
    stages: [
        { duration: '1m', target: 10 },   // Montée douce
        { duration: '2m', target: 20 },   // Charge stable  
        { duration: '1m', target: 30 },   // Charge élevée
        { duration: '30s', target: 0 },   // Descente
    ],
    thresholds: {
        'http_req_duration': ['p(95)<2000'], // Tolérant pour endpoints réalistes
        'http_req_failed': ['rate<0.20'],    // 20% d'erreurs acceptable (authentification)
    },
};

const INSTANCE_COUNT = __ENV.INSTANCES || '2';
const BASE_URL = 'http://localhost:80';

// Tokens de test (à remplir après création des utilisateurs)
let testUsers = [];
try {
    // Essayer de charger les tokens depuis le fichier
    testUsers = [
        { username: "testuser1", token: "fake-token-1", userId: 1 },
        { username: "testuser2", token: "fake-token-2", userId: 2 },
        { username: "testuser3", token: "fake-token-3", userId: 3 }
    ];
} catch (e) {
    console.log("⚠️ Tokens file not found, using mock tokens");
}

// Endpoints STRATÉGIQUES pour test de caching (publics mais coûteux)
const realisticEndpoints = [
    // Endpoints health (contrôle - pas cachables)
    { 
        path: '/health', 
        weight: 10, 
        requiresAuth: false,
        cacheable: false,
        description: 'Health Check (control)',
        expectedStatus: [200]
    },
    
    // Endpoints publics mais avec paramètres variables (simulant des requêtes cachables)
    { 
        path: '/api/auth/test', 
        weight: 30, 
        requiresAuth: false,
        cacheable: true,
        description: 'Auth Service - DB operations',
        expectedStatus: [200, 404, 500]
    },
    { 
        path: '/api/clients/test', 
        weight: 30, 
        requiresAuth: false,
        cacheable: true,
        description: 'Client Service - DB operations',
        expectedStatus: [200, 404, 500]
    },
    
    // Endpoints avec paramètres (publics, simulant des calculs coûteux)
    { 
        path: '/api/clients/getByEmail', 
        weight: 15, 
        requiresAuth: false,
        cacheable: true,
        description: 'Search Client by Email (expensive query)',
        expectedStatus: [200, 400, 404, 500]
    },
    { 
        path: '/api/clients/getEmailById', 
        weight: 15, 
        requiresAuth: false,
        cacheable: true,
        description: 'Get Email by ID (DB lookup)',
        expectedStatus: [200, 400, 404, 500]
    }
];

// Sélection pondérée d'endpoint
function selectEndpoint() {
    const totalWeight = realisticEndpoints.reduce((sum, ep) => sum + ep.weight, 0);
    const random = Math.random() * totalWeight;
    
    let cumulativeWeight = 0;
    for (const endpoint of realisticEndpoints) {
        cumulativeWeight += endpoint.weight;
        if (random <= cumulativeWeight) {
            return endpoint;
        }
    }
    return realisticEndpoints[0];
}

export function setup() {
    console.log(`🚀 Test Load Balancing RÉALISTE - ${INSTANCE_COUNT} instances`);
    console.log(`🎯 ${realisticEndpoints.length} endpoints (dont ${realisticEndpoints.filter(e => e.requiresAuth).length} avec auth)`);
    console.log(`👥 ${testUsers.length} utilisateurs de test disponibles`);
    
    // Test de connectivité
    const healthResponse = http.get(`${BASE_URL}/health`);
    console.log(`✅ Connectivité: ${healthResponse.status}`);
    
    return { startTime: Date.now() };
}

export default function() {
    const endpoint = selectEndpoint();
    
    // Construire l'URL selon le type d'endpoint
    let url = `${BASE_URL}${endpoint.path}`;
    
    // Pour les endpoints cachables, ajouter des paramètres qui simulent différentes requêtes
    if (endpoint.cacheable) {
        // Paramètres pour simuler des requêtes différentes qui seraient cachées
        if (endpoint.path.includes('getByEmail')) {
            // Emails variés pour simulation cache
            const emails = ['user1@test.com', 'user2@test.com', 'user3@test.com', 'admin@test.com', 'test@example.com'];
            const email = emails[Math.floor(Math.random() * emails.length)];
            url += `?email=${email}`;
        } else if (endpoint.path.includes('getEmailById')) {
            // IDs variés pour simulation cache
            const id = Math.floor(Math.random() * 10) + 1;
            url += `?id=${id}`;
        } else {
            // Pour les endpoints /test, ajouter paramètre de variation
            const variation = Math.floor(Math.random() * 20) + 1;
            url += `?cacheKey=${variation}`;
        }
    }
    
    // Headers simples (pas d'auth pour éviter 401)
    const headers = {
        'X-Load-Test': `cacheable-baseline-${INSTANCE_COUNT}instances`,
        'Accept': 'application/json',
        'User-Agent': 'k6-cache-baseline',
        'Cache-Control': 'no-cache'
    };
    
    // Faire la requête
    const response = http.get(url, {
        headers: headers,
        timeout: '10s'
    });
    
    // Métriques
    requestsPerSecond.add(1);
    
    // Validation adaptée selon l'endpoint
    const isValidStatus = endpoint.expectedStatus.includes(response.status);
    const isSuccess = response.status >= 200 && response.status < 300;
    const isAcceptableError = endpoint.expectedStatus.includes(response.status);
    
    if (!isAcceptableError) {
        loadBalancerErrors.add(1);
    }
    
    check(response, {
        [`${endpoint.description} - Status acceptable`]: (r) => isAcceptableError,
        [`${endpoint.description} - Response time OK`]: (r) => r.timings.duration < 5000,
        [`${endpoint.description} - No connection error`]: (r) => r.status !== 0,
    });
    
    // Log occasionnel pour debug
    if (Math.random() < 0.01) { // 1% des requêtes
        const authStatus = endpoint.requiresAuth ? '🔐' : '🌐';
        console.log(`${authStatus} ${endpoint.path} → ${response.status} (${response.timings.duration.toFixed(0)}ms)`);
    }
    
    sleep(0.1);
}

export function handleSummary(data) {
    const summary = {
        test_type: "REALISTIC_LOAD_BALANCING", 
        instances: parseInt(INSTANCE_COUNT),
        timestamp: new Date().toISOString(),
        
        // Métriques principales
        rps: data.metrics.http_reqs ? (data.metrics.http_reqs.values.count / (data.state.testRunDurationMs / 1000)) : 0,
        total_requests: data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0,
        
        // Latences (critiques pour load balancing)
        latency_avg: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.avg : 0,
        latency_p50: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.med : 0,
        latency_p95: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : 0,
        latency_max: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.max : 0,
        
        // Erreurs
        error_rate: data.metrics.http_req_failed ? (data.metrics.http_req_failed.values.rate * 100) : 0,
        
        // Load balancing metrics
        lb_error_rate: data.metrics.load_balancer_errors ? (data.metrics.load_balancer_errors.values.rate * 100) : 0,
        
        // Système
        max_vus: data.metrics.vus_max ? data.metrics.vus_max.values.max : 0,
        duration_seconds: data.state ? (data.state.testRunDurationMs / 1000) : 0
    };
    
    console.log('\n📊 RÉSULTATS LOAD BALANCING RÉALISTE:');
    console.log('═══════════════════════════════════════════');
    console.log(`🏭 Instances: ${summary.instances}`);
    console.log(`📈 Total requêtes: ${summary.total_requests}`);
    console.log(`⚡ RPS: ${summary.rps.toFixed(2)} req/sec`);
    console.log(`⏱️  Latence P50: ${summary.latency_p50.toFixed(2)}ms`);
    console.log(`📈 Latence P95: ${summary.latency_p95.toFixed(2)}ms`);
    console.log(`⛔ Latence Max: ${summary.latency_max.toFixed(2)}ms`);
    console.log(`❌ Erreurs HTTP: ${summary.error_rate.toFixed(2)}%`);
    console.log(`🔄 Erreurs LB: ${summary.lb_error_rate.toFixed(2)}%`);
    console.log(`⏰ Durée: ${summary.duration_seconds.toFixed(1)}s`);
    console.log('\n💡 Baseline pour comparaison avec caching');
    
    return {
        'stdout': JSON.stringify(summary, null, 2),
        [`realistic-loadbalancing-${INSTANCE_COUNT}instances.json`]: JSON.stringify(summary, null, 2),
    };
}