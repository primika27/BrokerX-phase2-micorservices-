import { Link, Outlet } from "react-router-dom";
import "./App.css";
import { useAuth } from "../lib/useAuth";

export default function App() {
  const { jwt, logout } = useAuth(); // assuming logout exists

  return (
    <div className="layout">
      <header className="topnav">
        <div className="nav-left">
          {jwt && (
            <>
              <Link to="/dashboard">Dashboard</Link>
              <Link to="/placeOrder">Place Order</Link>
              <Link to="/deposit">Deposit</Link>
            </>
          )}
        </div>

        <div className="nav-right">
          {!jwt ? (
            <>
              <Link className="btn-login" to="/login">Login</Link>
              <Link className="btn-register" to="/register">Not a member? Register</Link>
            </>
          ) : (
            <button className="btn-logout" onClick={logout}>Logout</button>
          )}
        </div>
      </header>

      <main>
        <Outlet />
      </main>
    </div>
  );
}
