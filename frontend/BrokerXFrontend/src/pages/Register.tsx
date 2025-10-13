import { useState } from "react";
import { Input, Button, Card } from "@nextui-org/react";

export default function Register() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    const params = new URLSearchParams({ name, email, password });
    const response = await fetch(`http://localhost:8080/api/clients/register?${params}`, {
      method: "POST",
    });
    const text = await response.text();
    alert(text);
  };

  return (
    <div className="flex justify-center items-center min-h-screen bg-gray-100">
      <Card className="p-8 w-full max-w-md shadow-lg">
        <h2 className="text-2xl font-semibold text-center mb-6">Inscription</h2>
        <form onSubmit={handleRegister} className="space-y-4">
          <Input label="Nom" value={name} onChange={(e) => setName(e.target.value)} required />
          <Input label="Email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <Input
            label="Mot de passe"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <Button type="submit" color="success" fullWidth>
            S'inscrire
          </Button>
        </form>
      </Card>
    </div>
  );
}
