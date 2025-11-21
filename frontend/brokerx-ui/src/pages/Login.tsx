import { useState } from "react";
import { useLocation, useNavigate, Link } from "react-router-dom";
import type { Location } from "react-router-dom";
import { useAuth } from "../lib/useAuth";
import { apiPost } from "../lib/api";

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
    <div className="auth-page">
      <div className="auth-container auth-container-animated">
        <div className="auth-header">
          <div className="auth-logo">BrokerX</div>
          <h1>Welcome Back</h1>
          <p>Sign in to your BrokerX account to access professional trading tools</p>
        </div>
        
        <form onSubmit={submit} className="auth-form">
          <div className="auth-form-group">
            <label htmlFor="email">Email Address</label>
            <input 
              id="email"
              type="email"
              value={email} 
              onChange={(e) => setEmail(e.target.value)} 
              placeholder="Enter your email address"
              required
              disabled={loading}
              className="auth-input"
            />
          </div>

          <div className="auth-form-group">
            <label htmlFor="password">Password</label>
            <input 
              id="password"
              type="password" 
              value={password} 
              onChange={(e) => setPassword(e.target.value)} 
              placeholder="Enter your password"
              required
              disabled={loading}
              className="auth-input"
            />
          </div>

          {error && (
            <div className="auth-alert auth-alert-error">
              <span className="auth-alert-icon">ERROR</span>
              {error}
            </div>
          )}

          <button 
            type="submit"
            disabled={loading || !email || !password}           
            className={`auth-button ${loading ? 'loading' : ''}`}
          >
            {loading ? (
              <>
                <span className="auth-loading-spinner"></span>
                {' '}Signing In...
              </>
            ) : (
              "Sign In"
            )}
          </button>
        </form>

        <div className="auth-footer">
          <p>Don't have an account? <Link to="/register">Create one here</Link></p>
        </div>
      </div>
    </div>
  );
}
