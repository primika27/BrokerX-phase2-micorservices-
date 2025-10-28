import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Métriques personnalisées pour l'analyse comparative
const latencyByInstance = new Trend('latency_by_instance', true);
const rpsCounter = new Counter('requests_per_second');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time_ms');
const loadBalancerHealth = new Rate('load_balancer_health');

// Configuration des tests selon le nombre d'instances
export let options = {
    scenarios: {
        load_test: {
            executor: 'ramping-vus',
            stages: [
                { duration: '2m', target: 20 },  // Montée progressive
                { duration: '3m', target: 50 },  // Charge soutenue
                { duration: '2m', target: 80 },  // Pic de charge
                { duration: '1m', target: 0 },   // Descente
            ],
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<500', 'p(99)<1000'],
        'http_req_failed': ['rate<0.05'], // Moins de 5% d'erreurs
        'error_rate': ['rate<0.05'],
        'load_balancer_health': ['rate>0.95'], // 95% de succès LB
    },
};

const INSTANCE_COUNT = __ENV.INSTANCES || '2'; // Par défaut 2 instances
const BASE_URL = 'http://localhost:80';

// Liste des ETFs pour les tests
const etfs = ['SPY', 'VTI', 'IVV', 'QQQ', 'VOO', 'ARKK', 'VEA', 'VWO'];
const traders = [
    'swing.trader@brokerx.com',
    'day.trader@brokerx.com', 
    'portfolio.manager@brokerx.com',
    'kprimika@gmail.com'
];

export default function() {
    const startTime = Date.now();
    
    // Test 1: Health Check du Load Balancer
    let healthResponse = http.get(`${BASE_URL}/nginx-health`);
    
    let healthCheck = check(healthResponse, {
        'Load balancer healthy': (r) => r.status === 200,
        'Instance count correct': (r) => {
            if (r.body) {
                try {
                    const data = JSON.parse(r.body);
                    return data.instances.toString() === INSTANCE_COUNT;
                } catch (e) {
                    return false;
                }
            }
            return false;
        }
    });
    
    loadBalancerHealth.add(healthCheck);
    
    // Test 2: Focus sur les métriques de load balancing (60% portfolio, 30% orders, 10% auth health)
    const rand = Math.random();
    if (rand < 0.6) {
        testPortfolioConsultation();
    } else if (rand < 0.9) {
        testOrderPlacement();
    } else {
        testTraderAuthentication();
    }
    
    // Test 3: Vérification système
    testSystemHealth();
    
    const endTime = Date.now();
    const duration = endTime - startTime;
    
    // Enregistrement des métriques
    latencyByInstance.add(duration, { instances: INSTANCE_COUNT });
    responseTime.add(duration);
    rpsCounter.add(1);
    
    sleep(Math.random() + 0.5); // 0.5-1.5 secondes entre requêtes
}

function testPortfolioConsultation() {
    const etf = etfs[Math.floor(Math.random() * etfs.length)];
    
    let response = http.get(`${BASE_URL}/api/wallet/portfolio/${etf}`, {
        headers: { 
            'Accept': 'application/json',
            'X-Test-Scenario': 'portfolio_consultation',
            'X-Bypass-Auth': 'true'
        },
    });
    
    let success = check(response, {
        'Load balancer responding': (r) => r.status !== 0,
        'Fast response': (r) => r.timings.duration < 500,
        'Load balancing active': (r) => r.status === 200 || r.status === 404 || r.status === 401,
    });
    
    if (!success) errorRate.add(1);
    console.log(` Portfolio ${etf} - LB distribué (${INSTANCE_COUNT} instances)`);
}

function testOrderPlacement() {
    const etf = etfs[Math.floor(Math.random() * etfs.length)];
    const side = Math.random() > 0.5 ? 'BUY' : 'SELL';
    const quantity = Math.floor(Math.random() * 50) + 1;
    
    const orderData = {
        symbol: etf,
        side: side,
        quantity: quantity,
        type: 'MARKET'
    };
    
    let response = http.post(`${BASE_URL}/api/order/place`, JSON.stringify(orderData), {
        headers: { 
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'X-Test-Scenario': 'order_placement',
            'X-Bypass-Auth': 'true'
        },
    });
    
    let success = check(response, {
        'Order service responding': (r) => r.status !== 0,
        'Load balancer working': (r) => r.status < 500,
        'Response time acceptable': (r) => r.timings.duration < 800,
    });
    
    if (!success) errorRate.add(1);
    console.log(`Ordre ${side} ${quantity} ${etf} - LB actif (${INSTANCE_COUNT} instances)`);
}

function testTraderAuthentication() {
    // Test simplifié - ping du service auth sans vraie auth
    let response = http.get(`${BASE_URL}/api/auth/health`, {
        headers: { 
            'Accept': 'application/json',
            'X-Test-Scenario': 'auth_health_check',
            'X-Bypass-Auth': 'true'
        },
    });
    
    let success = check(response, {
        'Auth service responding': (r) => r.status !== 0,
        'Load balancer routing': (r) => r.status < 500,
        'Fast auth response': (r) => r.timings.duration < 300,
    });
    
    if (!success) errorRate.add(1);
    console.log(`Auth service health - LB routage (${INSTANCE_COUNT} instances)`);
}

function testSystemHealth() {
    let response = http.get(`${BASE_URL}/health`, {
        headers: { 'X-Test-Scenario': 'health_check' },
    });
    
    let success = check(response, {
        'System health OK': (r) => r.status === 200,
        'Fast health response': (r) => r.timings.duration < 100,
    });
    
    if (!success) errorRate.add(1);
}

export function handleSummary(data) {
    const instances = INSTANCE_COUNT;
    const rps = (data.metrics.requests_per_second && data.metrics.requests_per_second.count || 0) / (data.state.testRunDurationMs / 1000);
    const avgLatency = data.metrics.latency_by_instance && data.metrics.latency_by_instance.avg || 0;
    const errorRate = (data.metrics.error_rate && data.metrics.error_rate.rate || 0) * 100;
    const p95Latency = data.metrics['http_req_duration'] && data.metrics['http_req_duration']['p(95)'] || 0;
    const p99Latency = data.metrics['http_req_duration'] && data.metrics['http_req_duration']['p(99)'] || 0;
    const lbHealth = (data.metrics.load_balancer_health && data.metrics.load_balancer_health.rate || 0) * 100;
    const maxVus = data.metrics.vus_max && data.metrics.vus_max.max || 0;
    const iterations = data.metrics.iterations && data.metrics.iterations.count || 0;
    const durationMin = (data.state.testRunDurationMs / 1000 / 60);
    
    return {
        'stdout': `
RÉSULTATS COMPARATIFS - ${instances} Instance(s)
=============================================
Performance Globale:
   - Requests/sec: ${rps.toFixed(2)}
   - Latence moyenne: ${avgLatency.toFixed(2)}ms
   - Taux d'erreur: ${errorRate.toFixed(2)}%
   - P95 latence: ${p95Latency.toFixed(2)}ms
   - P99 latence: ${p99Latency.toFixed(2)}ms
 Métriques Load Balancer:
   - Santé LB: ${lbHealth.toFixed(2)}%
   - Instances actives: ${instances}
   
Utilisation:
   - VUs max: ${maxVus}
   - Itérations: ${iterations}
   - Durée totale: ${durationMin.toFixed(1)} minutes

 Pour graphiques comparatifs:
   Instances=${instances}, RPS=${rps.toFixed(2)}, Latency_P95=${p95Latency.toFixed(2)}, ErrorRate=${errorRate.toFixed(2)}%
`,
        [`results-${instances}instances.json`]: JSON.stringify(data, null, 2),
    };
}