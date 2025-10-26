import { Link, Outlet } from "react-router-dom";
import "./App.css";

export default function App() {
  return (
    <div className="layout">
      <nav className="topnav">
        <Link to="/">Home</Link>
        <Link to="/dashboard">Dashboard</Link>
        <Link to="/place-order">Place Order</Link>
        <Link to="/deposit">Deposit</Link>
        <span className="spacer" />
        <Link to="/login">Login</Link>
        <Link to="/register">Register</Link>
      </nav>
      <Outlet />
    </div>
  );
}
