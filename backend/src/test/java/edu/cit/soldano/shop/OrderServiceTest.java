package edu.cit.soldano.shop;

import edu.cit.soldano.inventory.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

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

        OrderRequest request = new OrderRequest(List.of(new OrderRequestItem("P100", 2)));
        OrderResponse response = orderService.createOrder(request);

        assertEquals("CONFIRMED", response.status());
        assertNull(response.reason());
        verify(inventoryService, times(1)).reserve("P100", 2);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void testCreateOrder_AllOrNothingRollback_WhenOneItemFails() {
        when(inventoryService.checkStock("P100", 2)).thenReturn(true);
        when(inventoryService.checkStock("P200", 10)).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

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
}