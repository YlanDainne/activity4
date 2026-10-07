package edu.cit.soldano.supplier;

import edu.cit.soldano.events.SupplierOrderDeliveredEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;
    private final ApplicationEventPublisher eventPublisher;

    private record ProductPackConfig(String supplierSku, int packSize) {}
    private static final Map<String, ProductPackConfig> SKU_CATALOG = Map.of(
            "P100", new ProductPackConfig("VQS-8242", 12),
            "P200", new ProductPackConfig("VQS-5456", 24),
            "P300", new ProductPackConfig("VQS-9391", 12)
    );

    SupplierGatewayImpl(SupplierOrderRepository repository, LegacySupplyClient client, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.client = client;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public synchronized SupplierOrderResult placeReorder(String productId, int unitsNeeded) {
        ProductPackConfig config = SKU_CATALOG.get(productId);
        if (config == null) {
            throw new IllegalArgumentException("Unsupported product for supplier reorder: " + productId);
        }
        if (unitsNeeded <= 0) {
            throw new IllegalArgumentException("Reorder quantity must be greater than zero");
        }

        var activeOrder = repository.findFirstByProductIdAndStatusIn(
                productId,
                List.of(SupplierOrderStatus.PENDING, SupplierOrderStatus.PLACED)
        );
        if (activeOrder.isPresent()) {
            return toResult(activeOrder.get());
        }

        int cases = (int) Math.ceil((double) unitsNeeded / config.packSize());
        int totalUnits = cases * config.packSize();

        String requestId = UUID.randomUUID().toString();
        String buyerRef = "RO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        SupplierOrder order = new SupplierOrder(
                productId,
                buyerRef,
                requestId,
                cases,
                totalUnits,
                SupplierOrderStatus.PENDING
        );
            // Commit the request identity before contacting the supplier so a crash cannot lose it.
            order = repository.saveAndFlush(order);

        dispatchOrder(order, config);

        return toResult(order);
    }

    private SupplierOrderResult toResult(SupplierOrder order) {
        return new SupplierOrderResult(order.getId(), order.getBuyerRef(), order.getPoNumber(),
                order.getCases(), order.getUnits(), order.getStatus());
    }

    private void dispatchOrder(SupplierOrder order, ProductPackConfig config) {
        System.out.println(">>> [GATEWAY] Attempting dispatch for: " + order.getBuyerRef());
        try {
            var poReq = new LegacyXmlPayloads.PoRequest(
                    config.supplierSku(),
                    order.getCases(),
                    order.getBuyerRef()
            );

            var response = client.submitPurchaseOrder(order.getRequestId(), poReq);
            order.setPoNumber(response.poNumber());
            order.setStatus(SupplierOrderStatus.PLACED);
            repository.save(order);
            System.out.println(">>> [GATEWAY] SUCCESS! Created PO: " + response.poNumber());
        } catch (Exception e) {
            System.err.println(">>> [GATEWAY] DISPATCH FAILED: " + e.getMessage());
            order.setStatus(SupplierOrderStatus.PENDING);
            repository.save(order);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void retryPendingOrders() {
        var pendings = repository.findByStatus(SupplierOrderStatus.PENDING);
        for (SupplierOrder order : pendings) {
            ProductPackConfig config = SKU_CATALOG.get(order.getProductId());
            if (config != null) {
                dispatchOrder(order, config);
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void pollOpenOrders() {
        var placedOrders = repository.findByStatus(SupplierOrderStatus.PLACED);
        for (SupplierOrder order : placedOrders) {
            if (order.getPoNumber() == null) continue;
            try {
                var statusRes = client.checkOrderStatus(order.getPoNumber());
                String supplierStatus = statusRes.status() != null ? statusRes.status().toUpperCase() : "";

                if ("DELIVERED".equals(supplierStatus) || "COMPLETED".equals(supplierStatus) || "40".equals(statusRes.statusCode())) {
                    order.setStatus(SupplierOrderStatus.DELIVERED);
                    repository.save(order);
                    eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                            order.getProductId(),
                            order.getUnits(),
                            order.getPoNumber()
                    ));
                } else if ("CANCELLED".equals(supplierStatus) || "REJECTED".equals(supplierStatus)) {
                    order.setStatus(SupplierOrderStatus.REJECTED);
                    repository.save(order);
                } else if (!isOpenStatus(statusRes)) {
                    order.setStatus(SupplierOrderStatus.UNKNOWN);
                    repository.save(order);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isOpenStatus(LegacyXmlPayloads.StatusResponse statusResponse) {
        String status = statusResponse.status() == null ? "" : statusResponse.status().toUpperCase();
        return "10".equals(statusResponse.statusCode())
                || "20".equals(statusResponse.statusCode())
                || "30".equals(statusResponse.statusCode())
                || "ACCEPTED".equals(status)
                || "PICKING".equals(status)
                || "SHIPPED".equals(status);
    }
}