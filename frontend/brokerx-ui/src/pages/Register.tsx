import { useState } from "react";
import { Link } from "react-router-dom";
import Navigation from "../components/Navigation";

export default function Register() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();

    // Clear previous messages
    setError("");
    setSuccess("");

    // Basic client-side validation
    if (!name.trim()) {
      setError("Name is required");
      return;
    }
    if (!email.trim()) {
      setError("Email is required");
      return;
    }
    if (!password.trim()) {
      setError("Password is required");
      return;
    }
    if (password.length < 6) {
      setError("Password must be at least 6 characters long");
      return;
    }

    setLoading(true);

    try {
      console.log("Attempting registration with:", { name, email, password: "***" });
      
      // Use fetch with full URL to bypass potential proxy issues
      const response = await fetch("http://localhost:8080/api/clients/register", {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name, email, password })
      });

      console.log("Registration response status:", response.status);
      const message = await response.text(); // Get plain text response
      console.log("Registration response message:", message);

      if (response.ok) {
        setSuccess(message || "Registered successfully! Check your email to verify.");
        // Reset form on success
        setName("");
        setEmail("");
        setPassword("");
      } else {
        setError(`Registration failed (${response.status}): ${message || "Please try again."}`);
      }
    } catch (networkError) {
      console.error("Registration network error:", networkError);
      setError(`Network error: ${networkError instanceof Error ? networkError.message : "Please check your connection and try again."}`);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Navigation />
      <main style={{ padding: "20px", maxWidth: "400px", margin: "0 auto" }}>
        <h1>Create Account</h1>
      <p style={{ marginBottom: "20px", color: "#666" }}>
        Sign up to start trading with BrokerX
      </p>
      
      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "15px" }}>
        <div>
          <input 
            type="text"
            value={name} 
            onChange={e=>setName(e.target.value)} 
            placeholder="Full Name"
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
            type="email"
            value={email} 
            onChange={e=>setEmail(e.target.value)} 
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
            onChange={e=>setPassword(e.target.value)} 
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

        {success && (
          <div style={{ 
            color: "#155724", 
            backgroundColor: "#d4edda", 
            border: "1px solid #c3e6cb",
            borderRadius: "4px",
            padding: "10px",
            fontSize: "14px"
          }}>
            <strong>Success:</strong> {success}
          </div>
        )}

        <button 
          type="submit" 
          disabled={loading || !name || !email || !password}
          style={{ 
            padding: "12px", 
            backgroundColor: loading || !name || !email || !password ? "#6c757d" : "#28a745", 
            color: "white", 
            border: "none", 
            borderRadius: "4px", 
            cursor: loading || !name || !email || !password ? "not-allowed" : "pointer",
            fontSize: "16px"
          }}
        >
          {loading ? "Creating Account..." : "Create Account"}
        </button>
      </form>

      <div style={{ marginTop: "20px", textAlign: "center", fontSize: "14px", color: "#666" }}>
        <p>Already have an account? <Link to="/login" style={{ color: "#007bff" }}>Sign in here</Link></p>
      </div>
    </main>
    </>
  );
}
