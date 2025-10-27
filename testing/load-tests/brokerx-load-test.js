import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const loginDuration = new Trend('login_duration');
const orderPlacementDuration = new Trend('order_placement_duration');
const walletCheckDuration = new Trend('wallet_check_duration');
const errorRate = new Rate('error_rate');
const orderCount = new Counter('orders_placed');

// Test configuration
export let options = {
  stages: [
    { duration: '2m', target: 50 },    // Warm up
    { duration: '5m', target: 100 },   // Normal load
    { duration: '3m', target: 200 },   // Peak load
    { duration: '5m', target: 200 },   // Sustained peak
    { duration: '2m', target: 0 },     // Cool down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests under 500ms
    http_req_failed: ['rate<0.05'],    // Error rate under 5%
    login_duration: ['p(95)<200'],     // Login under 200ms for 95%
    order_placement_duration: ['p(95)<300'], // Order placement under 300ms
    error_rate: ['rate<0.01'],         // Custom error rate under 1%
  },
};

// Base URL configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const API_URL = `${BASE_URL}/api`;

// Test data - matches your actual system
const TEST_USERS = [
  { email: 'kprimika@gmail.com', password: '123' },  // Your existing user
  { email: 'test@example.com', password: 'test123' },
  { email: 'demo@brokerx.com', password: 'demo123' },
];

// ETF symbols that match your OrderController ETF_PRICES
const STOCKS = ['SPY', 'IVV', 'VOO', 'VTI', 'QQQ', 'BND', 'IWM', 'EFA'];

export function setup() {
  console.log('Starting BrokerX Load Test');
  console.log(`Target URL: ${BASE_URL}`);
  
  // Warm up the system
  const warmupRes = http.get(`${API_URL}/health`);
  check(warmupRes, {
    'Warmup successful': (r) => r.status === 200,
  });
  
  return { baseUrl: BASE_URL };
}

export default function(data) {
  // Select random user and stock
  const user = TEST_USERS[Math.floor(Math.random() * TEST_USERS.length)];
  const stock = STOCKS[Math.floor(Math.random() * STOCKS.length)];
  
  let token = '';
  
  // 1. Login flow (20% of requests)
  if (Math.random() < 0.2) {
    const loginStart = Date.now();
    const loginPayload = JSON.stringify({
      email: user.email,
      password: user.password
    });
    
    const loginRes = http.post(`${API_URL}/auth/login`, loginPayload, {
      headers: { 'Content-Type': 'application/json' }
    });
    
    const loginSuccess = check(loginRes, {
      'Login status is 200': (r) => r.status === 200,
      'Login has token': (r) => r.json('token') !== undefined,
    });
    
    if (loginSuccess) {
      token = loginRes.json('token');
      loginDuration.add(Date.now() - loginStart);
    } else {
      errorRate.add(1);
    }
  }
  
  // 2. Check wallet balance (30% of requests)  
  if (Math.random() < 0.3 && token) {
    const walletStart = Date.now();
    const walletRes = http.get(`${API_URL}/wallet/balance`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    
    const walletSuccess = check(walletRes, {
      'Wallet check status is 200': (r) => r.status === 200,
      'Has balance data': (r) => r.body !== null && r.body !== '',
    });
    
    if (walletSuccess) {
      walletCheckDuration.add(Date.now() - walletStart);
    } else {
      errorRate.add(1);
      console.log(`Wallet check failed: ${walletRes.status} - ${walletRes.body}`);
    }
  }
  
  // 3. Check order holdings (40% of requests)
  if (Math.random() < 0.4 && token) {
    const holdingsRes = http.get(`${API_URL}/orders/holdings`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    
    check(holdingsRes, {
      'Holdings check status is 200': (r) => r.status === 200,
      'Has holdings data': (r) => r.body !== null,
    }) || errorRate.add(1);
  }
  
  // 4. Place order (10% of requests)
  if (Math.random() < 0.1 && token) {
    const orderStart = Date.now();
    const stockSymbol = stock;
    const quantity = Math.floor(Math.random() * 10) + 1; // 1-10 shares
    const orderType = Math.random() > 0.5 ? 'BUY' : 'SELL';
    
    // Use form parameters as expected by your OrderController
    const orderPayload = `symbol=${stockSymbol}&quantity=${quantity}&orderType=${orderType}`;
    
    const orderRes = http.post(`${API_URL}/orders/placeOrder`, orderPayload, {
      headers: { 
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': `Bearer ${token}`
      }
    });
    
    const orderSuccess = check(orderRes, {
      'Order placement successful': (r) => r.status === 200,
      'Order response contains success': (r) => r.body.includes('Order processed'),
    });
    
    if (orderSuccess) {
      orderPlacementDuration.add(Date.now() - orderStart);
      orderCount.add(1);
    } else {
      errorRate.add(1);
      console.log(`Order failed: ${orderRes.status} - ${orderRes.body}`);
    }
  }
  
  // Random sleep between requests
  sleep(Math.random() * 2);
}

export function teardown(data) {
  console.log('Load test completed');
}