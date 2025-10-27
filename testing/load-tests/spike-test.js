import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics for spike testing
const spikeRecoveryRate = new Rate('spike_recovery_rate');
const peakResponseTime = new Trend('peak_response_time');
const systemRecoveryTime = new Trend('system_recovery_time');
const requestsDuringSpike = new Counter('requests_during_spike');

// Spike test configuration - sudden traffic spikes
export const options = {
  stages: [
    { duration: '2m', target: 100 },   // Normal baseline
    { duration: '30s', target: 100 },  // Stay at baseline
    { duration: '10s', target: 1500 }, // SPIKE! Sudden traffic surge
    { duration: '1m', target: 1500 },  // Maintain spike
    { duration: '10s', target: 100 },  // Sudden drop
    { duration: '3m', target: 100 },   // Recovery period
    { duration: '30s', target: 0 },    // Cool down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95% under 1s during spike
    http_req_failed: ['rate<0.30'],    // Error rate under 30% (spike test)
    spike_recovery_rate: ['rate>0.70'], // 70% recovery rate
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const API_URL = `${BASE_URL}/api`;

// Critical endpoints that must survive spikes
const CRITICAL_ENDPOINTS = [
  { path: '/health', critical: true },
  { path: '/auth/login', critical: true },
  { path: '/orders/place', critical: true },
  { path: '/market/quotes/AAPL', critical: false },
  { path: '/wallet/balance', critical: false },
];

let spikeStartTime = null;
let inSpike = false;

export function setup() {
  console.log('Starting BrokerX Spike Test');
  console.log('This test simulates sudden traffic spikes');
  
  const healthRes = http.get(`${API_URL}/health`);
  check(healthRes, {
    'Pre-spike system health': (r) => r.status === 200,
  });
  
  return { testStart: Date.now() };
}

export default function spikeTest(data) {
  const currentVUs = __VU;
  const currentStage = __ITER;
  
  // Detect when we're in spike phase (more than 1000 VUs)
  if (currentVUs > 1000 && !inSpike) {
    inSpike = true;
    spikeStartTime = Date.now();
    console.log(`Spike detected at ${spikeStartTime}`);
  } else if (currentVUs <= 200 && inSpike) {
    inSpike = false;
    if (spikeStartTime) {
      systemRecoveryTime.add(Date.now() - spikeStartTime);
      console.log('Spike ended, measuring recovery');
    }
  }
  
  // Select endpoint - prioritize critical endpoints during spike
  let selectedEndpoint;
  if (inSpike) {
    // During spike, focus on critical endpoints
    const criticalEndpoints = CRITICAL_ENDPOINTS.filter(ep => ep.critical);
    selectedEndpoint = criticalEndpoints[Math.floor(Math.random() * criticalEndpoints.length)];
    requestsDuringSpike.add(1);
  } else {
    selectedEndpoint = CRITICAL_ENDPOINTS[Math.floor(Math.random() * CRITICAL_ENDPOINTS.length)];
  }
  
  const requestStart = Date.now();
  
  // Make request with appropriate timeout for spike conditions
  const timeout = inSpike ? '10s' : '5s';
  const response = http.get(`${API_URL}${selectedEndpoint.path}`, {
    timeout: timeout,
  });
  
  const responseTime = Date.now() - requestStart;
  
  if (inSpike) {
    peakResponseTime.add(responseTime);
  }
  
  // Check response quality
  const isHealthy = check(response, {
    'Response received': (r) => r.status !== 0,
    'Acceptable response time': () => responseTime < (inSpike ? 2000 : 500),
    'Not internal server error': (r) => r.status !== 500,
  });
  
  // Track recovery for critical endpoints
  if (selectedEndpoint.critical) {
    const recovered = check(response, {
      'Critical endpoint responsive': (r) => r.status === 200,
      'Critical endpoint fast enough': () => responseTime < 1000,
    });
    spikeRecoveryRate.add(recovered);
  }
  
  // Aggressive request pattern during spike
  if (inSpike) {
    sleep(Math.random() * 0.5); // Very short sleep during spike
  } else {
    sleep(Math.random() * 2);   // Normal sleep
  }
}

export function teardown(data) {
  console.log('Spike test completed');
  
  // Final system health assessment
  sleep(5); // Wait for system to stabilize
  
  const finalHealthRes = http.get(`${BASE_URL}/api/health`);
  const systemRecovered = check(finalHealthRes, {
    'System fully recovered': (r) => r.status === 200,
    'Health endpoint responsive': (r) => r.body.length > 0,
  });
  
  if (systemRecovered) {
    console.log('✅ System successfully recovered from spike');
  } else {
    console.warn('⚠️  System may need time to fully recover');
  }
  
  console.log(`Total test duration: ${(Date.now() - data.testStart) / 1000}s`);
}