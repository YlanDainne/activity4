import { useState, useRef } from 'react';
import './App.css';

const CATALOG = [
  {
    id: 'P100',
    name: 'Wireless Mouse',
    icon: '🖱️',
    price: 1890,
    category: 'Peripherals',
    initialStock: 25,
    badge: 'SPATIAL SENSOR',
    badgeType: 'hot',
    desc: 'Sub-millimeter optical precision with haptic force feedback and translucent frosted shell.',
  },
  {
    id: 'P200',
    name: 'Mechanical Keyboard',
    icon: '⌨️',
    price: 4250,
    category: 'Hardware',
    initialStock: 10,
    badge: 'LIMITED ARCHIVE',
    badgeType: 'limited',
    desc: 'Magnetic hall-effect switches with adjustable actuation and precision milled aluminum chassis.',
  },
  {
    id: 'P300',
    name: 'USB-C Hub',
    icon: '🔌',
    price: 1290,
    category: 'Gear',
    initialStock: 0,
    badge: 'DEPLETED',
    badgeType: 'soldout',
    desc: 'Dual 4K spatial display output, high-bandwidth Thunderbolt expansion, and 100W Power Delivery.',
  },
];

// Lightweight browser-native synthesized spatial acoustic feedback
function playSpatialSound(type) {
  try {
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    if (!AudioContext) return;
    const ctx = new AudioContext();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);

    const now = ctx.currentTime;
    if (type === 'click') {
      osc.type = 'sine';
      osc.frequency.setValueAtTime(540, now);
      osc.frequency.exponentialRampToValueAtTime(880, now + 0.05);
      gain.gain.setValueAtTime(0.06, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05);
      osc.start(now);
      osc.stop(now + 0.05);
    } else if (type === 'confirm') {
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(587.33, now); // D5
      osc.frequency.setValueAtTime(880, now + 0.08); // A5
      gain.gain.setValueAtTime(0.08, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.28);
      osc.start(now);
      osc.stop(now + 0.28);
    } else if (type === 'reject') {
      osc.type = 'sine';
      osc.frequency.setValueAtTime(260, now);
      osc.frequency.exponentialRampToValueAtTime(140, now + 0.14);
      gain.gain.setValueAtTime(0.09, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.15);
      osc.start(now);
      osc.stop(now + 0.15);
    }
  } catch {
    // AudioContext blocked or not allowed until user interaction
  }
}

export default function App() {
  const [productId, setProductId] = useState('P100');
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [recentOrders, setRecentOrders] = useState([]);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [stockMap, setStockMap] = useState({
    P100: 22,
    P200: 8,
    P300: 0,
  });

  const cardsContainerRef = useRef(null);

  const selectedProduct = CATALOG.find((p) => p.id === productId) || CATALOG[0];
  const currentStock = stockMap[productId] !== undefined ? stockMap[productId] : selectedProduct.initialStock;

  const triggerSound = (type) => {
    if (soundEnabled) {
      playSpatialSound(type);
    }
  };

  const handleQuantityChange = (delta) => {
    triggerSound('click');
    setQuantity((prev) => Math.max(1, Math.min(99, Number(prev) + delta)));
  };

  const handleSetQuickQuantity = (qty) => {
    triggerSound('click');
    setQuantity(qty);
  };

  const handleSelectProduct = (id) => {
    triggerSound('click');
    setProductId(id);
    setResult(null);
  };

  // 3D Tilt Glare tracking on mouse move
  const handleCardMouseMove = (e, cardElem) => {
    if (!cardElem) return;
    const rect = cardElem.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    cardElem.style.setProperty('--mouse-x', `${(x / rect.width) * 100}%`);
    cardElem.style.setProperty('--mouse-y', `${(y / rect.height) * 100}%`);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;

    triggerSound('click');
    setLoading(true);
    setResult(null);

    const startTime = performance.now();

    try {
      const response = await fetch('http://localhost:8080/api/orders', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: JSON.stringify({
          productId,
          quantity: Number(quantity),
        }),
      });

      const data = await response.json();
      const elapsed = Math.round(performance.now() - startTime);

      const outcome = {
        ...data,
        productId,
        productName: selectedProduct.name,
        unitPrice: selectedProduct.price,
        quantity: Number(quantity),
        timestamp: new Date().toLocaleTimeString(),
        elapsedMs: elapsed,
      };

      if (outcome.inventory !== null && outcome.inventory !== undefined) {
        setStockMap((prev) => ({
          ...prev,
          [productId]: outcome.inventory,
        }));
      }

      setResult(outcome);
      setRecentOrders((prev) => [outcome, ...prev.slice(0, 4)]);

      if (outcome.status === 'CONFIRMED') {
        triggerSound('confirm');
      } else {
        triggerSound('reject');
      }
    } catch (err) {
      const errorOutcome = {
        status: 'REJECTED',
        reason: 'Network connection issue: ' + err.message,
        inventory: currentStock,
        productId,
        productName: selectedProduct.name,
        unitPrice: selectedProduct.price,
        quantity: Number(quantity),
        timestamp: new Date().toLocaleTimeString(),
        elapsedMs: 0,
      };
      setResult(errorOutcome);
      triggerSound('reject');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="spatial-viewport">
      {/* Volumetric Ambient Lighting Spheres */}
      <div className="spatial-ambient-canvas" aria-hidden="true">
        <div className="spatial-orb spatial-orb-cyan" />
        <div className="spatial-orb spatial-orb-violet" />
        <div className="spatial-orb spatial-orb-mint" />
      </div>

      {/* Floating Spatial Audio Toggle Button */}
      <div className="spatial-top-bar">
        <button
          type="button"
          className="dock-audio-btn minimal-audio-toggle"
          onClick={() => setSoundEnabled(!soundEnabled)}
          aria-label="Toggle spatial sound feedback"
          title="Toggle audio feedback"
        >
          <span>{soundEnabled ? '🔊' : '🔇'}</span>
          <span>{soundEnabled ? 'Audio On' : 'Muted'}</span>
        </button>
      </div>

      {/* Main Spatial Stage */}
      <main className="spatial-main-container">
        {/* Volumetric Header */}
        <header className="spatial-hero">
          <h1 className="spatial-hero-title">
            YLAN&apos;S <span>SHOP</span>
          </h1>
        </header>

        {/* Spatial Grid: Catalog & Dispatch Panel */}
        <div className="spatial-grid">
          {/* Left Column: Spatial Products */}
          <section className="spatial-catalog-column">
            <div className="spatial-section-header">
              <h2>
                <span>❖</span> The Spatial Collection
              </h2>
              <span className="spatial-badge-counter">{CATALOG.length} VAULT ITEMS</span>
            </div>

            <div className="spatial-cards-list" ref={cardsContainerRef}>
              {CATALOG.map((item) => {
                const isSelected = item.id === productId;
                const liveStock = stockMap[item.id] !== undefined ? stockMap[item.id] : item.initialStock;
                const isOutOfStock = liveStock <= 0;

                return (
                  <div key={item.id} className="spatial-card-outer">
                    <div
                      className={`spatial-card ${isSelected ? 'active-selected' : ''} ${
                        isOutOfStock ? 'depleted-card' : ''
                      }`}
                      onClick={() => handleSelectProduct(item.id)}
                      onMouseMove={(e) => handleCardMouseMove(e, e.currentTarget)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => e.key === 'Enter' && handleSelectProduct(item.id)}
                    >
                      <div className="card-header-row">
                        <div className="card-left-cluster">
                          <div className="spatial-hologram-orb">{item.icon}</div>
                          <div className="card-titles">
                            <span className="card-sku">ITEM #{item.id}</span>
                            <h3>{item.name}</h3>
                          </div>
                        </div>

                        <div className="card-pill-tags">
                          <span className={`spatial-edition-badge ${item.badgeType}`}>
                            {item.badge}
                          </span>
                        </div>
                      </div>

                      <p className="card-desc">{item.desc}</p>

                      <div className="card-footer-row">
                        <div className="card-price-group">
                          <span className="price-symbol">₱</span>
                          <span className="price-value">{item.price.toLocaleString()}</span>
                        </div>

                        <span className={`stock-capsule ${liveStock > 0 ? 'in-stock' : 'out-stock'}`}>
                          {liveStock > 0 ? `● ${liveStock} In Stock` : '● Depleted'}
                        </span>

                        {isSelected && (
                          <span className="card-selected-indicator">
                            ✓ Ready
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          {/* Right Column: Spatial Dispatch Terminal */}
          <section className="spatial-terminal-column">
            <div className="spatial-terminal-panel">
              <div className="terminal-header">
                <div className="terminal-heading-group">
                  <span className="terminal-icon">✦</span>
                  <h3>Dispatch Terminal</h3>
                </div>
                <span className="terminal-telemetry-badge">LIVE SYNC</span>
              </div>

              {/* Selected Focus Card */}
              <div className="selected-focus-window">
                <div className="focus-left">
                  <div className="focus-orb">{selectedProduct.icon}</div>
                  <div className="focus-details">
                    <h4>{selectedProduct.name}</h4>
                    <span>SKU: {selectedProduct.id} &middot; {selectedProduct.category}</span>
                  </div>
                </div>
                <div className="focus-cost">
                  <span className="unit-tag">PRICE</span>
                  <span className="val">₱{selectedProduct.price.toLocaleString()}</span>
                </div>
              </div>

              {/* Order Form */}
              <form onSubmit={handleSubmit} className="spatial-order-form">
                <div className="spatial-field-group">
                  <div className="field-label-bar">
                    <label htmlFor="spatial-qty-input">Reservation Units</label>
                    <span className="field-stock-info">
                      Stock: <strong>{currentStock} available</strong>
                    </span>
                  </div>

                  <div className="spatial-stepper">
                    <button
                      type="button"
                      className="stepper-action-btn"
                      onClick={() => handleQuantityChange(-1)}
                      disabled={quantity <= 1 || loading}
                      aria-label="Decrease quantity"
                    >
                      &minus;
                    </button>
                    <input
                      id="spatial-qty-input"
                      type="number"
                      className="stepper-display-input"
                      min="1"
                      max="99"
                      value={quantity}
                      onChange={(e) => setQuantity(e.target.value)}
                      required
                    />
                    <button
                      type="button"
                      className="stepper-action-btn"
                      onClick={() => handleQuantityChange(1)}
                      disabled={quantity >= 99 || loading}
                      aria-label="Increase quantity"
                    >
                      &#43;
                    </button>
                  </div>

                  <div className="quick-preset-row">
                    <button
                      type="button"
                      className="preset-capsule"
                      onClick={() => handleSetQuickQuantity(1)}
                    >
                      1 Unit
                    </button>
                    <button
                      type="button"
                      className="preset-capsule"
                      onClick={() => handleSetQuickQuantity(2)}
                    >
                      2 Units
                    </button>
                    <button
                      type="button"
                      className="preset-capsule"
                      onClick={() => handleSetQuickQuantity(5)}
                    >
                      5 Units
                    </button>
                  </div>
                </div>

                {/* Subtotal Pod */}
                <div className="spatial-subtotal-pod">
                  <span className="subtotal-label">Subtotal</span>
                  <span className="subtotal-amount">
                    ₱{(selectedProduct.price * Number(quantity || 1)).toLocaleString()}
                  </span>
                </div>

                {/* Dispatch Button */}
                <button
                  id="submit-order-btn"
                  type="submit"
                  className="spatial-cta-btn"
                  disabled={loading}
                >
                  {loading ? (
                    <>
                      <span className="spatial-spinner" />
                      <span>Transmitting to Vault...</span>
                    </>
                  ) : (
                    <>
                      <span>Dispatch Order</span>
                      <span>➔</span>
                    </>
                  )}
                </button>
              </form>

              {/* Volumetric Outcome Notification */}
              {result && (
                <div
                  id="order-result-banner"
                  className={`spatial-outcome-window ${
                    result.status === 'CONFIRMED' ? 'confirmed-window' : 'rejected-window'
                  }`}
                >
                  <div className="outcome-top-bar">
                    <span className="outcome-status-pill">
                      {result.status === 'CONFIRMED' ? '✓ ORDER CONFIRMED' : '✕ RESERVATION HELD'}
                    </span>
                    <span className="outcome-latency">{result.elapsedMs}ms</span>
                  </div>

                  <div className="outcome-body">
                    {result.status === 'CONFIRMED' ? (
                      <>
                        <h4>Vault Dispatch Sealed</h4>
                        <p>
                          Reserved <strong>{result.quantity}x {result.productName}</strong>. Your
                          order has been validated and recorded.
                        </p>
                      </>
                    ) : (
                      <>
                        <h4>Order Declined</h4>
                        <p>
                          {result.reason || 'Requested quantity exceeds available vault inventory.'}
                        </p>
                      </>
                    )}

                    {result.inventory !== null && result.inventory !== undefined && (
                      <div className="outcome-stock-telemetry">
                        <span className="telemetry-label">Live Remaining Stock:</span>
                        <span className="telemetry-val">{result.inventory} units</span>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Spatial Perks Row */}
              <div className="spatial-perks-row">
                <div className="spatial-perk">
                  <span className="perk-glyph">⚡</span>
                  <span>Instant Verification</span>
                </div>
                <div className="spatial-perk">
                  <span className="perk-glyph">🛡️</span>
                  <span>Direct Guarantee</span>
                </div>
                <div className="spatial-perk">
                  <span className="perk-glyph">📦</span>
                  <span>Priority Courier</span>
                </div>
                <div className="spatial-perk">
                  <span className="perk-glyph">✧</span>
                  <span>Original Edition</span>
                </div>
              </div>
            </div>
          </section>
        </div>

        {/* Spatial Session History Drawer */}
        <section className="spatial-ledger-card">
          <div className="ledger-top">
            <h3>
              <span>📋</span> Session Order Record
            </h3>
            <span className="spatial-badge-counter">{recentOrders.length} ENTRIES</span>
          </div>

          {recentOrders.length === 0 ? (
            <div className="empty-ledger-state">
              No orders logged in this spatial session yet. Select an item and dispatch above.
            </div>
          ) : (
            <div className="ledger-rows-wrapper">
              {recentOrders.map((order, index) => (
                <div key={index} className="spatial-ledger-item">
                  <div className="item-left">
                    <span
                      className={`item-status-pill ${
                        order.status === 'CONFIRMED' ? 'confirmed' : 'rejected'
                      }`}
                    >
                      {order.status}
                    </span>
                    <span className="item-name">{order.productName}</span>
                    <span className="item-qty">x{order.quantity}</span>
                  </div>

                  <div className="item-right">
                    <span>₱{(order.unitPrice * order.quantity).toLocaleString()}</span>
                    <span>&middot;</span>
                    <span>{order.timestamp}</span>
                    <span>&middot;</span>
                    <span>{order.elapsedMs}ms</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Floating Grabber Bar (visionOS Ergonomics) */}
        <footer className="spatial-grabber-bar-container">
          <div className="spatial-grabber-pill" />
          <div className="spatial-footer-info">
            <span>YLAN&apos;S SHOP</span>
            <span>&bull;</span>
            <span>SPATIAL VAULT COMPUTING</span>
            <span>&bull;</span>
            <span>&copy; {new Date().getFullYear()}</span>
          </div>
        </footer>
      </main>
    </div>
  );
}