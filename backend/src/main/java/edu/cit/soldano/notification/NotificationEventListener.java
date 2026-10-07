package edu.cit.soldano.notification;

import edu.cit.soldano.events.LowStockEvent;
import edu.cit.soldano.events.OrderPlacedEvent;
import edu.cit.soldano.events.OrderRejectedEvent;
import edu.cit.soldano.supplier.SupplierGateway;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class NotificationEventListener {
    private final NotificationRepository repository;
    private final SupplierGateway supplierGateway;

    NotificationEventListener(NotificationRepository repository, SupplierGateway supplierGateway) {
        this.repository = repository;
        this.supplierGateway = supplierGateway;
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
        repository.save(new Notification("Low stock alert: Product " + event.productId() + " has " + event.currentStock() + " remaining (reorder needed)"));
        supplierGateway.placeReorder(event.productId(), 12);
    }
}