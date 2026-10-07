package edu.cit.soldano.channel;

import jakarta.persistence.*;

@Entity
@Table(name = "marketplace_orders")
class MarketplaceOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tiangge_order_id", nullable = false, unique = true)
    private String tianggeOrderId;
    @Column(name = "placed_event_id", nullable = false, unique = true)
    private String placedEventId;
    @Column(name = "shop_order_id", nullable = false)
    private String shopOrderId;
    @Column(name = "lines_json", nullable = false, columnDefinition = "TEXT")
    private String linesJson;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketplaceOrderStatus status;

    public MarketplaceOrder() {}

    MarketplaceOrder(String tianggeOrderId, String placedEventId, String shopOrderId,
                      String linesJson, MarketplaceOrderStatus status) {
        this.tianggeOrderId = tianggeOrderId;
        this.placedEventId = placedEventId;
        this.shopOrderId = shopOrderId;
        this.linesJson = linesJson;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getTianggeOrderId() { return tianggeOrderId; }
    public String getPlacedEventId() { return placedEventId; }
    public String getShopOrderId() { return shopOrderId; }
    public void setShopOrderId(String shopOrderId) { this.shopOrderId = shopOrderId; }
    public String getLinesJson() { return linesJson; }
    public MarketplaceOrderStatus getStatus() { return status; }
    public void setStatus(MarketplaceOrderStatus status) { this.status = status; }
}
