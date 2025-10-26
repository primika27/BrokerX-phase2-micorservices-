import { useState } from "react";
import { useAuth } from "../lib/useAuth";
import { apiPost } from "../lib/api";

export default function PlaceOrder() {
  const { jwt } = useAuth();
  const [side, setSide] = useState("BUY");
  const [symbol, setSymbol] = useState("AAPL");
  const [qty, setQty] = useState(1);
  const [price, setPrice] = useState<number | "">("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    await apiPost("/api/orders", { side, symbol, qty, price }, jwt || undefined);
    alert("Order submitted");
  }

  return (
    <main>
      <h1>Place Order</h1>
      <form onSubmit={submit}>
        <select value={side} onChange={e=>setSide(e.target.value)}>
          <option>BUY</option><option>SELL</option>
        </select>
        <input value={symbol} onChange={e=>setSymbol(e.target.value)} placeholder="Symbol" />
        <input type="number" value={qty} onChange={e=>setQty(+e.target.value)} placeholder="Qty" />
        <input type="number" value={price} onChange={e=>setPrice(e.target.value === "" ? "" : +e.target.value)} placeholder="Price (limit optional)"/>
        <button type="submit">Submit</button>
      </form>
    </main>
  );
}
