package edu.cit.soldano.inventory;

import edu.cit.soldano.events.SupplierOrderDeliveredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
class SupplierDeliveryListener {
    private final InventoryService inventoryService;

    SupplierDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    @Order(0)
    public void restock(SupplierOrderDeliveredEvent event) {
        inventoryService.restock(event.productId(), event.units());
    }
}