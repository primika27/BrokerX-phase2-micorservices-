import { useState } from "react";
import { useAuth } from "../lib/useAuth";
import Navigation from "../components/Navigation";
export default function PlaceOrder() {
  const { jwt } = useAuth();
  const [side, setSide] = useState("BUY");
  const [symbol, setSymbol] = useState("SPY");
  const [qty, setQty] = useState(1);
  const [price, setPrice] = useState<number | "">("");

  // Available symbols from backend (matching OrderController)
  const availableSymbols = [
    { symbol: "SPY", name: "S&P 500 ETF", price: 445.50 },
    { symbol: "IVV", name: "iShares S&P 500 ETF", price: 445.20 },
    { symbol: "VOO", name: "Vanguard S&P 500 ETF", price: 445.80 },
    { symbol: "VTI", name: "Vanguard Total Stock Market ETF", price: 265.40 },
    { symbol: "QQQ", name: "Invesco QQQ Trust", price: 380.75 },
    { symbol: "VEA", name: "Vanguard FTSE Developed Markets ETF", price: 51.20 },
    { symbol: "VWO", name: "Vanguard FTSE Emerging Markets ETF", price: 42.85 },
    { symbol: "AGG", name: "iShares Core U.S. Aggregate Bond ETF", price: 101.50 },
    { symbol: "BND", name: "Vanguard Total Bond Market ETF", price: 73.25 },
    { symbol: "IWM", name: "iShares Russell 2000 ETF", price: 220.30 },
    { symbol: "EFA", name: "iShares MSCI EAFE ETF", price: 79.90 }
  ];

  // Get current market price for selected symbol
  const getCurrentPrice = () => {
    const selectedETF = availableSymbols.find(etf => etf.symbol === symbol);
    return selectedETF ? selectedETF.price : 0;
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    try {
      // Use direct call to backend (like api.ts) instead of Vite proxy
      const response = await fetch(`http://localhost:8080/api/orders/placeOrder?symbol=${symbol}&quantity=${qty}&orderType=${side}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${jwt}`,
          'Content-Type': 'application/json'
        }
      });
      const message = await response.text();
      if (response.ok) {
        alert(`Success: ${message}`);
        // Reset form
        setSymbol("SPY");
        setQty(1);
        setPrice("");
      } else {
        alert(`Error: ${message}`);
      }
    } catch (error) {
      alert(`Error: ${error}`);
    }
  }

  return (
    <main style={{ padding: "20px" }}>
      <Navigation />
      <h1>Place Order</h1>
      
      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "15px", maxWidth: "400px" }}>
        <div>
          <label style={{ display: "block", marginBottom: "5px" }}>Order Type:</label>
          <select value={side} onChange={e=>setSide(e.target.value)} style={{ width: "100%", padding: "8px" }}>
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
          </select>
        </div>

        <div>
          <label style={{ display: "block", marginBottom: "5px" }}>Symbol:</label>
          <select value={symbol} onChange={e=>setSymbol(e.target.value)} style={{ width: "100%", padding: "8px" }}>
            {availableSymbols.map(etf => (
              <option key={etf.symbol} value={etf.symbol}>
                {etf.symbol} - {etf.name} (${etf.price})
              </option>
            ))}
          </select>
          <small style={{ color: "#666" }}>
            Current Price: ${getCurrentPrice().toFixed(2)}
          </small>
        </div>

        <div>
          <label style={{ display: "block", marginBottom: "5px" }}>Quantity:</label>
          <input 
            type="number" 
            value={qty} 
            onChange={e=>setQty(+e.target.value)} 
            placeholder="Quantity" 
            min="1"
            style={{ width: "100%", padding: "8px" }}
          />
        </div>

        <div>
          <label style={{ display: "block", marginBottom: "5px" }}>Limit Price (optional):</label>
          <input 
            type="number" 
            value={price} 
            onChange={e=>setPrice(e.target.value === "" ? "" : +e.target.value)} 
            placeholder="Leave empty for market order"
            step="0.01"
            style={{ width: "100%", padding: "8px" }}
          />
        </div>

        <div>
          <strong>Estimated Total: ${(getCurrentPrice() * qty).toFixed(2)}</strong>
        </div>

        <button type="submit" style={{ padding: "12px", backgroundColor: "#007bff", color: "white", border: "none", borderRadius: "4px", cursor: "pointer" }}>
          {side} {qty} shares of {symbol}
        </button>
      </form>
    </main>
  );
}
