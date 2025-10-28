import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// Métriques focalisées sur le load balancing
const loadBalancerLatency = new Trend('load_balancer_latency');
const requestsPerSecond = new Counter('requests_total');
const loadBalancerErrors = new Rate('load_balancer_errors');
const serviceDistribution = new Counter('service_distribution');

export let options = {
    scenarios: {
        load_balancing_test: {
            executor: 'ramping-vus',
            stages: [
                { duration: '1m', target: 10 },   // Montée douce
                { duration: '3m', target: 30 },   // Charge normale
                { duration: '2m', target: 60 },   // Charge élevée
                { duration: '1m', target: 10 },   // Descente
                { duration: '1m', target: 0 },    // Arrêt
            ],
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<1000'],
        'load_balancer_errors': ['rate<0.1'], // Moins de 10% d'erreurs
        'http_req_failed': ['rate<0.15'],
    },
};

const INSTANCE_COUNT = __ENV.INSTANCES || '1';
const BASE_URL = 'http://localhost:80';

// Variables de configuration pour le test
const ACTUATOR_AVAILABLE = true; // Flag pour tester les endpoints actuator

// Endpoints pour BASELINE AVANT CACHING - simulation opérations coûteuses
const testEndpoints = [
    // Health checks (légers - référence)
    { path: '/health', weight: 10 },
    { path: '/nginx-health', weight: 10 },
    
    // Endpoints COÛTEUX qui bénéficieraient du caching
    // (Simulation avec paramètres pour éviter cache navigateur)
    
    // 1. Consultation de stocks/inventaire (coûteux - requêtes DB fréquentes)
    { path: '/api/stores/1/stock?t={{timestamp}}', weight: 20 },
    { path: '/api/stores/2/stock?t={{timestamp}}', weight: 15 },
    
    // 2. Rapports de ventes (très coûteux - agrégations complexes)
    { path: '/api/reports/sales?period=daily&t={{timestamp}}', weight: 25 },
    { path: '/api/reports/sales?period=weekly&t={{timestamp}}', weight: 15 },
    
    // 3. Carnets de cotations (coûteux - données temps réel)
    { path: '/api/market/quotes/SPY?t={{timestamp}}', weight: 20 },
    { path: '/api/market/quotes/AAPL?t={{timestamp}}', weight: 15 },
    
    // 4. Portfolio/wallet consultation (coûteux - calculs complexes)
    { path: '/api/wallet/portfolio/summary?t={{timestamp}}', weight: 30 }
];

// Fonction pour sélectionner un endpoint basé sur le poids
function selectEndpoint() {
    const totalWeight = testEndpoints.reduce((sum, ep) => sum + ep.weight, 0);
    let random = Math.random() * totalWeight;
    
    for (const endpoint of testEndpoints) {
        random -= endpoint.weight;
        if (random <= 0) {
            // Remplacer le timestamp pour simuler des requêtes uniques
            let path = endpoint.path;
            if (path.includes('{{timestamp}}')) {
                path = path.replace('{{timestamp}}', Date.now() + Math.floor(Math.random() * 1000));
            }
            return path;
        }
    }
    return testEndpoints[0].path.replace('{{timestamp}}', Date.now()); // fallback
}

// Setup executé une seule fois au début du test
export function setup() {
    console.log('🚀 Initialisation du test de load balancing...');
    console.log(`📊 Configuration: ${INSTANCE_COUNT} instance(s)`);
    console.log('🎯 Focus: Distribution de charge NGINX entre instances');
    
    // Test rapide des endpoints pour validation
    const healthCheck = http.get(`${BASE_URL}/health`, { timeout: '5s' });
    const nginxCheck = http.get(`${BASE_URL}/nginx-health`, { timeout: '5s' });
    
    console.log(`🏥 Health endpoint: ${healthCheck.status}`);
    console.log(`⚙️  NGINX endpoint: ${nginxCheck.status}`);
    
    return { ready: true };
}

export default function() {
    // Sélection d'endpoint basée sur les poids (simulation charge réaliste)
    const endpoint = selectEndpoint();
    
    const headers = { 
        'X-Load-Test': 'baseline-before-caching',
        'X-Instances': INSTANCE_COUNT,
        'Accept': 'application/json',
        'Cache-Control': 'no-cache', // Force pas de cache navigateur
        'X-Bypass-Auth': 'load-test'  // Bypass auth pour focus sur perf
    };
    
    // Timeout plus long pour les endpoints coûteux
    const timeout = endpoint.includes('/health') ? '5s' : '15s';
    
    const response = http.get(`${BASE_URL}${endpoint}`, {
        headers: headers,
        timeout: timeout
    });
    
    // Vérifications focalisées sur le load balancing et endpoints réalistes
    const success = check(response, {
        'Load balancer responsive': (r) => r.status !== 0,
        'No 5xx errors': (r) => r.status < 500,
        'Reasonable latency': (r) => r.timings.duration < 3000,
        'Has response': (r) => r.body !== null,
        'Endpoint routed correctly': (r) => {
            // Endpoints publics doivent répondre 200 ou 404 (si pas implémentés)
            if (endpoint.includes('/test') || endpoint.includes('/health')) {
                return r.status === 200 || r.status === 404;
            }
            // Endpoints protégés peuvent retourner 401 (normal sans vraie auth)
            return r.status === 200 || r.status === 401 || r.status === 404;
        }
    });
    
    // Métriques
    loadBalancerLatency.add(response.timings.duration);
    requestsPerSecond.add(1);
    loadBalancerErrors.add(!success);
    serviceDistribution.add(1, { endpoint: endpoint });
    
    // Log avec plus d'info sur le type d'endpoint
    const endpointType = endpoint.includes('/health') ? '🏥' : 
                        endpoint.includes('/auth') ? '🔐' : 
                        endpoint.includes('/wallet') ? '💰' : 
                        endpoint.includes('/order') ? '📊' : 
                        endpoint.includes('/client') ? '👤' : '⚙️';
    
    if (success) {
        console.log(`${endpointType} LB-${INSTANCE_COUNT}: ${endpoint} (${response.timings.duration.toFixed(0)}ms) - ${response.status}`);
    } else {
        console.log(`❌ LB-${INSTANCE_COUNT}: ${endpoint} FAILED - ${response.status || 'timeout'}`);
    }
    
    sleep(0.1 + Math.random() * 0.5); // 100-600ms entre requêtes
}

export function handleSummary(data) {
    const instances = INSTANCE_COUNT;
    const duration = data.state.testRunDurationMs / 1000;
    
    // Get metrics using correct k6 structure (no optional chaining in k6)
    const totalRequests = data.metrics.iterations && data.metrics.iterations.values && data.metrics.iterations.values.count || 0;
    const httpReqs = data.metrics.http_reqs && data.metrics.http_reqs.values && data.metrics.http_reqs.values.count || 0;
    
    const rps = data.metrics.http_reqs && data.metrics.http_reqs.values && data.metrics.http_reqs.values.rate || 0;
    const p95Latency = data.metrics['http_req_duration'] && data.metrics['http_req_duration'].values && data.metrics['http_req_duration'].values['p(95)'] || 0;
    const errorRate = (data.metrics['http_req_failed'] && data.metrics['http_req_failed'].values && data.metrics['http_req_failed'].values.rate || 0) * 100;
    const avgLatency = data.metrics['http_req_duration'] && data.metrics['http_req_duration'].values && data.metrics['http_req_duration'].values.avg || 0;
    const maxVus = data.metrics.vus_max && data.metrics.vus_max.values && data.metrics.vus_max.values.max || 0;
    
    
    return {
        'stdout': `
 LOAD BALANCING RESULTS - ${instances} Instance(s)
================================================
🎯 Performance Metrics:
   ✅ Total Requests: ${httpReqs}
   ✅ Requests/sec: ${rps.toFixed(2)}
   ✅ Average Latency: ${avgLatency.toFixed(2)}ms
   ✅ P95 Latency: ${p95Latency.toFixed(2)}ms
   ✅ Error Rate: ${errorRate.toFixed(2)}%
   ✅ Max VUs: ${maxVus}

Load Balancer Analysis:
   - Instances Active: ${instances}
   - Test Duration: ${duration.toFixed(1)}s
   - Distribution: NGINX Round Robin
   - Throughput: ${(httpReqs / (duration/60)).toFixed(1)} req/min

COMPARATIVE DATA:
Instances=${instances}|RPS=${rps.toFixed(2)}|P95=${p95Latency.toFixed(2)}|Errors=${errorRate.toFixed(2)}%|AvgLatency=${avgLatency.toFixed(2)}

Expected Improvements with more instances:
   - Higher RPS (better throughput)
   - Lower P95 latency (load distribution)
   - Better fault tolerance
   - Reduced error rates under load
`,
        [`load-balance-${instances}instances.json`]: JSON.stringify({
            instances: Number.parseInt(instances),
            rps: rps,
            p95_latency: p95Latency,
            avg_latency: avgLatency,
            error_rate: errorRate,
            total_requests: httpReqs,
            max_vus: maxVus,
            duration: duration
        }, null, 2),
    };
}