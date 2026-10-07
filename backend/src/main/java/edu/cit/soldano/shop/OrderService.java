package edu.cit.soldano.shop;

import edu.cit.soldano.events.OrderItemRecord;
import edu.cit.soldano.events.OrderPlacedEvent;
import edu.cit.soldano.events.OrderRejectedEvent;
import edu.cit.soldano.inventory.InventoryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    OrderService(OrderRepository orderRepository, InventoryService inventoryService, ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        List<ItemOutcome> outcomes = new ArrayList<>();

        boolean hasStockShortage = false;
        String failedProductId = null;

        // Phase 1: All-or-nothing stock validation
        for (OrderRequestItem item : request.items()) {
            boolean available = inventoryService.checkStock(item.productId(), item.quantity());
            if (!available) {
                hasStockShortage = true;
                failedProductId = item.productId();
                outcomes.add(new ItemOutcome(item.productId(), "INSUFFICIENT_STOCK"));
            } else {
                outcomes.add(new ItemOutcome(item.productId(), "AVAILABLE"));
            }
        }

        if (hasStockShortage) {
            String failureReason = "Insufficient stock for product " + failedProductId;
            Order rejectedOrder = new Order(orderId, "REJECTED", failureReason);
            orderRepository.save(rejectedOrder);

            eventPublisher.publishEvent(new OrderRejectedEvent(orderId, failureReason));
            return new OrderResponse(orderId, "REJECTED", failureReason, outcomes);
        }

        // Phase 2: Reserve stock and save confirmed order
        Order confirmedOrder = new Order(orderId, "CONFIRMED", null);
        List<OrderItem> lineItems = new ArrayList<>();
        List<OrderItemRecord> eventItems = new ArrayList<>();
        List<ItemOutcome> successOutcomes = new ArrayList<>();

        for (OrderRequestItem item : request.items()) {
            inventoryService.reserve(item.productId(), item.quantity());
            lineItems.add(new OrderItem(confirmedOrder, item.productId(), item.quantity()));
            eventItems.add(new OrderItemRecord(item.productId(), item.quantity()));
            successOutcomes.add(new ItemOutcome(item.productId(), "RESERVED"));
        }

        confirmedOrder.setItems(lineItems);
        orderRepository.save(confirmedOrder);

        eventPublisher.publishEvent(new OrderPlacedEvent(orderId, eventItems));

        return new OrderResponse(orderId, "CONFIRMED", null, successOutcomes);
    }

    @Transactional
    public void cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if ("CANCELLED".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already CANCELLED");
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);
    }

    public List<OrderHistoryDto> getOrderHistory() {
        return orderRepository.findAll().stream()
                .map(o -> new OrderHistoryDto(
                        o.getOrderId(),
                        o.getStatus(),
                        o.getReason(),
                        o.getItems().stream().map(i -> new OrderRequestItem(i.getProductId(), i.getQuantity())).toList()
                ))
                .toList();
    }
}