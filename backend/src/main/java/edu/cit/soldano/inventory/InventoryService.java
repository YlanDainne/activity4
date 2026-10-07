package edu.cit.soldano.inventory;

import java.util.List;

public interface InventoryService {
    boolean checkStock(String productId, int quantity);
    void reserve(String productId, int quantity);
    void restock(String productId, int quantity);
    List<ProductDto> getAllProducts();
}