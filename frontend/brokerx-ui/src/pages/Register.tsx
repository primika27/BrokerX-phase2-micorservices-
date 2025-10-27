import { useState } from "react";

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
    setLoading(true);

    try {
      // Use fetch directly since backend returns plain text, not JSON
      const response = await fetch("/api/clients/register", {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name, email, password })
      });

      const message = await response.text(); // Get plain text response

      if (response.ok) {
        setSuccess(message || "Registered successfully! Check your email to verify.");
        // Reset form on success
        setName("");
        setEmail("");
        setPassword("");
      } else {
        setError(message || "Registration failed. Please try again.");
      }
    } catch {
      setError("Network error. Please check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
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
        <p>Already have an account? <a href="/login" style={{ color: "#007bff" }}>Sign in here</a></p>
      </div>
    </main>
  );
}
