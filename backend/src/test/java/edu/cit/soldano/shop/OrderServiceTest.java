package edu.cit.soldano.shop;

import edu.cit.soldano.inventory.InventoryItem;
import edu.cit.soldano.inventory.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private InventoryService inventoryService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        inventoryService = Mockito.mock(InventoryService.class);
        orderService = new OrderService(orderRepository, inventoryService);
    }

    @Test
    @DisplayName("Should confirm order when stock is sufficient")
    void placeOrder_confirmedPath() {
        // Arrange
        String productId = "P100";
        int orderQty = 2;
        InventoryItem itemBefore = new InventoryItem(productId, "Wireless Mouse", 25);
        InventoryItem itemAfter = new InventoryItem(productId, "Wireless Mouse", 23);

        when(inventoryService.getItem(productId))
                .thenReturn(Optional.of(itemBefore))
                .thenReturn(Optional.of(itemAfter));
        when(inventoryService.reserve(productId, orderQty)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        OrderResponse response = orderService.placeOrder(new OrderRequest(productId, orderQty));

        // Assert
        assertEquals("CONFIRMED", response.status());
        assertNull(response.reason());
        assertEquals(23, response.inventory());
        verify(inventoryService).reserve(productId, orderQty);
        verify(orderRepository).save(argThat(order -> "CONFIRMED".equals(order.getStatus())));
    }

    @Test
    @DisplayName("Should reject order when stock is insufficient")
    void placeOrder_rejectedPath_insufficientStock() {
        // Arrange
        String productId = "P300";
        int orderQty = 1;
        InventoryItem item = new InventoryItem(productId, "USB-C Hub", 0);

        when(inventoryService.getItem(productId)).thenReturn(Optional.of(item));
        when(inventoryService.reserve(productId, orderQty)).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        OrderResponse response = orderService.placeOrder(new OrderRequest(productId, orderQty));

        // Assert
        assertEquals("REJECTED", response.status());
        assertTrue(response.reason().contains("Insufficient stock"));
        assertEquals(0, response.inventory());
        verify(inventoryService).reserve(productId, orderQty);
        verify(orderRepository).save(argThat(order -> "REJECTED".equals(order.getStatus())));
    }

    @Test
    @DisplayName("Should reject order when product does not exist")
    void placeOrder_rejectedPath_productNotFound() {
        // Arrange
        String productId = "NON_EXISTENT";
        int orderQty = 1;

        when(inventoryService.getItem(productId)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        OrderResponse response = orderService.placeOrder(new OrderRequest(productId, orderQty));

        // Assert
        assertEquals("REJECTED", response.status());
        assertEquals("Product not found", response.reason());
        assertNull(response.inventory());
        verify(orderRepository).save(argThat(order -> "REJECTED".equals(order.getStatus())));
        verify(inventoryService, never()).reserve(any(), anyInt());
    }
}
