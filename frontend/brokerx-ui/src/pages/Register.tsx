import { useState } from "react";


export default function Register() {
  const [name,setName]=useState("");
  const [email,setEmail]=useState("");
  const [password,setPassword]=useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();

    const params = new URLSearchParams({ name, email, password }).toString();
    const res = await fetch(`/api/clients/register?${params}`, { method: "POST" });
    if (!res.ok) {
      const msg = await res.text();
      alert(`Register failed: ${msg}`);
      return;
    }
    alert("Registered. Check your email to verify.");
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
