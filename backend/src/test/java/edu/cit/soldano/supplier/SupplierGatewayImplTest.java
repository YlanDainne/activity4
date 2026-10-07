package edu.cit.soldano.supplier;

import edu.cit.soldano.events.SupplierOrderDeliveredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierGatewayImplTest {

    @Mock
    private SupplierOrderRepository repository;

    @Mock
    private LegacySupplyClient client;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SupplierGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new SupplierGatewayImpl(repository, client, eventPublisher);
    }

    @Test
    void roundsUnitsUpToWholeCasesAndCommitsBeforeDispatch() throws Exception {
        when(repository.saveAndFlush(any(SupplierOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(SupplierOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findFirstByProductIdAndStatusIn(anyString(), any()))
            .thenReturn(Optional.empty());
        when(client.submitPurchaseOrder(anyString(), any(LegacyXmlPayloads.PoRequest.class)))
            .thenReturn(new LegacyXmlPayloads.PoResponse("PO-1", "10", "VQS-8242", 2, "CS", "RO-TEST", "2026-09-25T00:00:00Z"));

        SupplierOrderResult result = gateway.placeReorder("P100", 13);

        assertEquals(2, result.casesOrdered());
        assertEquals(24, result.unitsExpected());
        verify(repository).saveAndFlush(any(SupplierOrder.class));
        verify(client).submitPurchaseOrder(anyString(), any(LegacyXmlPayloads.PoRequest.class));
    }

    @Test
    void rejectsNonPositiveQuantityBeforePersistence() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> gateway.placeReorder("P100", 0));

        verify(repository, never()).saveAndFlush(any(SupplierOrder.class));
        verify(client, never()).submitPurchaseOrder(anyString(), any(LegacyXmlPayloads.PoRequest.class));
    }

    @Test
    void reusesAnExistingOpenOrderInsteadOfCreatingADuplicate() throws Exception {
        SupplierOrder existing = new SupplierOrder("P100", "RO-EXISTING", "request-existing", 2, 24,
                SupplierOrderStatus.PLACED);
        existing.setPoNumber("PO-EXISTING");
        when(repository.findFirstByProductIdAndStatusIn(anyString(), any())).thenReturn(Optional.of(existing));

        SupplierOrderResult result = gateway.placeReorder("P100", 12);

        assertEquals("PO-EXISTING", result.poNumber());
        verify(repository, never()).saveAndFlush(any(SupplierOrder.class));
        verify(client, never()).submitPurchaseOrder(anyString(), any(LegacyXmlPayloads.PoRequest.class));
    }

    @Test
    void recordsUnexpectedStatusAsUnknown() throws Exception {
        SupplierOrder order = new SupplierOrder("P100", "RO-1", "request-1", 1, 12, SupplierOrderStatus.PLACED);
        order.setPoNumber("PO-1");
        when(repository.findByStatus(SupplierOrderStatus.PLACED)).thenReturn(List.of(order));
        when(client.checkOrderStatus("PO-1"))
            .thenReturn(new LegacyXmlPayloads.StatusResponse("PO-1", "77", "DELAYED", "VQS-8242", 1, "CS", "RO-1", "2026-09-25T00:00:00Z"));

        gateway.pollOpenOrders();

        assertEquals(SupplierOrderStatus.UNKNOWN, order.getStatus());
        verify(repository).save(order);
        verify(eventPublisher, never()).publishEvent(any(SupplierOrderDeliveredEvent.class));
    }
}