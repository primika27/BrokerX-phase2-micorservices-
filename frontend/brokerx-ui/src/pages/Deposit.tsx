import { useState } from "react";
import { useAuth } from "../lib/useAuth";
import Navigation from "../components/Navigation";

export default function Deposit() {
  const { jwt } = useAuth();
  const [amount, setAmount] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    try {
      // Send amount as URL parameter instead of request body
      const response = await fetch(`/api/wallet/deposit?amount=${Number.parseFloat(amount)}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${jwt}`,
          'Content-Type': 'application/json'
        }
      });
      const message = await response.text();
      if (response.ok) {
        alert(`Success: ${message}`);
        setAmount(""); // Reset form
      } else {
        alert(`Error: ${message}`);
      }
    } catch (error) {
      alert(`Error: ${error}`);
    }
  }

  return (
    <>
      <Navigation />
      <main>
        <h1>Deposit</h1>
        <form onSubmit={submit}>
          <input 
            type="number" 
            value={amount} 
            onChange={e=>setAmount(e.target.value)}
            placeholder="Enter amount to deposit"
            min="0"
            step="0.01"
          />
          <button type="submit">Deposit</button>
        </form>
      </main>
    </>
  );
}
