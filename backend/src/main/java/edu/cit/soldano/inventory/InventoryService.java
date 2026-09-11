package edu.cit.soldano.inventory;

import java.util.Optional;

public interface InventoryService {
    Optional<InventoryItem> getItem(String productId);
    boolean reserve(String productId, int quantity);
}