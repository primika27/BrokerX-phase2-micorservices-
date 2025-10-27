import { useState } from "react";
import { apiPost } from "../lib/api";

export default function Register() {
  const [name,setName]=useState("");
  const [email,setEmail]=useState("");
  const [password,setPassword]=useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();

    try {
      const response = await apiPost<string>(
        "/api/clients/register",
        { name, email, password }
      );

      // Backend returns a simple string message
      alert(response || "Registered successfully! Check your email to verify.");
    } catch (error) {
      alert(`Register failed: ${error}`);
    }
  }

  return (
    <main>
      <h1>Register</h1>
      <form onSubmit={submit}>
        <input value={name} onChange={e=>setName(e.target.value)} placeholder="Name" />
        <input value={email} onChange={e=>setEmail(e.target.value)} placeholder="Email" />
        <input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Password" />
        <button type="submit">Create account</button>
      </form>
    </main>
  );
}
