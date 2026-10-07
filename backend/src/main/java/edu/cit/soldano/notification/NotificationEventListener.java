package edu.cit.soldano.notification;

import edu.cit.soldano.events.LowStockEvent;
import edu.cit.soldano.supplier.SupplierGateway;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final SupplierGateway supplierGateway;

    public NotificationEventListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        System.out.println(">>> [AUTO-REORDER] Low stock detected for: " + event.productId() + " (remaining: " + event.currentStock() + ")");
        supplierGateway.placeReorder(event.productId(), 12);
    }
}