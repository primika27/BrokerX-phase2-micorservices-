import { Link } from "react-router-dom";
import { useAuth } from "../lib/useAuth";

export default function Navigation() {
  const { jwt, logout } = useAuth();

  return (
    <header className="topnav" style={{
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      padding: "10px 20px",
      backgroundColor: "#f8f9fa",
      borderBottom: "1px solid #dee2e6",
      marginBottom: "20px"
    }}>
      <div className="nav-left" style={{ display: "flex", gap: "15px", alignItems: "center" }}>
        <Link to="/" style={{ fontWeight: "bold", fontSize: "18px", textDecoration: "none", color: "#007bff" }}>
          BrokerX
        </Link>
        {jwt && (
          <>
            <Link to="/dashboard" style={{ textDecoration: "none", color: "#007bff" }}>Dashboard</Link>
            <Link to="/placeOrder" style={{ textDecoration: "none", color: "#007bff" }}>Place Order</Link>
            <Link to="/deposit" style={{ textDecoration: "none", color: "#007bff" }}>Deposit</Link>
          </>
        )}
      </div>

      <div className="nav-right" style={{ display: "flex", gap: "10px", alignItems: "center" }}>
        {!jwt ? (
          <>
            <Link 
              to="/login" 
              style={{ 
                padding: "8px 16px",
                backgroundColor: "#007bff",
                color: "white",
                textDecoration: "none",
                borderRadius: "4px"
              }}
            >
              Login
            </Link>
            <Link 
              to="/register" 
              style={{ 
                padding: "8px 16px",
                backgroundColor: "#28a745",
                color: "white",
                textDecoration: "none",
                borderRadius: "4px"
              }}
            >
              Register
            </Link>
          </>
        ) : (
          <button 
            onClick={logout}
            style={{ 
              padding: "8px 16px",
              backgroundColor: "#dc3545",
              color: "white",
              border: "none",
              borderRadius: "4px",
              cursor: "pointer"
            }}
          >
            Logout
          </button>
        )}
      </div>
    </header>
  );
}