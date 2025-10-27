import { useEffect, useState } from "react";
import { useAuth } from "../lib/useAuth";
import { apiGet } from "../lib/api";
import { useNavigate } from "react-router-dom";
import Navigation from "../components/Navigation";

type Me = { id: number; name: string; email: string; status?: string };
type Tx = { id: string; type: "DEPOSIT" | "WITHDRAWAL" | "FILL"; amount: number; createdAt: string };

export default function Dashboard() {
  const { jwt } = useAuth();
  const nav = useNavigate();
  const [me, setMe] = useState<Me | null>(null);
  const [balance, setBalance] = useState<number>(0);
  const [orders, setOrders] = useState<string>(""); // Backend returns string message
  const [txs, setTxs] = useState<Tx[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!jwt) {
        setLoading(false);
        return;
      }
      
      try {
        setLoading(true);
        setErr(null);
        console.log('Loading dashboard data...');
        
        const [meR, balR, ordR] = await Promise.all([
          apiGet<Me>("/api/clients/me", jwt).catch(e => { 
            console.error('Failed to fetch user:', e); 
            throw e; 
          }),
          apiGet<number>("/api/wallet/balance", jwt).catch(e => { 
            console.error('Failed to fetch balance:', e); 
            return 0; // Default to 0 if balance fails
          }),
          apiGet<string>("/api/orders/holdings", jwt).catch(e => { 
            console.error('Failed to fetch holdings:', e); 
            return "No holdings available"; // Default message
          }),
        ]);
        
        if (cancelled) return;
        
        console.log('Dashboard data loaded:', { meR, balR, ordR });
        setMe(meR);
        setBalance(typeof balR === 'number' ? balR : 0);
        setOrders(typeof ordR === 'string' ? ordR : "No holdings available");
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
    load();
    return () => { cancelled = true; };
  }, [jwt]);

  return (
    <>
      <Navigation />
      <main className="container">
        <header style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <h1 style={{ margin: 0 }}>Dashboard</h1>
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
                <button onClick={() => nav("/settings")}>Settings</button>
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
        <h3>Holdings & Orders</h3>
        <div style={{ padding: "16px", border: "1px solid #ccc", borderRadius: "8px" }}>
          {orders ? (
            <p>{orders}</p>
          ) : (
            <p style={{ opacity: 0.7 }}>No holdings or orders available</p>
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
