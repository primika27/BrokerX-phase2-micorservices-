import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics for stress testing
const systemStabilityRate = new Rate('system_stability');
const responseTimeP99 = new Trend('response_time_p99');
const errorRateMetric = new Rate('error_rate');
const throughputCounter = new Counter('total_requests');

// Aggressive stress test configuration
export let options = {
  stages: [
    { duration: '1m', target: 100 },   // Baseline
    { duration: '2m', target: 500 },   // Ramp up
    { duration: '3m', target: 1000 },  // High stress
    { duration: '5m', target: 2000 },  // Maximum stress
    { duration: '3m', target: 1000 },  // Step down
    { duration: '2m', target: 500 },   // Recovery
    { duration: '1m', target: 0 },     // Cool down
  ],
  thresholds: {
    http_req_duration: ['p(99)<2000'], // 99th percentile under 2s
    http_req_failed: ['rate<0.20'],    // Error rate under 20% (stress test)
    system_stability: ['rate>0.80'],   // 80% stability under stress
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const API_URL = `${BASE_URL}/api`;

// Stress test endpoints
const STRESS_ENDPOINTS = [
  { path: '/market/quotes/AAPL', weight: 0.4 },
  { path: '/orders/recent', weight: 0.3 },
  { path: '/wallet/transactions', weight: 0.2 },
  { path: '/auth/validate', weight: 0.1 },
];

export function setup() {
  console.log('Starting BrokerX Stress Test');
  console.log('WARNING: This is a high-load stress test!');
  
  // System health check
  const healthRes = http.get(`${API_URL}/health`);
  const systemHealthy = check(healthRes, {
    'System initially healthy': (r) => r.status === 200,
  });
  
  if (!systemHealthy) {
    throw new Error('System not healthy before stress test');
  }
  
  return { startTime: new Date() };
}

export default function(data) {
  const startTime = Date.now();
  
  // Select endpoint based on weight
  let cumulativeWeight = 0;
  const random = Math.random();
  let selectedEndpoint = STRESS_ENDPOINTS[0];
  
  for (const endpoint of STRESS_ENDPOINTS) {
    cumulativeWeight += endpoint.weight;
    if (random <= cumulativeWeight) {
      selectedEndpoint = endpoint;
      break;
    }
  }
  
  // Make the request
  const response = http.get(`${API_URL}${selectedEndpoint.path}`, {
    timeout: '30s',
  });
  
  const responseTime = Date.now() - startTime;
  responseTimeP99.add(responseTime);
  throughputCounter.add(1);
  
  // Check system stability
  const isStable = check(response, {
    'Response received': (r) => r.status !== 0,
    'Not server error': (r) => r.status < 500,
    'Response time acceptable': () => responseTime < 5000,
  });
  
  systemStabilityRate.add(isStable);
  
  if (!isStable) {
    errorRateMetric.add(1);
  }
  
  // Minimal sleep for maximum stress
  sleep(0.1);
}

export function teardown(data) {
  console.log('Stress test completed');
  console.log(`Test duration: ${(new Date() - data.startTime) / 1000}s`);
  
  // Final health check
  const finalHealthRes = http.get(`${BASE_URL}/api/health`);
  const stillHealthy = check(finalHealthRes, {
    'System healthy after stress': (r) => r.status === 200,
  });
  
  if (!stillHealthy) {
    console.warn('WARNING: System may be degraded after stress test');
  }
}