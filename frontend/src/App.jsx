import { useState, useEffect, useRef } from 'react';
import './App.css';

const CATALOG = [
  {
    id: 'P100',
    name: 'Mechanical Keyboard',
    icon: '⌨️',
    price: 4250,
    category: 'Hardware',
    badge: 'SPATIAL SENSOR',
    badgeType: 'hot',
    desc: 'Sub-millimeter optical precision with haptic force feedback and translucent frosted shell.',
  },
  {
    id: 'P200',
    name: 'Wireless Mouse',
    icon: '🖱️',
    price: 1890,
    category: 'Peripherals',
    badge: 'LIMITED ARCHIVE',
    badgeType: 'limited',
    desc: 'Magnetic hall-effect switches with adjustable actuation and aluminum chassis.',
  },
  {
    id: 'P300',
    name: 'USB-C Monitor Hub',
    icon: '🔌',
    price: 1290,
    category: 'Gear',
    badge: 'HIGH DEMAND',
    badgeType: 'soldout',
    desc: 'Dual 4K spatial display output, high-bandwidth Thunderbolt expansion, and 100W PD.',
  },
];

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
      osc.frequency.setValueAtTime(587.33, now);
      osc.frequency.setValueAtTime(880, now + 0.08);
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
  } catch {}
}

export default function App() {
  const [stockMap, setStockMap] = useState({});
  const [cart, setCart] = useState([]);
  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);
  const [lastResult, setLastResult] = useState(null);

  const cardsContainerRef = useRef(null);

  // Fetch live state from Supabase/Spring Boot
  const fetchAllData = async () => {
    try {
      const [invRes, orderRes, notifRes] = await Promise.all([
        fetch('http://localhost:8080/api/inventory'),
        fetch('http://localhost:8080/api/orders'),
        fetch('http://localhost:8080/api/notifications'),
      ]);

      if (invRes.ok) {
        const invData = await invRes.json();
        const map = {};
        invData.forEach((item) => {
          map[item.productId] = item.stock;
        });
        setStockMap(map);
      }

      if (orderRes.ok) {
        const orderData = await orderRes.json();
        setOrders(orderData.reverse());
      }

      if (notifRes.ok) {
        const notifData = await notifRes.json();
        setNotifications(notifData);
      }
    } catch (err) {
      console.error('Failed to sync data:', err);
    }
  };

  useEffect(() => {
    fetchAllData();
  }, []);

  const addToCart = (productId) => {
    playSpatialSound('click');
    setCart((prev) => {
      const existing = prev.find((i) => i.productId === productId);
      if (existing) {
        return prev.map((i) =>
          i.productId === productId ? { ...i, quantity: i.quantity + 1 } : i
        );
      }
      return [...prev, { productId, quantity: 1 }];
    });
  };

  const updateCartQty = (productId, delta) => {
    playSpatialSound('click');
    setCart((prev) =>
      prev
        .map((i) => {
          if (i.productId === productId) {
            const nextQty = i.quantity + delta;
            return nextQty > 0 ? { ...i, quantity: nextQty } : null;
          }
          return i;
        })
        .filter(Boolean)
    );
  };

  const clearCart = () => setCart([]);

  const handlePlaceOrder = async (e) => {
    e.preventDefault();
    if (cart.length === 0 || loading) return;

    playSpatialSound('click');
    setLoading(true);
    setLastResult(null);

    try {
      const res = await fetch('http://localhost:8080/api/orders', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({
          items: cart.map((item) => ({
            productId: item.productId,
            quantity: Number(item.quantity),
          })),
        }),
      });

      const data = await res.json();
      setLastResult(data);

      if (data.status === 'CONFIRMED') {
        playSpatialSound('confirm');
        clearCart();
      } else {
        playSpatialSound('reject');
      }

      // Re-fetch live inventory and updates
      await fetchAllData();
    } catch (err) {
      setLastResult({
        status: 'REJECTED',
        reason: 'Network error: ' + err.message,
      });
      playSpatialSound('reject');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelOrder = async (orderId) => {
    playSpatialSound('click');
    try {
      const res = await fetch(`http://localhost:8080/api/orders/${orderId}/cancel`, {
        method: 'POST',
      });
      if (res.ok) {
        playSpatialSound('confirm');
        await fetchAllData();
      }
    } catch (err) {
      console.error('Failed to cancel order:', err);
    }
  };

  const cartTotal = cart.reduce((sum, item) => {
    const prod = CATALOG.find((c) => c.id === item.productId);
    return sum + (prod ? prod.price * item.quantity : 0);
  }, 0);

  return (
    <div className="spatial-viewport">
      <div className="spatial-ambient-canvas" aria-hidden="true">
        <div className="spatial-orb spatial-orb-cyan" />
        <div className="spatial-orb spatial-orb-violet" />
        <div className="spatial-orb spatial-orb-mint" />
      </div>

      <main className="spatial-main-container">
        <header className="spatial-hero">
          <h1 className="spatial-hero-title">
            YLAN&apos;S <span>SHOP</span>
          </h1>
        </header>

        <div className="spatial-grid">
          {/* Left Column: Live Vault Catalog */}
          <section className="spatial-catalog-column">
            <div className="spatial-section-header">
              <h2><span>❖</span> The Vault Catalog</h2>
              <span className="spatial-badge-counter">{CATALOG.length} VAULT ITEMS</span>
            </div>

            <div className="spatial-cards-list" ref={cardsContainerRef}>
              {CATALOG.map((item) => {
                const liveStock = stockMap[item.id] !== undefined ? stockMap[item.id] : 0;
                const isOutOfStock = liveStock <= 0;
                const isLowStock = liveStock > 0 && liveStock < 5;

                return (
                  <div key={item.id} className="spatial-card-outer">
                    <div
                      className={`spatial-card ${isOutOfStock ? 'depleted-card' : ''} ${
                        isLowStock ? 'low-stock-highlight' : ''
                      }`}
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
                          {isLowStock && (
                            <span className="spatial-edition-badge limited">LOW STOCK (&lt;5)</span>
                          )}
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
                          ● {liveStock} In Supabase
                        </span>

                        <button
                          type="button"
                          className="stepper-action-btn"
                          disabled={isOutOfStock}
                          onClick={() => addToCart(item.id)}
                          style={{ padding: '4px 12px', fontSize: '0.9rem', width: 'auto' }}
                        >
                          + Add to Cart
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          {/* Right Column: Multi-Item Cart & Dispatch Terminal */}
          <section className="spatial-terminal-column">
            <div className="spatial-terminal-panel">
              <div className="terminal-header">
                <div className="terminal-heading-group">
                  <span className="terminal-icon">✦</span>
                  <h3>Multi-Item Cart</h3>
                </div>
                <span className="terminal-telemetry-badge">ALL-OR-NOTHING</span>
              </div>

              {cart.length === 0 ? (
                <div className="empty-ledger-state" style={{ padding: '24px 0' }}>
                  Cart is empty. Click "+ Add to Cart" on catalog items.
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', margin: '14px 0' }}>
                  {cart.map((ci) => {
                    const prod = CATALOG.find((c) => c.id === ci.productId);
                    return (
                      <div
                        key={ci.productId}
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          padding: '8px 12px',
                          background: 'rgba(255,255,255,0.05)',
                          borderRadius: '8px',
                        }}
                      >
                        <div>
                          <strong>{prod?.name || ci.productId}</strong>
                          <div style={{ fontSize: '0.8rem', opacity: 0.7 }}>
                            ₱{prod?.price.toLocaleString()} each
                          </div>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <button
                            type="button"
                            className="stepper-action-btn"
                            onClick={() => updateCartQty(ci.productId, -1)}
                          >
                            -
                          </button>
                          <span>{ci.quantity}</span>
                          <button
                            type="button"
                            className="stepper-action-btn"
                            onClick={() => updateCartQty(ci.productId, 1)}
                          >
                            +
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              <div className="spatial-subtotal-pod">
                <span className="subtotal-label">Cart Total</span>
                <span className="subtotal-amount">₱{cartTotal.toLocaleString()}</span>
              </div>

              <button
                id="submit-order-btn"
                type="button"
                className="spatial-cta-btn"
                disabled={loading || cart.length === 0}
                onClick={handlePlaceOrder}
              >
                {loading ? 'Dispatching...' : 'Place Multi-Item Order ➔'}
              </button>

              {lastResult && (
                <div
                  id="order-result-banner"
                  className={`spatial-outcome-window ${
                    lastResult.status === 'CONFIRMED' ? 'confirmed-window' : 'rejected-window'
                  }`}
                  style={{ marginTop: '16px' }}
                >
                  <div className="outcome-top-bar">
                    <span className="outcome-status-pill">
                      {lastResult.status === 'CONFIRMED' ? '✓ CONFIRMED' : '✕ REJECTED'}
                    </span>
                    <span className="outcome-latency">{lastResult.orderId || 'FAILED'}</span>
                  </div>
                  <div className="outcome-body">
                    {lastResult.status === 'CONFIRMED' ? (
                      <p>All items reserved and atomic transaction committed.</p>
                    ) : (
                      <p>{lastResult.reason || 'All-or-nothing rollback triggered.'}</p>
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* Notifications Feed */}
            <div className="spatial-terminal-panel" style={{ marginTop: '20px' }}>
              <div className="terminal-header">
                <div className="terminal-heading-group">
                  <span className="terminal-icon">🔔</span>
                  <h3>Domain Notifications</h3>
                </div>
                <span className="spatial-badge-counter">{notifications.length} EVENTS</span>
              </div>
              <div style={{ maxHeight: '180px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {notifications.length === 0 ? (
                  <div className="empty-ledger-state">No domain events received yet.</div>
                ) : (
                  notifications.map((n) => (
                    <div
                      key={n.notificationId}
                      style={{
                        padding: '6px 10px',
                        background: 'rgba(255,255,255,0.03)',
                        borderRadius: '6px',
                        fontSize: '0.82rem',
                      }}
                    >
                      <div>{n.message}</div>
                      <div style={{ opacity: 0.5, fontSize: '0.7rem' }}>
                        {new Date(n.createdAt).toLocaleTimeString()}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </section>
        </div>

        {/* Orders Table with Cancel & Restock */}
        <section className="spatial-ledger-card" style={{ marginTop: '28px' }}>
          <div className="ledger-top">
            <h3><span>📋</span> Order History & Restock Control</h3>
            <span className="spatial-badge-counter">{orders.length} ORDERS</span>
          </div>

          {orders.length === 0 ? (
            <div className="empty-ledger-state">No orders registered in Supabase.</div>
          ) : (
            <div className="ledger-rows-wrapper">
              {orders.map((o) => (
                <div key={o.orderId} className="spatial-ledger-item">
                  <div className="item-left">
                    <span
                      className={`item-status-pill ${
                        o.status === 'CONFIRMED' ? 'confirmed' : 'rejected'
                      }`}
                    >
                      {o.status}
                    </span>
                    <span className="item-name"><strong>{o.orderId}</strong></span>
                    <span className="item-qty">
                      {o.items?.map((it) => `${it.productId} (x${it.quantity})`).join(', ') || o.reason}
                    </span>
                  </div>

                  <div className="item-right">
                    {o.status === 'CONFIRMED' && (
                      <button
                        type="button"
                        onClick={() => handleCancelOrder(o.orderId)}
                        style={{
                          background: 'rgba(255, 77, 79, 0.2)',
                          color: '#ff4d4f',
                          border: '1px solid rgba(255, 77, 79, 0.4)',
                          borderRadius: '6px',
                          padding: '4px 8px',
                          cursor: 'pointer',
                        }}
                      >
                        Cancel & Restock
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}