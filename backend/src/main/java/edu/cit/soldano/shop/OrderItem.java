package edu.cit.soldano.shop;

import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    private String productId;
    private int quantity;

    public OrderItem() {}
    public OrderItem(Order order, String productId, int quantity) {
        this.order = order;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Integer getId() { return id; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
}