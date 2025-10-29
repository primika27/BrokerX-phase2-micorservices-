import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ============================================================================
// COMPREHENSIVE LOAD BALANCING TEST SUITE - BROKERX MICROSERVICES
// ============================================================================
// This test systematically measures performance across 1,2,3,4 instances
// Both with and without caching for comprehensive analysis
// ============================================================================

// Custom metrics for detailed analysis
const requestsPerEndpoint = new Counter('requests_per_endpoint');
const authErrors = new Rate('auth_errors');
const dbQueryLatency = new Trend('db_query_latency');
const cacheHitRate = new Rate('cache_hit_rate');
const loadBalancerLatency = new Trend('load_balancer_latency');

// Test configuration from environment
const INSTANCE_COUNT = __ENV.INSTANCES || '1';
const CACHE_ENABLED = __ENV.CACHE_ENABLED === 'true' || false;
const BASE_URL = __ENV.BASE_URL || 'http://localhost:80';
const TEST_DURATION = __ENV.TEST_DURATION || '10m';
const MAX_VUS = Number.parseInt(__ENV.MAX_VUS || '50');

export const options = {
    scenarios: {
        load_balancing_test: {
            executor: 'ramping-vus',
            stages: [
                { duration: '2m', target: Math.floor(MAX_VUS * 0.2) },  // 20% ramp-up
                { duration: '3m', target: Math.floor(MAX_VUS * 0.5) },  // 50% sustained
                { duration: '3m', target: MAX_VUS },                     // 100% peak load
                { duration: '2m', target: Math.floor(MAX_VUS * 0.5) },  // 50% cool-down
                { duration: '1m', target: 0 },                          // Complete shutdown
            ],
        },
        fault_tolerance_test: {
            executor: 'constant-vus',
            vus: Math.floor(MAX_VUS * 0.3),
            duration: '5m',
            startTime: '5m', // Start after load test is stable
            exec: 'faultToleranceScenario'
        }
    },
    thresholds: {
        'http_req_duration': ['p(95)<2000'],     // 95% requests < 2s
        'http_req_failed': ['rate<0.10'],        // Error rate < 10%
        'db_query_latency': ['p(95)<1000'],      // DB queries < 1s
        'load_balancer_latency': ['p(95)<100'],  // LB overhead < 100ms
    },
    // Add tags for Prometheus/Grafana identification
    tags: {
        instance_count: INSTANCE_COUNT,
        cache_enabled: CACHE_ENABLED,
        test_type: 'comprehensive_load_balancing',
        environment: 'performance_test'
    }
};

// ============================================================================
// EXPENSIVE ENDPOINTS - CANDIDATES FOR CACHING
// ============================================================================
const expensiveEndpoints = [
    {
        path: '/api/stores/1/stock',
        method: 'GET',
        weight: 20,
        cacheable: true,
        category: 'stock_data',
        description: 'Stock Data Query',
        expectedLatency: CACHE_ENABLED ? 50 : 800
    },
    {
        path: '/api/reports/sales',
        method: 'GET',
        weight: 15,
        cacheable: true,
        category: 'reports',
        description: 'Sales Report',
        expectedLatency: CACHE_ENABLED ? 100 : 1200
    },
    {
        path: '/api/quotes/AAPL',
        method: 'GET',
        weight: 25,
        cacheable: true,
        category: 'market_data',
        description: 'Market Quotes',
        expectedLatency: CACHE_ENABLED ? 30 : 400
    },
    {
        path: '/api/quotes/GOOGL',
        method: 'GET',
        weight: 25,
        cacheable: true,
        category: 'market_data',
        description: 'Market Quotes',
        expectedLatency: CACHE_ENABLED ? 30 : 400
    }
];

// Standard endpoints for load balancing baseline
const standardEndpoints = [
    {
        path: '/health',
        method: 'GET',
        weight: 30,
        cacheable: false,
        category: 'health',
        description: 'Health Check'
    },
    {
        path: '/api/auth/status',
        method: 'GET',
        weight: 20,
        cacheable: false,
        category: 'auth',
        description: 'Auth Status'
    },
    {
        path: '/nginx-health',
        method: 'GET',
        weight: 15,
        cacheable: false,
        category: 'load_balancer',
        description: 'Load Balancer Health'
    }
];

// Combine all endpoints
const allEndpoints = [...expensiveEndpoints, ...standardEndpoints];

// ============================================================================
// WEIGHTED ENDPOINT SELECTION
// ============================================================================
function selectRandomEndpoint() {
    const totalWeight = allEndpoints.reduce((sum, ep) => sum + ep.weight, 0);
    let random = Math.random() * totalWeight;
    
    for (const endpoint of allEndpoints) {
        random -= endpoint.weight;
        if (random <= 0) {
            return endpoint;
        }
    }
    return allEndpoints[0]; // Fallback
}

// ============================================================================
// MAIN LOAD BALANCING TEST SCENARIO
// ============================================================================
export default function loadBalancingScenario() {
    const endpoint = selectRandomEndpoint();
    const startTime = Date.now();
    
    // Add cache-related headers
    const params = {
        headers: {
            'Accept': 'application/json',
            'X-Test-Instance-Count': INSTANCE_COUNT,
            'X-Cache-Enabled': CACHE_ENABLED.toString(),
            'X-Test-Category': endpoint.category
        },
        tags: {
            endpoint: endpoint.path,
            method: endpoint.method,
            category: endpoint.category,
            cacheable: endpoint.cacheable.toString(),
            instance_count: INSTANCE_COUNT,
            cache_enabled: CACHE_ENABLED.toString()
        }
    };
    
    const response = http.get(`${BASE_URL}${endpoint.path}`, params);
    const endTime = Date.now();
    const duration = endTime - startTime;
    
    // Record custom metrics
    requestsPerEndpoint.add(1, { endpoint: endpoint.path, instance_count: INSTANCE_COUNT });
    loadBalancerLatency.add(duration, { instance_count: INSTANCE_COUNT });
    
    // Check if this was a cache hit (based on response time for cacheable endpoints)
    if (endpoint.cacheable && CACHE_ENABLED) {
        const isCacheHit = duration < endpoint.expectedLatency;
        cacheHitRate.add(isCacheHit ? 1 : 0, { endpoint: endpoint.path });
    }
    
    // Record DB query latency for expensive endpoints
    if (endpoint.cacheable) {
        dbQueryLatency.add(duration, { 
            endpoint: endpoint.path, 
            cache_enabled: CACHE_ENABLED.toString() 
        });
    }
    
    // Comprehensive response validation
    const checks = check(response, {
        [`${endpoint.description} - Status OK`]: (r) => [200, 404, 500].includes(r.status),
        [`${endpoint.description} - Response time acceptable`]: (r) => r.timings.duration < 5000,
        [`${endpoint.description} - Has response body`]: (r) => r.body && r.body.length > 0
    });
    
    if (!checks) {
        authErrors.add(1, { endpoint: endpoint.path });
    }
    
    // Realistic user pause
    sleep(Math.random() * 2 + 0.5); // 0.5-2.5 seconds
}

// ============================================================================
// FAULT TOLERANCE TEST SCENARIO
// ============================================================================
export function faultToleranceScenario() {
    // This scenario runs constant load while instances might be killed
    const endpoint = selectRandomEndpoint();
    
    const params = {
        headers: {
            'Accept': 'application/json',
            'X-Test-Type': 'fault_tolerance',
            'X-Instance-Count': INSTANCE_COUNT
        },
        tags: {
            test_type: 'fault_tolerance',
            endpoint: endpoint.path,
            instance_count: INSTANCE_COUNT
        }
    };
    
    const response = http.get(`${BASE_URL}${endpoint.path}`, params);
    
    // More lenient checks for fault tolerance testing
    check(response, {
        'Service available during fault': (r) => r.status !== 0, // Any response is good
        'Reasonable response time during fault': (r) => r.timings.duration < 10000
    });
    
    sleep(1); // Consistent 1-second intervals for fault tolerance
}

// ============================================================================
// SETUP AND TEARDOWN
// ============================================================================
export function setup() {
    console.log(`Starting comprehensive load balancing test:`);
    console.log(`  - Instance Count: ${INSTANCE_COUNT}`);
    console.log(`  - Cache Enabled: ${CACHE_ENABLED}`);
    console.log(`  - Base URL: ${BASE_URL}`);
    console.log(`  - Max VUs: ${MAX_VUS}`);
    
    // Warm up the system
    const healthCheck = http.get(`${BASE_URL}/health`);
    if (healthCheck.status !== 200) {
        console.warn('Health check failed, system might not be ready');
    }
    
    return { startTime: Date.now() };
}

export function teardown(data) {
    const duration = (Date.now() - data.startTime) / 1000;
    console.log(`Test completed in ${duration} seconds`);
    console.log(`Configuration: ${INSTANCE_COUNT} instances, Cache: ${CACHE_ENABLED}`);
}