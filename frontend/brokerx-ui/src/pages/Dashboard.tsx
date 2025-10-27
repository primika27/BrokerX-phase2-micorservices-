import { useEffect, useState } from "react";
import { useAuth } from "../lib/useAuth";
import { apiGet } from "../lib/api";
import { useNavigate } from "react-router-dom";

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
      try {
        setLoading(true);
        setErr(null);
        
        const [meR, balR, ordR] = await Promise.all([
          apiGet<Me>("/api/clients/me", jwt || undefined),
          apiGet<number>("/api/wallet/balance", jwt || undefined),
          apiGet<string>("/api/orders/holdings", jwt || undefined),
        ]);
        if (cancelled) return;
        setMe(meR);
        setBalance(balR);
        setOrders(ordR);
        setTxs([]); // No transaction history endpoint available yet
      } catch (e: unknown) {
        if (!cancelled) setErr(String((e as Error).message || e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [jwt]);

  return (
    <main className="container">
      <header style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <h1 style={{ margin: 0 }}>Dashboard</h1>
      </header>

      {loading && <p>Loading…</p>}
      {err && <p style={{ color: "crimson" }}>{err}</p>}

      {me && (
        <section style={{ marginTop: 16 }}>
          <h2 style={{ marginBottom: 8 }}>Welcome, {me.name}</h2>
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
            ${balance.toFixed(2)} CAD
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
  );
}
