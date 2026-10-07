package edu.cit.soldano.inventory;

import edu.cit.soldano.events.LowStockEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class InventoryServiceImpl implements InventoryService {

    private static final int LOW_STOCK_THRESHOLD = 5;
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    InventoryServiceImpl(InventoryRepository inventoryRepository, ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean checkStock(String productId, int quantity) {
        return inventoryRepository.findById(productId)
                .map(item -> item.getStock() >= quantity)
                .orElse(false);
    }

    @Override
    @Transactional
    public void reserve(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        if (item.getStock() < quantity) {
            throw new IllegalStateException("Insufficient stock for " + productId);
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);

        if (item.getStock() < LOW_STOCK_THRESHOLD) {
            eventPublisher.publishEvent(new LowStockEvent(productId, item.getStock()));
        }
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        item.setStock(item.getStock() + quantity);
        inventoryRepository.save(item);
    }

    @Override
    public List<ProductDto> getAllProducts() {
        return inventoryRepository.findAll().stream()
                .map(item -> new ProductDto(item.getProductId(), item.getProductName(), item.getStock()))
                .toList();
    }

    @org.springframework.context.event.EventListener
    public void onSupplierOrderDelivered(edu.cit.soldano.events.SupplierOrderDeliveredEvent event) {
        this.restock(event.productId(), event.units());
    }
}