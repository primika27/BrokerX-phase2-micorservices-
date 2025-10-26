import { useState } from "react";
import { useAuth } from "../lib/useAuth";
import { apiPost } from "../lib/api";

export default function Deposit() {
  const { jwt } = useAuth();
  const [amount, setAmount] = useState(0);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    await apiPost("/api/wallet/deposit", { amount }, jwt || undefined);
    alert("Deposited");
  }

  return (
    <main>
      <h1>Deposit</h1>
      <form onSubmit={submit}>
        <input type="number" value={amount} onChange={e=>setAmount(+e.target.value)} />
        <button type="submit">Deposit</button>
      </form>
    </main>
  );
}
