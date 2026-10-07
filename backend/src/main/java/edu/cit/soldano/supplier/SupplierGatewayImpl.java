package edu.cit.soldano.supplier;

import edu.cit.soldano.events.SupplierOrderDeliveredEvent;
import org.springframework.context.ApplicationEventPublisher;
import edu.cit.soldano.channel.ClientInstanceProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
class SupplierGatewayImpl implements SupplierGateway {
    private static final int MAX_CASES_PER_ORDER = 99;

    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;
    private final ApplicationEventPublisher eventPublisher;
    private final ClientInstanceProvider instance;

    private record ProductPackConfig(String supplierSku, int packSize) {}
    private static final Map<String, ProductPackConfig> SKU_CATALOG = Map.of(
            "P100", new ProductPackConfig("VQS-8242", 12),
            "P200", new ProductPackConfig("VQS-5456", 24),
            "P300", new ProductPackConfig("VQS-9391", 12)
    );

    @org.springframework.beans.factory.annotation.Autowired
    SupplierGatewayImpl(SupplierOrderRepository repository, LegacySupplyClient client,
                        ApplicationEventPublisher eventPublisher, ClientInstanceProvider instance) {
        this.repository = repository;
        this.client = client;
        this.eventPublisher = eventPublisher;
        this.instance = instance;
    }

    SupplierGatewayImpl(SupplierOrderRepository repository, LegacySupplyClient client,
                        ApplicationEventPublisher eventPublisher) {
        this(repository, client, eventPublisher, null);
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

        var activeOrder = findCurrentActiveOrder(productId);
        if (activeOrder.isPresent()) {
            SupplierOrder existing = activeOrder.get();
            if (existing.getStatus() == SupplierOrderStatus.PENDING || existing.getPoNumber() == null) {
                return toResult(existing);
            }
            try {
                var status = client.checkOrderStatus(existing.getPoNumber());
                if (isOpenStatus(status)) {
                    return toResult(existing);
                }
                if (isDelivered(status)) {
                    existing.setStatus(SupplierOrderStatus.DELIVERED);
                    repository.save(existing);
                    eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                            existing.getProductId(), existing.getUnits(), existing.getPoNumber()));
                } else if (isRejected(status)) {
                    existing.setStatus(SupplierOrderStatus.REJECTED);
                    repository.save(existing);
                } else {
                    return toResult(existing);
                }
            } catch (Exception ignored) {
                return toResult(existing);
            }
        }

        int cases = (int) Math.ceil((double) unitsNeeded / config.packSize());
        SupplierOrder firstOrder = null;
        int remainingCases = cases;
        while (remainingCases > 0) {
            int orderCases = Math.min(remainingCases, MAX_CASES_PER_ORDER);
            SupplierOrder order = createPendingOrder(productId, orderCases, config);
            if (firstOrder == null) firstOrder = order;
            remainingCases -= orderCases;
        }
        return toResult(firstOrder);
    }

    private SupplierOrder createPendingOrder(String productId, int cases, ProductPackConfig config) {
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
            return order;
    }

    private SupplierOrderResult toResult(SupplierOrder order) {
        return new SupplierOrderResult(order.getId(), order.getBuyerRef(), order.getPoNumber(),
                order.getCases(), order.getUnits(), order.getStatus());
    }

    private java.util.Optional<SupplierOrder> findCurrentActiveOrder(String productId) {
        List<SupplierOrderStatus> statuses = List.of(SupplierOrderStatus.PENDING, SupplierOrderStatus.PLACED);
        if (instance == null) {
            return repository.findFirstByProductIdAndStatusIn(productId, statuses);
        }
        return repository.findFirstByProductIdAndStatusInAndCreatedAtAfter(
                productId, statuses, LocalDateTime.ofInstant(instance.startedAt(), java.time.ZoneOffset.UTC));
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
            SupplierOrderStatus failureStatus = e.getMessage() != null && e.getMessage().contains("E-QTY-11")
                    ? SupplierOrderStatus.REJECTED : SupplierOrderStatus.PENDING;
            order.setStatus(failureStatus);
            repository.save(order);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void retryPendingOrders() {
        quarantineInvalidPendingOrders();
        var pendings = repository.findByStatus(SupplierOrderStatus.PENDING);
        for (SupplierOrder order : pendings) {
            ProductPackConfig config = SKU_CATALOG.get(order.getProductId());
            if (config != null) {
                dispatchOrder(order, config);
            }
        }
    }

    private void quarantineInvalidPendingOrders() {
        for (SupplierOrder order : repository.findByStatus(SupplierOrderStatus.PENDING)) {
            if (order.getCases() > MAX_CASES_PER_ORDER) {
                order.setStatus(SupplierOrderStatus.REJECTED);
                repository.save(order);
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

                if (isDelivered(statusRes)) {
                    order.setStatus(SupplierOrderStatus.DELIVERED);
                    repository.save(order);
                    eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                            order.getProductId(),
                            order.getUnits(),
                            order.getPoNumber()
                    ));
                } else if (isRejected(statusRes)) {
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

    private boolean isDelivered(LegacyXmlPayloads.StatusResponse statusResponse) {
        String status = statusResponse.status() == null ? "" : statusResponse.status().toUpperCase();
        return "DELIVERED".equals(status) || "COMPLETED".equals(status)
                || "40".equals(statusResponse.statusCode());
    }

    private boolean isRejected(LegacyXmlPayloads.StatusResponse statusResponse) {
        String status = statusResponse.status() == null ? "" : statusResponse.status().toUpperCase();
        return "CANCELLED".equals(status) || "REJECTED".equals(status)
                || "90".equals(statusResponse.statusCode());
    }
}