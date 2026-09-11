package edu.cit.soldano.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryItem> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        Optional<InventoryItem> optItem = inventoryRepository.findById(productId);
        if (optItem.isEmpty()) {
            return false;
        }

        InventoryItem item = optItem.get();
        if (item.getStock() < quantity) {
            return false;
        }

        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);
        return true;
    }
}