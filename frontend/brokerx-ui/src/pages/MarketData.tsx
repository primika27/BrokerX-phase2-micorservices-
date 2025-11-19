import { useAuth } from "../lib/useAuth";
import Navigation from "../components/Navigation";
import MarketQuotes from "../components/MarketQuotes";

export default function MarketData() {
  const { jwt } = useAuth();

  if (!jwt) {
    window.location.href = "/login";
    return null;
  }

  return (
    <div>
      <Navigation />
      <div style={{ maxWidth: "1200px", margin: "0 auto", padding: "20px" }}>
        <h1 style={{ marginBottom: "30px", color: "#333" }}>Live Market Data</h1>
        <MarketQuotes userToken={jwt} />
      </div>
    </div>
  );
}
