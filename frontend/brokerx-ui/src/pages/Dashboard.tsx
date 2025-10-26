import { useEffect, useState } from "react";
import { useAuth } from "../lib/useAuth";
import { apiGet } from "../lib/api";
//import { nav } from "framer-motion/m";
import { useNavigate } from "react-router-dom";


type Me = { id: number; name: string; email: string };
type Balance = { currency: string; available: number; locked: number };
type Order = { id: string; symbol: string; side: "BUY" | "SELL"; qty: number; price?: number; status: string; createdAt: string };
type Tx = { id: string; type: "DEPOSIT" | "WITHDRAWAL" | "FILL"; amount: number; createdAt: string };

export default function Dashboard() {
  const { jwt, setJwt } = useAuth();
  const nav = useNavigate();
  const [me, setMe] = useState<Me | null>(null);
  const [balances, setBalances] = useState<Balance[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [txs, setTxs] = useState<Tx[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        setLoading(true);
        setErr(null);
        
        const [meR, balR, ordR, txR] = await Promise.all([
          apiGet<Me>("/api/clients/me", jwt || undefined),
          apiGet<Balance[]>("/api/wallet/balance", jwt || undefined),
          apiGet<Order[]>("/api/orders/holdings", jwt || undefined),
          apiGet<Tx[]>("/api/wallet/deposit", jwt || undefined),
        ]);
        if (cancelled) return;
        setMe(meR);
        setBalances(balR);
        setOrders(ordR);
        setTxs(txR);
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
        <div style={{ marginLeft: "auto" }}>
          <button onClick={() => setJwt(null)}>Logout</button>
        </div>
      </header>

      {loading && <p>Loading…</p>}
      {err && <p style={{ color: "crimson" }}>{err}</p>}

      {me && (
        <section style={{ marginTop: 16 }}>
          <h2 style={{ marginBottom: 8 }}>Welcome, {me.name}</h2>
          <p style={{ opacity: 0.8 }}>{me.email}</p>
            <div style={{ marginTop: 12, display: "flex", gap: 12 }}>
                <button onClick={() => nav("/wallet/deposit")}>Deposit</button>
                <button onClick={() => nav("/orders/place")}>Place Order</button>
                <button onClick={() => nav("/wallet/withdraw")}>Withdraw</button>
                <button onClick={() => nav("/settings")}>Settings</button>
            </div>
        </section>
      )}

      <section style={{ marginTop: 24 }}>
        <h3>Balances</h3>
        <table>
          <thead>
            <tr><th>Currency</th><th>Available</th><th>Locked</th></tr>
          </thead>
          <tbody>
            {balances.map(b => (
              <tr key={b.currency}>
                <td>{b.currency}</td>
                <td>{b.available}</td>
                <td>{b.locked}</td>
              </tr>
            ))}
            {!balances.length && !loading && <tr><td colSpan={3}>No balances</td></tr>}
          </tbody>
        </table>
      </section>

      <section style={{ marginTop: 24 }}>
        <h3>Open Orders</h3>
        <table>
          <thead>
            <tr><th>ID</th><th>Symbol</th><th>Side</th><th>Qty</th><th>Price</th><th>Status</th><th>Created</th></tr>
          </thead>
          <tbody>
            {orders.map(o => (
              <tr key={o.id}>
                <td>{o.id}</td>
                <td>{o.symbol}</td>
                <td>{o.side}</td>
                <td>{o.qty}</td>
                <td>{o.price ?? "-"}</td>
                <td>{o.status}</td>
                <td>{new Date(o.createdAt).toLocaleString()}</td>
              </tr>
            ))}
            {!orders.length && !loading && <tr><td colSpan={7}>No open orders</td></tr>}
          </tbody>
        </table>
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
