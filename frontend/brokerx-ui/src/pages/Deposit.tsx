import { useState } from "react";
import { useAuth } from "../lib/useAuth";
import Navigation from "../components/Navigation";

// Anti-fraud limits and validation
const DEPOSIT_LIMITS = {
  MIN_AMOUNT: 1,
  MAX_SINGLE_DEPOSIT: 10000,
  MAX_DAILY_DEPOSIT: 25000,
  MAX_MONTHLY_DEPOSIT: 100000
};

export default function Deposit() {
  const { jwt } = useAuth();
  const [amount, setAmount] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  // Validation function
  const validateAmount = (value: number): string | null => {
    if (Number.isNaN(value) || value <= 0) {
      return "Please enter a valid positive amount";
    }
    if (value < DEPOSIT_LIMITS.MIN_AMOUNT) {
      return `Minimum deposit amount is $${DEPOSIT_LIMITS.MIN_AMOUNT}`;
    }
    if (value > DEPOSIT_LIMITS.MAX_SINGLE_DEPOSIT) {
      return `Maximum single deposit is $${DEPOSIT_LIMITS.MAX_SINGLE_DEPOSIT.toLocaleString()}`;
    }
    return null;
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setSuccess("");
    
    const depositAmount = Number.parseFloat(amount);
    const validationError = validateAmount(depositAmount);
    
    if (validationError) {
      setError(validationError);
      return;
    }
    
    setIsLoading(true);
    
    try {
      const response = await fetch(`http://localhost:8080/api/wallet/deposit?amount=${depositAmount}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${jwt}`,
          'Content-Type': 'application/json'
        }
      });
      const message = await response.text();
      if (response.ok) {
        setSuccess(`Deposit successful: ${message}`);
        setAmount("");
      } else {
        setError(`Deposit failed: ${message}`);
      }
    } catch (error) {
      setError(`Network error: ${error}`);
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <>
      <Navigation />
      <main className="deposit-page">
        <div className="deposit-container">
          <div className="deposit-header">
            <h1>💳 Deposit Funds</h1>
            <p>Add funds to your BrokerX account securely</p>
          </div>

          <div className="deposit-form-container">
            <form onSubmit={submit} className="deposit-form">
              <div className="form-group">
                <label htmlFor="amount">Deposit Amount ($)</label>
                <div className="input-wrapper">
                  <span className="currency-symbol">$</span>
                  <input 
                    id="amount"
                    type="number" 
                    value={amount} 
                    onChange={e=>setAmount(e.target.value)}
                    placeholder="0.00"
                    min={DEPOSIT_LIMITS.MIN_AMOUNT}
                    max={DEPOSIT_LIMITS.MAX_SINGLE_DEPOSIT}
                    step="0.01"
                    className={error ? 'error' : ''}
                    disabled={isLoading}
                    required
                  />
                </div>
                {amount && (
                  <div className="amount-preview">
                    You're depositing: <strong>${Number.parseFloat(amount || "0").toFixed(2)}</strong>
                  </div>
                )}
              </div>

              {error && (
                <div className="alert alert-error">
                  <span className="alert-icon">⚠️</span>
                  {error}
                </div>
              )}

              {success && (
                <div className="alert alert-success">
                  <span className="alert-icon">✅</span>
                  {success}
                </div>
              )}

              <button 
                type="submit" 
                className="deposit-button"
                disabled={isLoading || !amount}
              >
                {isLoading ? (
                  <>
                    <span className="loading-spinner"></span>
                    {' '}Processing...
                  </>
                ) : (
                  <>
                    <span className="button-icon">💰</span>
                    {' '}Deposit Funds
                  </>
                )}
              </button>
            </form>

            <div className="security-info">
              <h3>🔒 Security & Limits</h3>
              <div className="limits-grid">
                <div className="limit-item">
                  <span className="limit-label">Minimum:</span>
                  <span className="limit-value">${DEPOSIT_LIMITS.MIN_AMOUNT}</span>
                </div>
                <div className="limit-item">
                  <span className="limit-label">Maximum per deposit:</span>
                  <span className="limit-value">${DEPOSIT_LIMITS.MAX_SINGLE_DEPOSIT.toLocaleString()}</span>
                </div>
                <div className="limit-item">
                  <span className="limit-label">Daily limit:</span>
                  <span className="limit-value">${DEPOSIT_LIMITS.MAX_DAILY_DEPOSIT.toLocaleString()}</span>
                </div>
                <div className="limit-item">
                  <span className="limit-label">Monthly limit:</span>
                  <span className="limit-value">${DEPOSIT_LIMITS.MAX_MONTHLY_DEPOSIT.toLocaleString()}</span>
                </div>
              </div>
              
              <div className="security-features">
                <div className="feature">
                  <span className="feature-icon">🛡️</span>
                  <span>All transactions are encrypted</span>
                </div>
                <div className="feature">
                  <span className="feature-icon">🔍</span>
                  <span>Fraud detection monitoring</span>
                </div>
                <div className="feature">
                  <span className="feature-icon">⚡</span>
                  <span>Instant fund availability</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </>
  );
}
