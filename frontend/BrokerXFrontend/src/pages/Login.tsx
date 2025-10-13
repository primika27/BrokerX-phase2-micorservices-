
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";

export default function Login() {
  const [email, setEmail] = useState("");
  const [motDePasse, setMotDePasse] = useState("");
  const [otp, setOtp] = useState("");
  const [showOtp, setShowOtp] = useState(false);
  const [error, setError] = useState("");
  const [otpLoading, setOtpLoading] = useState(false);
  const navigate = useNavigate();

  // Login form submit handler
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: `email=${encodeURIComponent(email)}&motDePasse=${encodeURIComponent(motDePasse)}`
      });
      const text = await response.text();

      if (text === "MFA_REQUIRED") {
        setShowOtp(true);
      } else if (text === "SUCCESS" || text === `Login successful for: ${email}`) {
        navigate("/dashboard");
      } else {
        setError("Identifiants invalides ou compte non activé.");
      }
    } catch (err) {
      setError("Erreur de connexion: " + (err instanceof Error ? err.message : String(err)));
    }
  };

  // OTP form submit handler
  const handleOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setOtpLoading(true);
    try {
      const response = await fetch("/api/auth/verify-otp", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: `email=${encodeURIComponent(email)}&otp=${encodeURIComponent(otp)}`
      });
      const text = await response.text();

      if (text === "LOGIN_SUCCESS") {
        navigate("/dashboard");
      } else {
        setError("OTP invalide. Veuillez réessayer.");
      }
    } catch (err) {
      setError("Erreur lors de la validation OTP: " + (err instanceof Error ? err.message : String(err)));
    } finally {
      setOtpLoading(false);
    }
  };

  return (
    <div>
      <h2>Connexion</h2>
      {!showOtp ? (
        <form onSubmit={handleLogin}>
          <input
            type="email"
            name="email"
            placeholder="Email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            required
          />
          <input
            type="password"
            name="motDePasse"
            placeholder="Mot de passe"
            value={motDePasse}
            onChange={e => setMotDePasse(e.target.value)}
            required
          />
          <button type="submit">Se connecter</button>
          <button type="button" onClick={() => navigate("/register")}>S'inscrire</button>
          {error && <div style={{ color: "red" }}>{error}</div>}
        </form>
      ) : (
        <form onSubmit={handleOtp}>
          <div style={{ color: "green", marginBottom: "10px" }}>
            Un code a été envoyé à votre email. Veuillez le saisir ci-dessous :
          </div>
          <input
            type="text"
            name="otp"
            placeholder="Code OTP (6 chiffres)"
            pattern="\d{6}"
            maxLength={6}
            required
            value={otp}
            onChange={e => setOtp(e.target.value)}
            disabled={otpLoading}
          />
          <button type="submit" disabled={otpLoading}>Valider</button>
          {error && <div style={{ color: "red" }}>{error}</div>}
        </form>
      )}
    </div>
  );
}

