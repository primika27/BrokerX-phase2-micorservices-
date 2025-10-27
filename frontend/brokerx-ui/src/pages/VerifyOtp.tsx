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
    <main style={{ padding: "20px", maxWidth: "400px", margin: "0 auto" }}>
      <h1>Verify OTP</h1>
      <p style={{ marginBottom: "20px", color: "#666" }}>
        Enter the 6-digit verification code sent to <strong>{email}</strong>
      </p>
      
      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "15px" }}>
        <div>
          <input 
            type="text"
            value={otp} 
            onChange={handleOtpChange} 
            placeholder="Enter 6-digit OTP"
            maxLength={6}
            style={{ 
              width: "100%", 
              padding: "12px", 
              fontSize: "18px",
              textAlign: "center",
              letterSpacing: "2px",
              border: error ? "2px solid #dc3545" : "1px solid #ccc",
              borderRadius: "4px",
              backgroundColor: error ? "#fff5f5" : "white"
            }}
            disabled={loading}
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
          disabled={loading || otp.length !== 6}
          style={{ 
            padding: "12px", 
            backgroundColor: loading || otp.length !== 6 ? "#6c757d" : "#007bff", 
            color: "white", 
            border: "none", 
            borderRadius: "4px", 
            cursor: loading || otp.length !== 6 ? "not-allowed" : "pointer",
            fontSize: "16px"
          }}
        >
          {loading ? "Verifying..." : "Verify OTP"}
        </button>
      </form>

      <div style={{ marginTop: "20px", textAlign: "center", fontSize: "14px", color: "#666" }}>
        <p>Didn't receive the code? Check your spam folder or try again.</p>
      </div>
    </main>
  );
}
