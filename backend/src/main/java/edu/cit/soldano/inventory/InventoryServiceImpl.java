package edu.cit.soldano.inventory;

import edu.cit.soldano.events.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class InventoryServiceImpl implements InventoryService {

    private final int lowStockThreshold;
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
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

        if (item.getStock() < lowStockThreshold) {
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
}