import { useState } from "react";
import { useLocation, useNavigate, Link } from "react-router-dom";
import type { Location } from "react-router-dom";
import { useAuth } from "../lib/useAuth";
import { apiPost } from "../lib/api";
import Navigation from "../components/Navigation";

type FromState = { from?: Location };
type LoginResp = { status: "OK" | "MFA_REQUIRED" | "ERROR" | string; token?: string; message?: string };

export default function Login() {
  const nav = useNavigate();
  const loc = useLocation();
  const { setJwt } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const fromPath = ((loc.state as FromState | null)?.from?.pathname) ?? "/dashboard";

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    
    // Clear previous error and set loading
    setError("");
    setLoading(true);

    try{
      console.log("Attempting login for email:", email);
      const r = await apiPost<LoginResp, { email: string; password: string }>(
        "/api/auth/login",
        { email, password }
      );

      console.log("Login response:", r);

      if (r.status === "MFA_REQUIRED") {
        console.log("MFA required, navigating to OTP verification");
        nav("/auth/verify-otp", { state: { email } });
      } else if ((r.status === "OK" || r.status === "LOGIN_SUCCESS") && r.token) {
        console.log("Login successful, setting JWT and navigating to:", fromPath);
        setJwt(r.token);
        nav(fromPath, { replace: true });
      } else {
        console.log("Login failed with status:", r.status, "message:", r.message);
        setError(r.message ?? r.status);
      }
    } catch (error) {
      console.error("Login error:", error);
      setError("Login failed: " + error);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Navigation />
      <main style={{ padding: "20px", maxWidth: "400px", margin: "0 auto" }}>
        <h1>Welcome Back</h1>
      <p style={{ marginBottom: "20px", color: "#666" }}>
        Sign in to your BrokerX account
      </p>
      
      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "15px" }}>
        <div>
          <input 
            type="email"
            value={email} 
            onChange={(e) => setEmail(e.target.value)} 
            placeholder="Email Address"
            required
            disabled={loading}
            style={{ 
              width: "100%", 
              padding: "12px", 
              border: "1px solid #ccc",
              borderRadius: "4px"
            }} 
          />
        </div>
        <div>
          <input 
            type="password" 
            value={password} 
            onChange={(e) => setPassword(e.target.value)} 
            placeholder="Password"
            required
            disabled={loading}
            style={{ 
              width: "100%", 
              padding: "12px", 
              border: "1px solid #ccc",
              borderRadius: "4px"
            }} 
          />
        </div>

        {error && (
          <div style={{ 
            color: "#dc3545", 
            backgroundColor: "#f8d7da", 
            border: "1px solid #f5c6cb",
            borderRadius: "4px",
            padding: "10px",
            fontSize: "14px"
          }}>
            <strong>Error:</strong> {error}
          </div>
        )}

        <button 
          type="submit"
          disabled={loading || !email || !password}           
          style={{ 
            padding: "12px", 
            backgroundColor: loading || !email || !password ? "#6c757d" : "#28a745", 
            color: "white", 
            border: "none", 
            borderRadius: "4px", 
            cursor: loading || !email || !password ? "not-allowed" : "pointer",
            fontSize: "16px"
          }}
        >
          {loading ? "Signing In..." : "Sign In"}
        </button>
      </form>

      <div style={{ marginTop: "20px", textAlign: "center", fontSize: "14px", color: "#666" }}>
        <p>Don't have an account? <Link to="/register" style={{ color: "#007bff" }}>Create one here</Link></p>
      </div>
    </main>
    </>
  );
}
