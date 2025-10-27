import { useState } from "react";
import { useAuth } from "../lib/useAuth";

export default function Deposit() {
  const { jwt } = useAuth();
  const [amount, setAmount] = useState(0);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    try {
      // Send amount as URL parameter instead of request body
      const response = await fetch(`/api/wallet/deposit?amount=${amount}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${jwt}`,
          'Content-Type': 'application/json'
        }
      });
      const message = await response.text();
      if (response.ok) {
        alert(`Success: ${message}`);
        setAmount(0); // Reset form
      } else {
        alert(`Error: ${message}`);
      }
    } catch (error) {
      alert(`Error: ${error}`);
    }
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
