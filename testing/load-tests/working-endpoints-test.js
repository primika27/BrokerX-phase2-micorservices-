import http from 'k6/http';
import { check } from 'k6';

const INSTANCE_COUNT = __ENV.INSTANCES || "2";
const BASE_URL = 'http://localhost:80';

export let options = {
    stages: [
        { duration: '30s', target: 10 }, // Montée progressive
        { duration: '1m', target: 20 },  // Charge stable
        { duration: '30s', target: 0 },  // Descente
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% des requêtes sous 500ms
        http_req_failed: ['rate<0.01'],   // Moins de 1% d'erreurs
    }
};

// Endpoints qui fonctionnent sans erreur 500
const testEndpoints = [
    { path: '/health', weight: 40, description: 'Health Check' },
    { path: '/nginx-health', weight: 30, description: 'NGINX Health' },
    { path: '/actuator/health', weight: 30, description: 'Actuator Health' }
];

// Sélection pondérée d'endpoint
function selectEndpoint() {
    const totalWeight = testEndpoints.reduce((sum, ep) => sum + ep.weight, 0);
    const random = Math.random() * totalWeight;
    
    let cumWeight = 0;
    for (const endpoint of testEndpoints) {
        cumWeight += endpoint.weight;
        if (random <= cumWeight) {
            return endpoint.path;
        }
    }
    return testEndpoints[0].path; // fallback
}

export default function() {
    const endpoint = selectEndpoint();
    
    const headers = {
        'X-Load-Test': 'working-endpoints-baseline',
        'X-Instances': INSTANCE_COUNT,
        'Accept': 'application/json'
    };
    
    const response = http.get(`${BASE_URL}${endpoint}`, {
        headers: headers,
        timeout: '10s'
    });
    
    // Vérifications simples
    const isSuccess = check(response, {
        'status is 200': (r) => r.status === 200,
        'response time < 500ms': (r) => r.timings.duration < 500,
        'has response body': (r) => r.body && r.body.length > 0,
    });
    
    if (!isSuccess) {
        console.log(`❌ ${endpoint} failed: ${response.status} - ${response.body}`);
    }
}

export function handleSummary(data) {
    const totalRequests = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
    const avgDuration = data.metrics.http_req_duration ? data.metrics.http_req_duration.values.avg : 0;
    const p95Duration = data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : 0;
    const failedRequests = data.metrics.http_req_failed ? data.metrics.http_req_failed.values.passes : 0;
    const errorRate = totalRequests > 0 ? (failedRequests / totalRequests) * 100 : 0;
    
    const rps = totalRequests > 0 ? totalRequests / (data.state.testRunDuration / 1000) : 0;
    
    console.log(`
🔧 BASELINE TEST RESULTS (${INSTANCE_COUNT} instances)
═══════════════════════════════════════════════════
📊 Total Requests: ${totalRequests}
⚡ RPS: ${rps.toFixed(2)}
⏱️  Avg Latency: ${avgDuration.toFixed(2)}ms
📈 P95 Latency: ${p95Duration.toFixed(2)}ms
❌ Error Rate: ${errorRate.toFixed(2)}%
🕒 Duration: ${(data.state.testRunDuration / 1000).toFixed(1)}s
    `);
    
    // Retour pour collecte JSON
    return {
        'working-endpoints-results.json': JSON.stringify({
            instances: INSTANCE_COUNT,
            test_type: "working_endpoints_baseline",
            timestamp: new Date().toISOString(),
            metrics: {
                total_requests: totalRequests,
                rps: parseFloat(rps.toFixed(2)),
                avg_latency_ms: parseFloat(avgDuration.toFixed(2)),
                p95_latency_ms: parseFloat(p95Duration.toFixed(2)),
                error_rate_percent: parseFloat(errorRate.toFixed(2)),
                duration_seconds: parseFloat((data.state.testRunDuration / 1000).toFixed(1))
            }
        }, null, 2)
    };
}