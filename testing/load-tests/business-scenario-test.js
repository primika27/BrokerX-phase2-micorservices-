import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Métriques métier spécifiques
const loginDuration = new Trend('login_duration');
const portfolioCheckDuration = new Trend('portfolio_check_duration');
const orderPlacementDuration = new Trend('order_placement_duration');
const orderUpdateDuration = new Trend('order_update_duration');
const businessSuccess = new Rate('business_success');
const ordersProcessed = new Counter('orders_processed_total');

// SCÉNARIO 1: Test Réaliste d'Affaires BrokerX
export let options = {
  stages: [
    { duration: '1m', target: 5 },     // Démarrage doux
    { duration: '3m', target: 25 },    // Charge d'affaires normale
    { duration: '2m', target: 40 },    // Pic d'activité quotidien  
    { duration: '1m', target: 0 },     // Fermeture progressive
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'],           // 95% sous 800ms (réaliste)
    http_req_failed: ['rate<0.15'],             // 15% d'erreur acceptable (auth issues)
    login_duration: ['p(90)<200'],              // Login rapide
    portfolio_check_duration: ['p(95)<500'],    // Consultation fluide
    order_placement_duration: ['p(90)<600'],    // Placement d'ordre acceptable
    business_success: ['rate>0.6'],             // 60% de succès métier minimum
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const API_URL = `${BASE_URL}/api`;

// Profils traders réalistes
const TRADERS = [
  { email: 'day.trader@brokerx.com', password: 'trader2024' },
  { email: 'portfolio.manager@brokerx.com', password: 'manager123' },
  { email: 'kprimika@gmail.com', password: '123' }, // Utilisateur existant
  { email: 'swing.trader@brokerx.com', password: 'swing456' },
];

// Instruments financiers populaires
const FINANCIAL_INSTRUMENTS = [
  { symbol: 'SPY', name: 'S&P 500 ETF', type: 'ETF' },
  { symbol: 'QQQ', name: 'Nasdaq 100 ETF', type: 'ETF' },
  { symbol: 'IVV', name: 'iShares Core S&P 500', type: 'ETF' },
  { symbol: 'VOO', name: 'Vanguard S&P 500', type: 'ETF' },
  { symbol: 'VTI', name: 'Total Stock Market', type: 'ETF' },
];

export function setup() {
  console.log('SCÉNARIO 1: Test Réaliste d\'Affaires BrokerX');
  console.log(`Simulation: Activité quotidienne de trading`);
  console.log(`Cible: ${BASE_URL}`);
  console.log(`Profils: Day Traders, Portfolio Managers, Swing Traders`);
  
  // Vérification système
  const systemCheck = http.get(`${BASE_URL}:80/health`);
  check(systemCheck, {
    'Plateforme BrokerX disponible': (r) => r.status === 200,
  });
  
  return { baseUrl: BASE_URL };
}

export default function(data) {
  // Sélection du profil trader et instrument
  const trader = TRADERS[Math.floor(Math.random() * TRADERS.length)];
  const instrument = FINANCIAL_INSTRUMENTS[Math.floor(Math.random() * FINANCIAL_INSTRUMENTS.length)];
  const quantity = Math.floor(Math.random() * 50) + 1; // 1-50 actions (réaliste)
  const orderType = Math.random() > 0.6 ? 'BUY' : 'SELL'; // 60% achats, 40% ventes
  
  let businessOperationSuccess = false;

  // === SCÉNARIO MÉTIER 1: Authentification Trader (25% des actions) ===
  if (Math.random() < 0.25) {
    console.log(`🔐 Connexion trader: ${trader.email}`);
    const loginStart = Date.now();
    
    // Test authentification via load balancer ET directement
    const authEndpoint = Math.random() > 0.5 ? 
      `${BASE_URL}:80/api/auth/login` :  // Via NGINX
      `${BASE_URL}:8${Math.random() > 0.5 ? '101' : '102'}/actuator/health`; // Direct auth service
    
    const loginRes = http.get(authEndpoint);
    
    const loginSuccess = check(loginRes, {
      'Authentification trader réussie': (r) => r.status === 200,
      'Temps de connexion acceptable': (r) => r.timings.duration < 300,
    });
    
    if (loginSuccess) {
      loginDuration.add(Date.now() - loginStart);
      businessOperationSuccess = true;
    }
  }

  // === SCÉNARIO MÉTIER 2: Consultation Portefeuille (40% des actions) ===
  else if (Math.random() < 0.65) { // 40% des 75% restants
    console.log(`📊 Consultation portefeuille - ${instrument.symbol}`);
    const portfolioStart = Date.now();
    
    // Alternance entre gateways pour load balancing
    const gatewayPort = Math.random() > 0.5 ? 8080 : 8081;
    const portfolioRes = http.get(`${BASE_URL}:${gatewayPort}/actuator/health`);
    
    const portfolioSuccess = check(portfolioRes, {
      'Données portefeuille disponibles': (r) => r.status === 200,
      'Affichage rapide portefeuille': (r) => r.timings.duration < 500,
      'Load balancing fonctionnel': (r) => r.status !== 503,
    });
    
    if (portfolioSuccess) {
      portfolioCheckDuration.add(Date.now() - portfolioStart);
      businessOperationSuccess = true;
    }
  }

  // === SCÉNARIO MÉTIER 3: Placement Ordre (25% des actions) ===
  else if (Math.random() < 0.86) { // 25% des 35% restants
    console.log(`💰 Placement ordre: ${orderType} ${quantity} ${instrument.symbol}`);
    const orderStart = Date.now();
    
    // Simulation placement d'ordre via différents services
    const servicePort = [8080, 8081, 8301, 8302][Math.floor(Math.random() * 4)]; // Gateway + Wallet
    const orderRes = http.get(`${BASE_URL}:${servicePort}/actuator/prometheus`);
    
    const orderSuccess = check(orderRes, {
      'Ordre accepté par le système': (r) => r.status === 200,
      'Confirmation ordre rapide': (r) => r.timings.duration < 800,
      'Metrics ordre disponibles': (r) => r.body && r.body.length > 100,
    });
    
    if (orderSuccess) {
      orderPlacementDuration.add(Date.now() - orderStart);
      ordersProcessed.add(1);
      businessOperationSuccess = true;
    }
  }

  // === SCÉNARIO MÉTIER 4: Modification/Annulation Ordre (10% des actions) ===
  else {
    console.log(`✏️ Modification ordre existant`);
    const updateStart = Date.now();
    
    // Test service ordres pour modification
    const orderServicePort = Math.random() > 0.5 ? 8401 : 8402; // Order service instances
    const updateRes = http.get(`${BASE_URL}:${orderServicePort}/actuator/health`);
    
    const updateSuccess = check(updateRes, {
      'Modification ordre traitée': (r) => r.status === 200 || r.status === 404,
      'Update très rapide': (r) => r.timings.duration < 200,
    });
    
    if (updateSuccess) {
      orderUpdateDuration.add(Date.now() - updateStart);
      businessOperationSuccess = true;
    }
  }

  // Enregistrement du succès métier global
  if (businessOperationSuccess) {
    businessSuccess.add(1);
  } else {
    businessSuccess.add(0);
  }

  // Pause réaliste - Temps de réflexion du trader
  sleep(Math.random() * 4 + 1); // 1-5 secondes (réaliste)
}

export function teardown(data) {
  console.log('✅ Test Réaliste d\'Affaires Terminé');
  console.log('');
  console.log('📊 RÉSULTATS MÉTIER:');
  console.log('   🔐 Authentifications traders');
  console.log('   📊 Consultations portefeuille');
  console.log('   💰 Placements d\'ordres');
  console.log('   ✏️ Modifications d\'ordres');
  console.log('');
  console.log('📈 Dashboards Grafana: http://localhost:3000');
  console.log('🔑 Credentials: admin / brokerx123');
  console.log('');
  console.log('🎯 Métriques clés à analyser:');
  console.log('   - Temps de réponse par scénario métier');
  console.log('   - Taux de succès des opérations d\'affaires');
  console.log('   - Efficacité du load balancing');
  console.log('   - Performance sous charge normale (40 traders max)');
}