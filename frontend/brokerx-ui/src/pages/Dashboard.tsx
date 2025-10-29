import { useEffect, useState } from "react";
import { useAuth } from "../lib/useAuth";
import { apiGet } from "../lib/api";
import { useNavigate } from "react-router-dom";
import Navigation from "../components/Navigation";

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

  useEffect(() => {
    let cancelled = false;
    let intervalId: number | null = null;

    async function load() {
      if (!jwt) {
        setLoading(false);
        return;
      }
      
      try {
        setLoading(true);
        setErr(null);
        console.log('Loading dashboard data...');
        
        const [meR, balR, holdingsR, statusR] = await Promise.all([
          apiGet<Me>("/api/clients/me", jwt).catch(e => { 
            console.error('Failed to fetch user:', e); 
            throw e; 
          }),
          apiGet<number>("/api/wallet/balance", jwt).catch(e => { 
            console.error('Failed to fetch balance:', e); 
            return 0; // Default to 0 if balance fails
          }),
          apiGet<Holdings>("/api/orders/holdings", jwt).catch(e => { 
            console.error('Failed to fetch holdings:', e); 
            return { holdings: {}, totalPositions: 0 }; // Default object
          }),
          apiGet<OrderStatus>("/api/orders/status", jwt).catch(e => { 
            console.error('Failed to fetch order status:', e); 
            return { orders: [], totalOrders: 0 }; // Default object
          })
        ]);
        
        if (cancelled) return;
        
        console.log('Dashboard data loaded:', { meR, balR, holdingsR, statusR });
        setMe(meR);
        setBalance(typeof balR === 'number' ? balR : 0);
        setHoldings(holdingsR || { holdings: {}, totalPositions: 0 });
        setOrderStatus(statusR || { orders: [], totalOrders: 0 });
        setTxs([]); // No transaction history endpoint available yet
      } catch (e: unknown) {
        if (!cancelled) {
          console.error('Dashboard loading error:', e);
          setErr(String((e as Error).message || e));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    // Initial load
    load();

    // Set up auto-refresh every 3 seconds to show PENDING -> FILLED transitions
    intervalId = setInterval(() => {
      if (!cancelled) {
        load();
      }
    }, 3000);

    return () => { 
      cancelled = true; 
      if (intervalId) clearInterval(intervalId);
    };
  }, [jwt]);

  return (
    <>
      <Navigation />
      <main className="container">
        <header style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <h1 style={{ margin: 0 }}>Dashboard</h1>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ fontSize: "12px", color: "#666" }}>Auto-refresh: 3s</span>
          <button onClick={() => window.location.reload()} style={{ padding: "8px 16px", fontSize: "14px" }}>
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
                  display: "flex", 
                  justifyContent: "space-between", 
                  alignItems: "center",
                  marginBottom: "12px",
                  padding: "8px",
                  backgroundColor: order.status === 'PENDING' ? '#fff3cd' : '#d4edda',
                  borderRadius: "4px"
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
