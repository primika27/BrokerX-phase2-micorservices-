import { useState } from "react";
import { Input, Button, Card } from "@nextui-org/react";

export default function Deposit() {
  const [amount, setAmount] = useState("");

  const handleDeposit = async (e: React.FormEvent) => {
    e.preventDefault();
    const token = localStorage.getItem("token");
    const response = await fetch(`http://localhost:8080/api/wallet/deposit?amount=${amount}`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    const text = await response.text();
    alert(text);
  };

  return (
    <div className="flex justify-center items-center min-h-screen bg-gray-100">
      <Card className="p-8 w-full max-w-md shadow-lg">
        <h2 className="text-2xl font-semibold text-center mb-6">Dépôt</h2>
        <form onSubmit={handleDeposit} className="space-y-4">
          <Input
            label="Montant"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
          <Button type="submit" color="primary" fullWidth>
            Déposer
          </Button>
        </form>
      </Card>
    </div>
  );
}
