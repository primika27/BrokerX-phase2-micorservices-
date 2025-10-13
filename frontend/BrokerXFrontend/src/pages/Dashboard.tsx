import { useEffect, useState } from "react";
import { Card, Button, Spinner } from "@nextui-org/react";
import { useNavigate } from "react-router-dom";

export default function Dashboard() {
  const [balance, setBalance] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchBalance = async () => {
      try {
        const token = localStorage.getItem("token");
        const response = await fetch("http://localhost:8080/api/wallet/balance", {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (!response.ok) throw new Error("Erreur récupération du solde");
        const balance = await response.json();
        setBalance(balance);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchBalance();
  }, []);

  const handleLogout = () => {
    localStorage.removeItem("token");
    navigate("/");
  };

  return (
    <div className="flex justify-center items-center min-h-screen bg-gray-100 p-6">
      <Card className="p-8 w-full max-w-md shadow-lg space-y-6">
        <h2 className="text-2xl font-semibold text-center mb-4">Tableau de bord</h2>

        {loading ? (
          <div className="flex justify-center"><Spinner /></div>
        ) : (
          <div className="text-center text-xl font-medium text-green-600">
            Solde actuel : {balance?.toFixed(2)} $
          </div>
        )}

        <div className="flex flex-col gap-3 mt-6">
          <Button color="primary" onPress={() => navigate("/deposit")}>
            Effectuer un dépôt
          </Button>
          <Button color="secondary" onPress={() => navigate("/place-order")}>
            Passer un ordre
          </Button>
          <Button color="danger" onPress={handleLogout}>
            Déconnexion
          </Button>
        </div>
      </Card>
    </div>
  );
}
