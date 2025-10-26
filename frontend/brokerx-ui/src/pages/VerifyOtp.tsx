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

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    type OtpResp = { status: "OK" | "INVALID_OTP" | "ERROR"; token?: string; message?: string };
    const r = await apiPost<OtpResp, { email: string; otp: string }>("/api/auth/verify-otp", { email, otp });
    if (r.status === "OK" && r.token) { setJwt(r.token); nav("/dashboard"); }
    else { alert(r.message ?? r.status); }

  }

  return (
    <main>
      <h1>Verify OTP</h1>
      <form onSubmit={submit}>
        <input value={otp} onChange={e => setOtp(e.target.value)} placeholder="Enter OTP" />
        <button type="submit">Verify</button>
      </form>
    </main>
  );
}
