package edu.cit.soldano.shop;

import edu.cit.soldano.inventory.InventoryService;
import edu.cit.soldano.inventory.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, inventoryService, eventPublisher);
    }

    @Test
    void testCreateOrder_Success() {
        when(inventoryService.checkStock("P100", 2)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(inventoryService.getAllProducts()).thenReturn(List.of(new ProductDto("P100", "Wireless Mouse", 20)));

        OrderRequest request = new OrderRequest(List.of(new OrderRequestItem("P100", 2)));
        OrderResponse response = orderService.createOrder(request);

        assertEquals("CONFIRMED", response.status());
        assertNull(response.reason());
        assertNotNull(response.inventory());
        assertEquals(1, response.inventory().size());
        verify(inventoryService, times(1)).reserve("P100", 2);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void testCreateOrder_AllOrNothingRollback_WhenOneItemFails() {
        when(inventoryService.checkStock("P100", 2)).thenReturn(true);
        when(inventoryService.checkStock("P200", 10)).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(inventoryService.getAllProducts()).thenReturn(List.of());

        OrderRequest request = new OrderRequest(List.of(
                new OrderRequestItem("P100", 2),
                new OrderRequestItem("P200", 10)
        ));
        OrderResponse response = orderService.createOrder(request);

        assertEquals("REJECTED", response.status());
        assertNotNull(response.reason());
        // Verify NO reservations were made because of all-or-nothing check
        verify(inventoryService, never()).reserve(anyString(), anyInt());
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void testCancelOrder_Success() {
        Order order = new Order("ORD-1", "CONFIRMED", null);
        OrderItem item1 = new OrderItem(order, "P100", 2);
        order.setItems(List.of(item1));

        when(orderRepository.findById("ORD-1")).thenReturn(Optional.of(order));

        orderService.cancelOrder("ORD-1");

        assertEquals("CANCELLED", order.getStatus());
        verify(inventoryService, times(1)).restock("P100", 2);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void testCancelOrder_NotFound_Throws404() {
        when(orderRepository.findById("ORD-MISSING")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orderService.cancelOrder("ORD-MISSING")
        );
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void testCancelOrder_AlreadyCancelled_Throws409() {
        Order order = new Order("ORD-2", "CANCELLED", null);
        when(orderRepository.findById("ORD-2")).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orderService.cancelOrder("ORD-2")
        );
        assertEquals(409, ex.getStatusCode().value());
        verify(inventoryService, never()).restock(anyString(), anyInt());
    }
}