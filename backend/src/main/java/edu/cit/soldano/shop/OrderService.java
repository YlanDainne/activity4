package edu.cit.soldano.shop;

import edu.cit.soldano.inventory.InventoryItem;
import edu.cit.soldano.inventory.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService; // Boundary enforced: injected interface only

    public OrderService(OrderRepository orderRepository, InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        var optItem = inventoryService.getItem(request.productId());

        if (optItem.isEmpty()) {
            orderRepository.save(new Order(request.productId(), request.quantity(), "REJECTED", "Product not found"));
            return new OrderResponse("REJECTED", "Product not found", null);
        }

        boolean reserved = inventoryService.reserve(request.productId(), request.quantity());
        InventoryItem currentItem = inventoryService.getItem(request.productId()).orElse(null);
        int remainingStock = (currentItem != null) ? currentItem.getStock() : 0;

        if (reserved) {
            orderRepository.save(new Order(request.productId(), request.quantity(), "CONFIRMED", null));
            return new OrderResponse("CONFIRMED", null, remainingStock);
        } else {
            String reason = "Insufficient stock. Available: " + remainingStock;
            orderRepository.save(new Order(request.productId(), request.quantity(), "REJECTED", reason));
            return new OrderResponse("REJECTED", reason, remainingStock);
        }
    }
}