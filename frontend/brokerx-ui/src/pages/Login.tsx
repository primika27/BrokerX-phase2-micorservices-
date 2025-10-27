import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
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

  const fromPath = ((loc.state as FromState | null)?.from?.pathname) ?? "/dashboard";

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    try{
    const r = await apiPost<LoginResp, { email: string; password: string }>(
      "/api/auth/login",
      { email, password }
    );

    if (r.status === "MFA_REQUIRED") {
      nav("/auth/verify-otp", { state: { email } });
    } else if ((r.status === "OK" || r.status === "LOGIN_SUCCESS") && r.token) {
      setJwt(r.token);
      nav(fromPath, { replace: true });
    } else {
      alert(r.message ?? r.status);
    }
  } catch (error) {
        alert("Login failed: " + error);
  }
    }
  return (
    <main>
      <h1>Login</h1>
      <form onSubmit={submit}>
        <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" />
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" />
        <button type="submit">Log in</button>
      </form>
    </main>
  );
}
