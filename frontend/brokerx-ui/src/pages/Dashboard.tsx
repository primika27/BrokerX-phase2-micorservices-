import { useEffect, useState, useMemo } from "react";
import { useAuth } from "../lib/useAuth";
import { apiGet, apiPut, apiDelete } from "../lib/api";
import { useNavigate } from "react-router-dom";
import Navigation from "../components/Navigation";
import MarketQuotes from "../components/MarketQuotes";
import orderNotificationService, { type OrderUpdate } from "../services/OrderNotificationService";

type Me = { id: number; name: string; email: string; status?: string };
type Tx = { id: string; type: "DEPOSIT" | "WITHDRAWAL" | "FILL"; amount: number; createdAt: string };
type Holdings = { holdings: { [symbol: string]: number }; totalPositions: number };
type OrderStatus = { orders: Array<{ orderId: number; symbol: string; quantity: number; price: number; status: string; orderType: string }>; totalOrders: number };

export default function Dashboard() {
  const { jwt } = useAuth();
  const nav = useNavigate();
  const [me, setMe] = useState<Me | null>(null);
  const [balance, setBalance] = useState<number>(0);
  const [holdings, setHoldings] = useState<Holdings | null>(null);
  const [orderStatus, setOrderStatus] = useState<OrderStatus | null>(null);
  const [txs, setTxs] = useState<Tx[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [modifyingOrder, setModifyingOrder] = useState<number | null>(null);
  const [newPrice, setNewPrice] = useState<string>("");
  const [newQuantity, setNewQuantity] = useState<string>("");

  // Load financial data - called initially and after order updates
  const loadFinancialData = async () => {
    if (!jwt) {
      setLoading(false);
      return;
    }
    
    try {
      setLoading(true);
      setErr(null);
      console.log('Loading financial data...');
      
      const [balR, holdingsR, statusR] = await Promise.all([
        apiGet<number>("/api/wallet/balance", jwt).catch(e => { 
          console.error('Failed to fetch balance:', e); 
          return 0;
        }),
        apiGet<Holdings>("/api/orders/holdings", jwt).catch(e => { 
          console.error('Failed to fetch holdings:', e); 
          return { holdings: {}, totalPositions: 0 };
        }),
        apiGet<OrderStatus>("/api/orders/status", jwt).catch(e => { 
          console.error('Failed to fetch order status:', e); 
          return { orders: [], totalOrders: 0 };
        })
      ]);
      
      console.log('Financial data loaded:', { balR, holdingsR, statusR });
      setBalance(typeof balR === 'number' ? balR : 0);
      setHoldings(holdingsR || { holdings: {}, totalPositions: 0 });
      setOrderStatus(statusR || { orders: [], totalOrders: 0 });
      setTxs([]);
    } catch (e: unknown) {
      console.error('Financial data loading error:', e);
      setErr(String((e as Error).message || e));
    } finally {
      setLoading(false);
    }
  };

  // WebSocket connection for real-time order updates
  useEffect(() => {
    let isSubscribed = false;

    async function setupWebSocket() {
      if (!jwt) return;
      
      try {
        // Connect to OrderNotificationService
        await orderNotificationService.connect(jwt);
        
        // Subscribe to order updates
        orderNotificationService.subscribeToOrderUpdates((update: OrderUpdate) => {
          console.log('Received order update via WebSocket:', update);
          
          // Reload financial data when order status changes
          loadFinancialData();
          
          // Show notification to user
          if (update.message) {
            console.log(`Order #${update.orderId}: ${update.message}`);
          }
        });
        
        isSubscribed = true;
        console.log('WebSocket order notifications active');
      } catch (error) {
        console.error('Failed to setup WebSocket for orders:', error);
      }
    }

    setupWebSocket();

    return () => {
      if (isSubscribed) {
        orderNotificationService.disconnect();
      }
    };
  }, [jwt]);

  // Initial data load
  useEffect(() => {
    loadFinancialData();
  }, [jwt]);

  // Separate useEffect for user data (loads once only)
  useEffect(() => {
    async function loadUserData() {
      if (!jwt) return;
      
      try {
        setLoading(true);
        console.log('Loading user data...');
        
        const meR = await apiGet<Me>("/api/clients/me", jwt);
        setMe(meR);
      } catch (e: unknown) {
        console.error('User data loading error:', e);
        setErr(String((e as Error).message || e));
      } finally {
        setLoading(false);
      }
    }

    if (!me) {
      loadUserData();
    }
  }, [jwt, me]);

  // Memoized MarketQuotes to prevent unnecessary re-renders
  const marketQuotesComponent = useMemo(() => (
    <MarketQuotes userToken={jwt} />
  ), [jwt]);

  const handleCancelOrder = async (orderId: number) => {
    if (!jwt) return;
    if (!confirm(`Êtes-vous sûr de vouloir annuler la commande #${orderId}?`)) return;
    
    try {
      await apiDelete(`/api/orders/${orderId}`, jwt);
      alert('Commande annulée avec succès');
      // Refresh will happen automatically via interval
    } catch (e: unknown) {
      alert(`Erreur lors de l'annulation: ${(e as Error).message}`);
    }
  };

  const handleModifyOrder = async (orderId: number) => {
    if (!jwt) return;
    
    const price = newPrice ? parseFloat(newPrice) : null;
    const quantity = newQuantity ? parseInt(newQuantity) : null;
    
    if (!price && !quantity) {
      alert('Veuillez entrer au moins un nouveau prix ou une nouvelle quantité');
      return;
    }
    
    try {
      const params = new URLSearchParams();
      if (price) params.append('newPrice', price.toString());
      if (quantity) params.append('newQuantity', quantity.toString());
      
      await apiPut(`/api/orders/${orderId}?${params.toString()}`, jwt);
      alert('Commande modifiée avec succès');
      setModifyingOrder(null);
      setNewPrice("");
      setNewQuantity("");
      // WebSocket will automatically refresh data
    } catch (e: unknown) {
      alert(`Erreur lors de la modification: ${(e as Error).message}`);
    }
  };

  return (
    <>
      <Navigation />
      <main className="container">
        <header style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <h1 style={{ margin: 0 }}>Dashboard</h1>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ fontSize: "12px", color: "#666" }}>
              Market: Real-time WS | Orders: Real-time WS
          </span>
          <button onClick={() => loadFinancialData()} style={{ padding: "8px 16px", fontSize: "14px" }}>
            Manual Refresh
          </button>
        </div>
      </header>

      {loading && <p>Loading…</p>}
      {err && <p style={{ color: "crimson" }}>{err}</p>}

      {me && (
        <section style={{ marginTop: 16 }}>
          <h2 style={{ marginBottom: 8 }}>Welcome, {me.name || 'User'}</h2>
          <p style={{ opacity: 0.8 }}>{me.email}</p>
            <div style={{ marginTop: 12, display: "flex", gap: 12 }}>
                <button onClick={() => nav("/deposit")}>Deposit</button>
                <button onClick={() => nav("/placeOrder")}>Place Order</button>
                <button onClick={() => nav("/wallet/withdraw")}>Withdraw</button>
            </div>
        </section>
      )}

      <section style={{ marginTop: 24 }}>
        <h3>Account Balance</h3>
        <div style={{ padding: "16px", border: "1px solid #ccc", borderRadius: "8px" }}>
          <p style={{ fontSize: "24px", margin: 0, fontWeight: "bold" }}>
            ${typeof balance === 'number' ? balance.toFixed(2) : '0.00'} CAD
          </p>
          <p style={{ fontSize: "14px", margin: "4px 0 0 0", opacity: 0.7 }}>
            Available Balance
          </p>
        </div>
      </section>

      {/* Market Data Section - Uses WebSocket, isolated from auto-refresh */}
      <section style={{ marginTop: 24 }}>
        {marketQuotesComponent}
      </section>

      <section style={{ marginTop: 24 }}>
        <h3>Holdings</h3>
        <div style={{ padding: "16px", border: "1px solid #ccc", borderRadius: "8px" }}>
          {holdings && holdings.totalPositions > 0 ? (
            <div>
              {Object.entries(holdings.holdings).map(([symbol, quantity]) => (
                <div key={symbol} style={{ display: "flex", justifyContent: "space-between", marginBottom: "8px" }}>
                  <span>{symbol}</span>
                  <span>{quantity} shares</span>
                </div>
              ))}
            </div>
          ) : (
            <p style={{ opacity: 0.7 }}>No holdings available</p>
          )}
        </div>
      </section>



      <section style={{ marginTop: 24 }}>
        <h3>Recent Orders</h3>
        <div style={{ padding: "16px", border: "1px solid #ccc", borderRadius: "8px" }}>
          {orderStatus && orderStatus.totalOrders > 0 ? (
            <div>
              {orderStatus.orders.slice(0, 5).map((order) => (
                <div key={order.orderId} style={{ 
                  marginBottom: "12px",
                  padding: "12px",
                  backgroundColor: order.status === 'PENDING' ? '#fff3cd' : '#d4edda',
                  borderRadius: "4px"
                }}>
                  <div style={{
                    display: "flex", 
                    justifyContent: "space-between", 
                    alignItems: "center",
                    marginBottom: order.status === 'PENDING' ? "8px" : "0"
                  }}>
                    <div>
                      <strong>{order.orderType}</strong> {order.quantity} {order.symbol}
                    </div>
                    <div>
                      ${order.price.toFixed(2)}
                    </div>
                    <div>
                      <span style={{ 
                        padding: "4px 8px", 
                        borderRadius: "4px", 
                        fontSize: "12px",
                        backgroundColor: order.status === 'PENDING' ? '#ffc107' : order.status === 'FILLED' ? '#28a745' : '#6c757d',
                        color: 'white'
                      }}>
                        {order.status}
                      </span>
                    </div>
                  </div>
                  
                  {order.status === 'PENDING' && (
                    <div style={{ display: "flex", gap: "8px", marginTop: "8px" }}>
                      <button 
                        onClick={() => setModifyingOrder(order.orderId)}
                        style={{ 
                          padding: "6px 12px", 
                          fontSize: "14px",
                          backgroundColor: "#007bff",
                          color: "white",
                          border: "none",
                          borderRadius: "4px",
                          cursor: "pointer"
                        }}
                      >
                        Modifier
                      </button>
                      <button 
                        onClick={() => handleCancelOrder(order.orderId)}
                        style={{ 
                          padding: "6px 12px", 
                          fontSize: "14px",
                          backgroundColor: "#dc3545",
                          color: "white",
                          border: "none",
                          borderRadius: "4px",
                          cursor: "pointer"
                        }}
                      >
                        Annuler
                      </button>
                    </div>
                  )}
                  
                  {modifyingOrder === order.orderId && (
                    <div style={{ 
                      marginTop: "12px", 
                      padding: "12px", 
                      backgroundColor: "white",
                      borderRadius: "4px",
                      border: "1px solid #ccc"
                    }}>
                      <h4 style={{ marginTop: 0, marginBottom: "12px" }}>Modifier la commande #{order.orderId}</h4>
                      <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        <div>
                          <label style={{ display: "block", marginBottom: "4px" }}>Nouveau prix (optionnel):</label>
                          <input 
                            type="number" 
                            step="0.01"
                            placeholder={`Prix actuel: $${order.price.toFixed(2)}`}
                            value={newPrice}
                            onChange={(e) => setNewPrice(e.target.value)}
                            style={{ width: "100%", padding: "8px" }}
                          />
                        </div>
                        <div>
                          <label style={{ display: "block", marginBottom: "4px" }}>Nouvelle quantité (optionnel):</label>
                          <input 
                            type="number" 
                            placeholder={`Quantité actuelle: ${order.quantity}`}
                            value={newQuantity}
                            onChange={(e) => setNewQuantity(e.target.value)}
                            style={{ width: "100%", padding: "8px" }}
                          />
                        </div>
                        <div style={{ display: "flex", gap: "8px", marginTop: "8px" }}>
                          <button 
                            onClick={() => handleModifyOrder(order.orderId)}
                            style={{ 
                              padding: "8px 16px",
                              backgroundColor: "#28a745",
                              color: "white",
                              border: "none",
                              borderRadius: "4px",
                              cursor: "pointer"
                            }}
                          >
                            Confirmer
                          </button>
                          <button 
                            onClick={() => {
                              setModifyingOrder(null);
                              setNewPrice("");
                              setNewQuantity("");
                            }}
                            style={{ 
                              padding: "8px 16px",
                              backgroundColor: "#6c757d",
                              color: "white",
                              border: "none",
                              borderRadius: "4px",
                              cursor: "pointer"
                            }}
                          >
                            Annuler
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <p style={{ opacity: 0.7 }}>No recent orders</p>
          )}
        </div>
      </section>

      <section style={{ marginTop: 24 }}>
        <h3>Recent Activity</h3>
        <table>
          <thead>
            <tr><th>ID</th><th>Type</th><th>Amount</th><th>When</th></tr>
          </thead>
          <tbody>
            {txs.map(t => (
              <tr key={t.id}>
                <td>{t.id}</td>
                <td>{t.type}</td>
                <td>{t.amount}</td>
                <td>{new Date(t.createdAt).toLocaleString()}</td>
              </tr>
            ))}
            {!txs.length && !loading && <tr><td colSpan={4}>No recent activity</td></tr>}
          </tbody>
        </table>
      </section>
    </main>
    </>
  );
}
