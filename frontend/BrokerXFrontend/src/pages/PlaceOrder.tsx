import { useState } from "react";
import { Input, Button, Card, Select, SelectItem } from "@nextui-org/react";

const ETF_SYMBOLS = [
  { key: "SPY", label: "S&P 500 ETF (SPY)" },
  { key: "QQQ", label: "Nasdaq ETF (QQQ)" },
  { key: "VOO", label: "Vanguard S&P 500 ETF (VOO)" },
  { key: "VTI", label: "Total Stock Market (VTI)" },
];

export default function PlaceOrder() {
  const [symbol, setSymbol] = useState("SPY");
  const [quantity, setQuantity] = useState<number>(0);
  const [orderType, setOrderType] = useState("BUY");
  const [loading, setLoading] = useState(false);

  const handleOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const token = localStorage.getItem("token");

    try {
      const params = new URLSearchParams({
        symbol,
        quantity: quantity.toString(),
        orderType,
      });
      const response = await fetch(`http://localhost:8080/api/orders/placeOrder?${params}`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      const text = await response.text();
      alert(text);
    } catch (err) {
      alert("Erreur lors du placement de l’ordre");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex justify-center items-center min-h-screen bg-gray-100 p-6">
      <Card className="p-8 w-full max-w-md shadow-lg">
        <h2 className="text-2xl font-semibold text-center mb-6">Passer un ordre</h2>

        <form onSubmit={handleOrder} className="space-y-4">
          <Select
            label="Symbole"
            selectedKeys={[symbol]}
            onChange={(e) => setSymbol(e.target.value)}
          >
            {ETF_SYMBOLS.map((etf) => (
              <SelectItem key={etf.key}>{etf.label}</SelectItem>
            ))}
          </Select>

          <Input
            label="Quantité"
            type="number"
            value={quantity.toString()}
            onChange={(e) => setQuantity(Number(e.target.value))}
            required
          />

          <Select
            label="Type d’ordre"
            selectedKeys={[orderType]}
            onChange={(e) => setOrderType(e.target.value)}
          >
            <SelectItem key="BUY">Achat</SelectItem>
            <SelectItem key="SELL">Vente</SelectItem>
          </Select>

          <Button type="submit" color="primary" fullWidth isLoading={loading}>
            Confirmer l’ordre
          </Button>
        </form>
      </Card>
    </div>
  );
}
