import { useState, useEffect } from "react";
import { useAuth } from "../lib/useAuth";
import Navigation from "../components/Navigation";

// Pretrade control limits
const PRETRADE_LIMITS = {
  MAX_ORDER_VALUE: 50000, // Maximum order value (notional limit)
  MAX_QUANTITY: 1000, // Maximum shares per order
  MIN_QUANTITY: 1, // Minimum shares
  MAX_PRICE_DEVIATION: 0.05, // 5% max deviation (price bands)
  TICK_SIZE: 0.01, // Minimum price increment
  MAX_DAILY_ORDER_VALUE: 200000, // Maximum daily trading volume
  MIN_BUYING_POWER: 1000, // Minimum buying power required
  SHORT_SELL_ENABLED: false, // Short selling authorization
  MAX_POSITION_SIZE: 500 // Maximum position size per user
};

export default function PlaceOrder() {
  const { jwt } = useAuth();
  const [side, setSide] = useState("BUY");
  const [symbol, setSymbol] = useState("SPY");
  const [qty, setQty] = useState(1);
  const [price, setPrice] = useState<number | "">("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [accountBalance, setAccountBalance] = useState<number>(0);
  const [currentHoldings, setCurrentHoldings] = useState<{[symbol: string]: number}>({});

  // Available symbols from backend (matching OrderController)
  const availableSymbols = [
    { symbol: "SPY", name: "S&P 500 ETF", price: 445.50 },
    { symbol: "IVV", name: "iShares S&P 500 ETF", price: 445.20 },
    { symbol: "VOO", name: "Vanguard S&P 500 ETF", price: 445.80 },
    { symbol: "VTI", name: "Vanguard Total Stock Market ETF", price: 265.40 },
    { symbol: "QQQ", name: "Invesco QQQ Trust", price: 380.75 },
    { symbol: "VEA", name: "Vanguard FTSE Developed Markets ETF", price: 51.20 },
    { symbol: "VWO", name: "Vanguard FTSE Emerging Markets ETF", price: 42.85 },
    { symbol: "AGG", name: "iShares Core U.S. Aggregate Bond ETF", price: 101.50 },
    { symbol: "BND", name: "Vanguard Total Bond Market ETF", price: 73.25 },
    { symbol: "IWM", name: "iShares Russell 2000 ETF", price: 220.30 },
    { symbol: "EFA", name: "iShares MSCI EAFE ETF", price: 79.90 }
  ];

  // Get current market price for selected symbol
  const getCurrentPrice = () => {
    const selectedETF = availableSymbols.find(etf => etf.symbol === symbol);
    return selectedETF ? selectedETF.price : 0;
  };

  // Calculate order value
  const getOrderValue = () => {
    const marketPrice = getCurrentPrice();
    const effectivePrice = price || marketPrice;
    return effectivePrice * qty;
  };

  // Calculate buying power after this order
  const getBuyingPowerAfterOrder = () => {
    if (side === "SELL") return accountBalance + getOrderValue();
    return accountBalance - getOrderValue();
  };

  // Check if instrument is active (simplified - all ETFs considered active)
  const isInstrumentActive = () => {
    return availableSymbols.some(etf => etf.symbol === symbol);
  };

  // Validate price increment (tick size)
  const validateTickSize = (priceValue: number) => {
    if (!priceValue) return true; // Market order
    const remainder = priceValue % PRETRADE_LIMITS.TICK_SIZE;
    return Math.abs(remainder) < 0.001; // Allow for floating point precision
  };

  // Calculate current position size for user
  const getCurrentPositionSize = () => {
    return currentHoldings[symbol] || 0;
  };

  // Pre-trade Controls 
  const getPretradeChecks = () => {
    const marketPrice = getCurrentPrice();
    const orderValue = getOrderValue();
    const buyingPowerAfter = getBuyingPowerAfterOrder();
    const currentPosition = getCurrentPositionSize();
    const newPositionSize = side === "BUY" ? currentPosition + qty : currentPosition - qty;
    const checks = [];

    // 1. Sanity checks (quantity > 0, active instrument)
    if (qty > 0 && isInstrumentActive()) {
      checks.push({ 
        status: 'passed', 
        message: `Sanity check: Positive quantity (${qty}) and active instrument (${symbol})`,
        icon: 'PASS'
      });
    } else {
      const issue = qty <= 0 ? "invalid quantity" : "inactive instrument";
      checks.push({ 
        status: 'failed', 
        message: `Sanity check failed: ${issue}`,
        icon: 'FAIL'
      });
    }

    // 2. User limits (maximum position size)
    if (Math.abs(newPositionSize) <= PRETRADE_LIMITS.MAX_POSITION_SIZE) {
      checks.push({ 
        status: 'passed', 
        message: `Position size OK (${Math.abs(newPositionSize)} ≤ ${PRETRADE_LIMITS.MAX_POSITION_SIZE})`,
        icon: 'PASS'
      });
    } else {
      checks.push({ 
        status: 'failed', 
        message: `Position size excessive (${Math.abs(newPositionSize)} > ${PRETRADE_LIMITS.MAX_POSITION_SIZE})`,
        icon: 'FAIL'
      });
    }

    // 3. User limits (notional limits)
    if (orderValue <= PRETRADE_LIMITS.MAX_ORDER_VALUE) {
      checks.push({ 
        status: 'passed', 
        message: `Notional OK ($${orderValue.toFixed(2)} ≤ $${PRETRADE_LIMITS.MAX_ORDER_VALUE.toLocaleString()})`,
        icon: 'PASS'
      });
    } else {
      checks.push({ 
        status: 'failed', 
        message: `Notional excessive ($${orderValue.toFixed(2)} > $${PRETRADE_LIMITS.MAX_ORDER_VALUE.toLocaleString()})`,
        icon: 'FAIL'
      });
    }

    // 4. Buying power / available margin
    if (side === "BUY") {
      if (buyingPowerAfter >= PRETRADE_LIMITS.MIN_BUYING_POWER) {
        checks.push({ 
          status: 'passed', 
          message: `Sufficient buying power (Remaining: $${buyingPowerAfter.toFixed(2)})`,
          icon: 'PASS'
        });
      } else if (buyingPowerAfter >= 0) {
        checks.push({ 
          status: 'warning', 
          message: `Low buying power after order (Remaining: $${buyingPowerAfter.toFixed(2)})`,
          icon: 'WARN'
        });
      } else {
        checks.push({ 
          status: 'failed', 
          message: `Insufficient buying power (Deficit: $${Math.abs(buyingPowerAfter).toFixed(2)})`,
          icon: 'FAIL'
        });
      }
    } else {
      // For SELL orders, check if we have enough shares
      if (currentPosition >= qty) {
        checks.push({ 
          status: 'passed', 
          message: `Sufficient position for sale (${currentPosition} ≥ ${qty} shares)`,
          icon: 'PASS'
        });
      } else {
        checks.push({ 
          status: 'failed', 
          message: `Insufficient position (${currentPosition} < ${qty} shares)`,
          icon: 'FAIL'
        });
      }
    }

    // 5. Restrictions (short-sell if not authorized)
    if (side === "SELL" && newPositionSize < 0 && !PRETRADE_LIMITS.SHORT_SELL_ENABLED) {
      checks.push({ 
        status: 'failed', 
        message: `Short selling prohibited (new position: ${newPositionSize})`,
        icon: 'FAIL'
      });
    } else if (side === "SELL" && newPositionSize < 0 && PRETRADE_LIMITS.SHORT_SELL_ENABLED) {
      checks.push({ 
        status: 'warning', 
        message: `Short selling authorized (new position: ${newPositionSize})`,
        icon: 'WARN'
      });
    } else {
      checks.push({ 
        status: 'passed', 
        message: `No short selling detected`,
        icon: 'PASS'
      });
    }

    // 6. Price rules (price bands)
    if (price) {
      const deviation = Math.abs(price - marketPrice) / marketPrice;
      if (deviation <= PRETRADE_LIMITS.MAX_PRICE_DEVIATION) {
        checks.push({ 
          status: 'passed', 
          message: `Price within authorized bands (${(deviation * 100).toFixed(1)}% ≤ ${(PRETRADE_LIMITS.MAX_PRICE_DEVIATION * 100).toFixed(0)}%)`,
          icon: 'PASS'
        });
      } else {
        checks.push({ 
          status: 'failed', 
          message: `Price outside bands (${(deviation * 100).toFixed(1)}% > ${(PRETRADE_LIMITS.MAX_PRICE_DEVIATION * 100).toFixed(0)}%)`,
          icon: 'FAIL'
        });
      }

      // 7. Price rules (tick size)
      if (validateTickSize(price)) {
        checks.push({ 
          status: 'passed', 
          message: `Tick size respected (multiple of $${PRETRADE_LIMITS.TICK_SIZE})`,
          icon: 'PASS'
        });
      } else {
        checks.push({ 
          status: 'failed', 
          message: `Invalid tick size (must be multiple of $${PRETRADE_LIMITS.TICK_SIZE})`,
          icon: 'FAIL'
        });
      }
    } else {
      checks.push({ 
        status: 'passed', 
        message: `Market order - no price controls`,
        icon: 'PASS'
      });
    }

    // Control summary
    const failedChecks = checks.filter(c => c.status === 'failed').length;
    const warningChecks = checks.filter(c => c.status === 'warning').length;
    
    if (failedChecks === 0 && warningChecks === 0) {
      checks.push({ 
        status: 'passed', 
        message: 'All pre-trade controls validated - Order approved',
        icon: 'PASS'
      });
    } else if (failedChecks === 0) {
      checks.push({ 
        status: 'warning', 
        message: `${warningChecks} warning(s) - Review recommended`,
        icon: 'WARN'
      });
    } else {
      checks.push({ 
        status: 'failed', 
        message: `${failedChecks} critical issue(s) must be resolved`,
        icon: 'FAIL'
      });
    }

    return checks;
  };

  const canPlaceOrder = () => {
    const checks = getPretradeChecks();
    return !checks.some(check => check.status === 'failed');
  };

  // Load account data on component mount
  useEffect(() => {
    async function loadAccountData() {
      if (!jwt) return;
      
      try {
        // Load account balance
        const balanceResponse = await fetch('http://localhost:8080/api/wallet/balance', {
          headers: { 'Authorization': `Bearer ${jwt}` }
        });
        if (balanceResponse.ok) {
          const balance = await balanceResponse.json();
          setAccountBalance(typeof balance === 'number' ? balance : 0);
        }

        // Load current holdings
        const holdingsResponse = await fetch('http://localhost:8080/api/orders/holdings', {
          headers: { 'Authorization': `Bearer ${jwt}` }
        });
        if (holdingsResponse.ok) {
          const holdings = await holdingsResponse.json();
          setCurrentHoldings(holdings.holdings || {});
        }
      } catch (error) {
        console.error('Failed to load account data:', error);
        // Set defaults on error
        setAccountBalance(0);
        setCurrentHoldings({});
      }
    }

    loadAccountData();
  }, [jwt]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setSuccess("");

    if (!canPlaceOrder()) {
      setError("Order failed pretrade validation checks");
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch(`http://localhost:8080/api/orders/placeOrder?symbol=${symbol}&quantity=${qty}&orderType=${side}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${jwt}`,
          'Content-Type': 'application/json'
        }
      });
      const message = await response.text();
      if (response.ok) {
        setSuccess(`Order placed successfully: ${message}`);
        // Reset form
        setSymbol("SPY");
        setQty(1);
        setPrice("");
      } else {
        setError(`Order failed: ${message}`);
      }
    } catch (error) {
      setError(`Network error: ${error}`);
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <main className="place-order-page">
      <Navigation />
      <div className="place-order-container">
        <div className="place-order-header">
          <h1>Place Order</h1>
          <p>Execute trades with advanced pretrade validation</p>
        </div>
        
        {error && <div className="alert alert-error">{error}</div>}
        {success && <div className="alert alert-success">{success}</div>}
        
        <div className="place-order-form-container">
          <form onSubmit={submit} className="place-order-form">
            <div className="form-row">
              <div className="form-group">
                <label>Order Type:</label>
                <select 
                  value={side} 
                  onChange={e=>setSide(e.target.value)} 
                  className="form-select"
                  disabled={isLoading}
                >
                  <option value="BUY">BUY</option>
                  <option value="SELL">SELL</option>
                </select>
              </div>

              <div className="form-group">
                <label>Symbol:</label>
                <select 
                  value={symbol} 
                  onChange={e=>setSymbol(e.target.value)} 
                  className="form-select"
                  disabled={isLoading}
                >
                  {availableSymbols.map(etf => (
                    <option key={etf.symbol} value={etf.symbol}>
                      {etf.symbol} - {etf.name}
                    </option>
                  ))}
                </select>
                <div className="market-info">
                  <span>Current Price: <strong>${getCurrentPrice().toFixed(2)}</strong></span>
                </div>
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label>Quantity:</label>
                <input 
                  type="number" 
                  value={qty} 
                  onChange={e=>setQty(+e.target.value)} 
                  placeholder="Enter quantity" 
                  min="1"
                  max={PRETRADE_LIMITS.MAX_QUANTITY}
                  className="form-input"
                  disabled={isLoading}
                />
              </div>

              <div className="form-group">
                <label>Limit Price (optional):</label>
                <input 
                  type="number" 
                  value={price} 
                  onChange={e=>setPrice(e.target.value === "" ? "" : +e.target.value)} 
                  placeholder="Market order if empty"
                  step="0.01"
                  className="form-input"
                  disabled={isLoading}
                />
              </div>
            </div>

            <div className="order-summary">
              <div className="summary-row">
                <span>Order Value:</span>
                <span className="value">${getOrderValue().toFixed(2)}</span>
              </div>
              <div className="summary-row total">
                <span>Total Cost:</span>
                <span className="value">${getOrderValue().toFixed(2)}</span>
              </div>
            </div>

            <div className="pretrade-controls">
              <h4>Pre-Trade Validation</h4>
              <div className="validation-checks">
                {getPretradeChecks().map((check, index) => (
                  <div key={index} className={`validation-item ${check.status}`}>
                    <span className="status-indicator">{check.icon}</span>
                    <span className="validation-message">{check.message}</span>
                  </div>
                ))}
              </div>
              
              <div className="limits-info">
                <h5>Trading Limits</h5>
                <div className="limits-grid">
                  <div className="limit-item">
                    <span>Max Order Value:</span>
                    <span>${PRETRADE_LIMITS.MAX_ORDER_VALUE.toLocaleString()}</span>
                  </div>
                  <div className="limit-item">
                    <span>Max Quantity:</span>
                    <span>{PRETRADE_LIMITS.MAX_QUANTITY.toLocaleString()} shares</span>
                  </div>
                  <div className="limit-item">
                    <span>Price Deviation:</span>
                    <span>±{(PRETRADE_LIMITS.MAX_PRICE_DEVIATION * 100).toFixed(0)}%</span>
                  </div>
                  <div className="limit-item">
                    <span>Account Balance:</span>
                    <span>${accountBalance.toFixed(2)}</span>
                  </div>
                  <div className="limit-item">
                    <span>Current Holdings:</span>
                    <span>{Object.keys(currentHoldings).length} positions</span>
                  </div>
                </div>
              </div>
            </div>

            <button 
              type="submit" 
              className={`place-order-button ${side.toLowerCase()} ${isLoading ? 'loading' : ''}`}
              disabled={isLoading || !canPlaceOrder()}
            >
              {isLoading ? 'Processing Order...' : `${side} ${qty} ${symbol}`}
            </button>
          </form>
        </div>
      </div>
    </main>
  );
}
