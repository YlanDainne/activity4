package edu.cit.soldano.shop;

import edu.cit.soldano.inventory.ProductDto;
import java.util.List;

public record OrderResponse(
    String orderId,
    String status,
    String reason,
    List<ItemOutcome> items,
    List<ProductDto> inventory
) {}