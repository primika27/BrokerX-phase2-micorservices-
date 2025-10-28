import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    scenarios: {
        quick_test: {
            executor: 'constant-vus',
            vus: 10,
            duration: '1m',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<500'],
        'http_req_failed': ['rate<0.1'],
    },
};

const BASE_URL = 'http://localhost:80';
const INSTANCE_COUNT = __ENV.INSTANCES || '2';

const testEndpoints = [
    '/health',
    '/nginx-health', 
    '/api/wallet/portfolio/SPY',
    '/api/auth/health',
];

export default function() {
    const endpoint = testEndpoints[Math.floor(Math.random() * testEndpoints.length)];
    
    const response = http.get(`${BASE_URL}${endpoint}`, {
        headers: { 'X-Load-Test': 'true' },
        timeout: '5s'
    });
    
    check(response, {
        'Load balancer responsive': (r) => r.status !== 0,
        'No 5xx errors': (r) => r.status < 500,
    });
    
    sleep(0.1);
}

export function handleSummary(data) {
    const instances = INSTANCE_COUNT;
    const duration = data.state.testRunDurationMs / 1000;
    const httpReqs = data.metrics.http_reqs && data.metrics.http_reqs.values && data.metrics.http_reqs.values.count || 0;
    const rps = data.metrics.http_reqs && data.metrics.http_reqs.values && data.metrics.http_reqs.values.rate || 0;
    const p95Latency = data.metrics['http_req_duration'] && data.metrics['http_req_duration'].values && data.metrics['http_req_duration'].values['p(95)'] || 0;
    const errorRate = (data.metrics['http_req_failed'] && data.metrics['http_req_failed'].values && data.metrics['http_req_failed'].values.rate || 0) * 100;
    const avgLatency = data.metrics['http_req_duration'] && data.metrics['http_req_duration'].values && data.metrics['http_req_duration'].values.avg || 0;
    const maxVus = data.metrics.vus_max && data.metrics.vus_max.values && data.metrics.vus_max.values.max || 0;
    
    return {
        'stdout': `
⚖️ QUICK TEST RESULTS - ${instances} Instance(s)
===============================================
🎯 Performance Metrics:
   ✅ Total Requests: ${httpReqs}
   ✅ Requests/sec: ${rps.toFixed(2)}
   ✅ Average Latency: ${avgLatency.toFixed(2)}ms
   ✅ P95 Latency: ${p95Latency.toFixed(2)}ms
   ✅ Error Rate: ${errorRate.toFixed(2)}%
   ✅ Max VUs: ${maxVus}

📊 Load Balancer Analysis:
   - Test Duration: ${duration.toFixed(1)}s
   - Throughput: ${(httpReqs / (duration/60)).toFixed(1)} req/min
   - Distribution: NGINX Round Robin
`,
        [`quick-test-${instances}instances.json`]: JSON.stringify({
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