package edu.cit.soldano.notification;

import edu.cit.soldano.events.LowStockEvent;
import edu.cit.soldano.events.OrderPlacedEvent;
import edu.cit.soldano.events.OrderRejectedEvent;
import edu.cit.soldano.supplier.SupplierGateway;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationEventListener {

    private final SupplierGateway supplierGateway;
    private final NotificationRepository repository;

    public NotificationEventListener(SupplierGateway supplierGateway, NotificationRepository repository) {
        this.supplierGateway = supplierGateway;
        this.repository = repository;
    }

    @EventListener
    @Transactional
    public void onOrderPlaced(OrderPlacedEvent event) {
        repository.save(new Notification("Order " + event.orderId() + " confirmed"));
    }

    @EventListener
    @Transactional
    public void onOrderRejected(OrderRejectedEvent event) {
        repository.save(new Notification("Order " + event.orderId() + " rejected: " + event.reason()));
    }

    @EventListener
    @Transactional
    public void onLowStock(LowStockEvent event) {
        repository.save(new Notification("Low stock alert: Product " + event.productId()
                + " has " + event.currentStock() + " remaining (reorder requested)"));
        System.out.println(">>> [AUTO-REORDER] Low stock detected for: " + event.productId() + " (remaining: " + event.currentStock() + ")");
        supplierGateway.placeReorder(event.productId(), 12);
    }
}