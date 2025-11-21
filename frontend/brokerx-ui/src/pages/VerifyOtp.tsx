import React, { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/useAuth";
import { apiPost } from "../lib/api";

export default function VerifyOtp() {
  const nav = useNavigate();
  const loc = useLocation();
  const { setJwt } = useAuth();
  const email = (loc.state as { email: string }).email;
  const [otp, setOtp] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // Clear error when user starts typing
  const handleOtpChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setOtp(e.target.value);
    if (error) setError("");
  };

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    
    // Clear previous errors and set loading
    setError("");
    setLoading(true);

    try {
      type OtpResp = { status: "LOGIN_SUCCESS" | "OK" | "INVALID_OTP" | "ERROR"; token?: string; message?: string };
      const r = await apiPost<OtpResp, { email: string; otp: string }>("/api/auth/verify-otp", { email, otp });
      
      if ((r.status === "LOGIN_SUCCESS" || r.status === "OK") && r.token) { 
        setJwt(r.token); 
        nav("/dashboard"); 
      } else if (r.status === "ERROR" && r.message === "INVALID_OTP") {
        setError("Invalid OTP. Please check the code and try again.");
      } else if (r.status === "ERROR") {
        setError(r.message || "An error occurred. Please try again.");
      } else {
        setError(r.message || "Verification failed. Please try again.");
      }
    } catch {
      setError("Network error. Please check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-container auth-container-animated">
        <div className="auth-header">
          <div className="auth-logo">BrokerX</div>
          <h1>Verify OTP</h1>
          <p>Enter the verification code sent to your email</p>
        </div>

        <div className="otp-info">
          <p>
            We've sent a 6-digit verification code to <strong>{email}</strong>
          </p>
        </div>
        
        <form onSubmit={submit} className="auth-form">
          <div className="auth-form-group">
            <label htmlFor="otp">Verification Code</label>
            <input 
              id="otp"
              type="text"
              value={otp} 
              onChange={handleOtpChange} 
              placeholder="000000"
              maxLength={6}
              className={`auth-input otp-input ${error ? 'error' : ''}`}
              disabled={loading}
              autoComplete="one-time-code"
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
            disabled={loading || otp.length !== 6}
            className={`auth-button ${loading ? 'loading' : ''}`}
          >
            {loading ? (
              <>
                <span className="auth-loading-spinner"></span>
                {' '}Verifying...
              </>
            ) : (
              "Verify Code"
            )}
          </button>
        </form>

        <div className="auth-footer">
          <p>Didn't receive the code? Check your spam folder or contact support.</p>
        </div>
      </div>
    </div>
  );
}
