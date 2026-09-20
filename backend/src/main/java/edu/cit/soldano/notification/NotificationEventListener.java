package edu.cit.soldano.notification;

import edu.cit.soldano.events.LowStockEvent;
import edu.cit.soldano.events.OrderPlacedEvent;
import edu.cit.soldano.events.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.cit.soldano.events.LowStockEvent;
import edu.cit.soldano.events.OrderPlacedEvent;
import edu.cit.soldano.events.OrderRejectedEvent;

@Service
class NotificationEventListener {
    private final NotificationRepository repository;

    NotificationEventListener(NotificationRepository repository) {
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
        repository.save(new Notification("Low stock alert: Product " + event.productId() + " has " + event.currentStock() + " remaining (reorder needed)"));
    }
}