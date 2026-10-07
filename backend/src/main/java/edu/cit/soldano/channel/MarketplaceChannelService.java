package edu.cit.soldano.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.soldano.events.StockChangedEvent;
import edu.cit.soldano.events.SupplierOrderDeliveredEvent;
import edu.cit.soldano.inventory.InventoryService;
import edu.cit.soldano.inventory.ProductDto;
import edu.cit.soldano.shop.ShopOrderCommand;
import edu.cit.soldano.shop.ShopOrderLine;
import edu.cit.soldano.shop.ShopOrderPort;
import edu.cit.soldano.shop.ShopOrderResult;
import edu.cit.soldano.supplier.SupplierGateway;
import edu.cit.soldano.supplier.SupplierOrderResult;
import edu.cit.soldano.supplier.SupplierOrderStatus;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
class MarketplaceChannelService implements MarketplaceChannel {
    private static final Map<String, String> SUPPLIER_SKUS = Map.of(
            "P100", "VQS-8242",
            "P200", "VQS-5456",
            "P300", "VQS-9391"
    );

    private final TianggeClient client;
    private final ClientInstanceProvider instance;
    private final InventoryService inventory;
    private final ShopOrderPort orders;
    private final SupplierGateway supplier;
    private final ChannelStateRepository stateRepository;
    private final MarketplaceOrderRepository orderRepository;
    private final ProcessedMarketplaceEventRepository eventRepository;
    private final ObjectMapper mapper;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile ChannelState state;

    MarketplaceChannelService(TianggeClient client, ClientInstanceProvider instance,
                              InventoryService inventory, ShopOrderPort orders,
                              SupplierGateway supplier, ChannelStateRepository stateRepository,
                              MarketplaceOrderRepository orderRepository,
                              ProcessedMarketplaceEventRepository eventRepository,
                              ObjectMapper mapper) {
        this.client = client;
        this.instance = instance;
        this.inventory = inventory;
        this.orders = orders;
        this.supplier = supplier;
        this.stateRepository = stateRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void goLive() {
        state = stateRepository.findById(1).orElseGet(() -> ChannelState.fresh(
                instance.instanceId(), instance.startedAt().toString()));
        state.setInstanceId(instance.instanceId());
        state.setOnline(false);
        state.setLastError(null);
        stateRepository.save(state);

        if (!client.configured()) {
            state.setLastError("TIANGGE_API_KEY or TIANGGE_CLIENT_ID is not configured");
            stateRepository.save(state);
            return;
        }
        try {
            client.heartbeat();
            instance.allowCalls();
            publishListings();
            publishAllStock();
            state.setOnline(true);
            state.setLastError(null);
            stateRepository.save(state);
            started.set(true);
        } catch (Exception exception) {
            recordError(exception);
        }
    }

    @Scheduled(fixedDelayString = "${tiangge.heartbeat-ms:30000}")
    public void heartbeat() {
        if (!started.get()) return;
        try {
            client.heartbeat();
            state.setOnline(true);
            state.setLastError(null);
            stateRepository.save(state);
        } catch (Exception exception) {
            recordError(exception);
        }
    }

    @Scheduled(fixedDelayString = "${tiangge.poll-ms:5000}")
    public void pollFeed() {
        if (!started.get()) return;
        try {
            TianggePayloads.FeedResponse response = client.feed(state.getCursor());
            for (TianggePayloads.FeedEvent event : response.events()) {
                if (!eventRepository.existsByEventId(event.eventId())) {
                    process(event);
                    eventRepository.save(new ProcessedMarketplaceEvent(event.eventId(), event.seq(), event.type()));
                }
                state.setCursor(Math.max(state.getCursor(), event.seq()));
                stateRepository.save(state);
            }
            if (response.nextCursor() != null) {
                state.setCursor(Math.max(state.getCursor(), response.nextCursor()));
                stateRepository.save(state);
            }
        } catch (Exception exception) {
            recordError(exception);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void reconcileBackorders() {
        if (!started.get()) return;
        for (MarketplaceOrder order : orderRepository.findByStatus(MarketplaceOrderStatus.BACKORDERED)) {
            try {
                List<ChannelOrderLine> lines = mapper.readValue(order.getLinesJson(), new TypeReference<>() {});
                if (lines.stream().allMatch(line -> inventory.checkStock(line.sellerSku(), line.qty()))) {
                    tryResolve(order, null);
                } else {
                    for (ChannelOrderLine line : lines) {
                        if (!inventory.checkStock(line.sellerSku(), line.qty())) {
                            supplier.placeReorder(line.sellerSku(), line.qty());
                        }
                    }
                }
            } catch (Exception exception) {
                recordError(exception);
            }
        }
    }

    @EventListener
    public void onStockChanged(StockChangedEvent event) {
        if (!started.get()) return;
        try {
            client.publishStock(List.of(new TianggePayloads.Stock(event.productId(), event.available())));
            state.setLastError(null);
            stateRepository.save(state);
        } catch (Exception exception) {
            recordError(exception);
        }
    }

    @EventListener
    @Order(10)
    public void onSupplierDelivery(SupplierOrderDeliveredEvent event) {
        if (!started.get()) return;
        for (MarketplaceOrder order : orderRepository.findByStatus(MarketplaceOrderStatus.BACKORDERED)) {
            tryResolve(order, event.productId());
        }
    }

    @Override
    public ChannelStatus status() {
        ChannelState current = state;
        if (current == null) {
            return new ChannelStatus(false, instance.instanceId(), 0, 0, 0, 0, "Starting");
        }
        return new ChannelStatus(current.isOnline(), current.getInstanceId(), current.getCursor(),
                current.getListingCount(), current.getDecisionCount(), current.getBackorderCount(), current.getLastError());
    }

    private void process(TianggePayloads.FeedEvent event) throws Exception {
        if ("ORDER_PLACED".equals(event.type())) {
            processPlaced(event);
        } else if ("ORDER_CANCELLED".equals(event.type())) {
            processCancelled(event);
        }
    }

    @Transactional
    void processPlaced(TianggePayloads.FeedEvent event) throws Exception {
        MarketplaceOrder existing = orderRepository.findByTianggeOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            resendDecision(existing);
            return;
        }

        List<ChannelOrderLine> lines = event.lines() == null ? List.of() : event.lines();
        boolean allAvailable = lines.stream().allMatch(line -> inventory.checkStock(line.sellerSku(), line.qty()));
        if (allAvailable) {
            ShopOrderResult result = orders.place(new ShopOrderCommand(lines.stream()
                    .map(line -> new ShopOrderLine(line.sellerSku(), line.qty())).toList()));
            MarketplaceOrderStatus status = "CONFIRMED".equals(result.status())
                    ? MarketplaceOrderStatus.ACCEPTED : MarketplaceOrderStatus.REJECTED;
            MarketplaceOrder order = new MarketplaceOrder(event.orderId(), event.eventId(), result.orderId(),
                    mapper.writeValueAsString(lines), status);
            orderRepository.save(order);
            client.decide(event.orderId(), status == MarketplaceOrderStatus.ACCEPTED ? "ACCEPTED" : "REJECTED",
                    result.orderId(), result.reason());
            countDecision();
            return;
        }

        boolean supplierCommitted = true;
        for (ChannelOrderLine line : lines) {
            if (!inventory.checkStock(line.sellerSku(), line.qty())) {
                try {
                    SupplierOrderResult result = supplier.placeReorder(line.sellerSku(), line.qty());
                    supplierCommitted &= result.status() == SupplierOrderStatus.PENDING
                            || result.status() == SupplierOrderStatus.PLACED;
                } catch (RuntimeException exception) {
                    supplierCommitted = false;
                }
            }
        }

        String shopOrderId = "BO-" + event.orderId();
        MarketplaceOrderStatus status = supplierCommitted
                ? MarketplaceOrderStatus.BACKORDERED : MarketplaceOrderStatus.REJECTED;
        MarketplaceOrder order = new MarketplaceOrder(event.orderId(), event.eventId(), shopOrderId,
                mapper.writeValueAsString(lines), status);
        orderRepository.save(order);
        client.decide(event.orderId(), status == MarketplaceOrderStatus.BACKORDERED ? "BACKORDERED" : "REJECTED",
                shopOrderId, supplierCommitted ? "Supplier replenishment is on the way" : "No replenishment is available");
        countDecision();
        state.setBackorderCount(orderRepository.findByStatus(MarketplaceOrderStatus.BACKORDERED).size());
        stateRepository.save(state);
    }

    private void processCancelled(TianggePayloads.FeedEvent event) throws Exception {
        MarketplaceOrder order = orderRepository.findByTianggeOrderId(event.orderId()).orElse(null);
        if (order == null) return;
        if (order.getStatus() == MarketplaceOrderStatus.ACCEPTED) {
            orders.cancel(order.getShopOrderId());
            client.confirmCancellation(event.orderId());
        } else if (order.getStatus() == MarketplaceOrderStatus.BACKORDERED) {
            client.resolve(event.orderId(), "CANCELLED");
        }
        order.setStatus(MarketplaceOrderStatus.CANCELLED);
        orderRepository.save(order);
        state.setBackorderCount(orderRepository.findByStatus(MarketplaceOrderStatus.BACKORDERED).size());
        stateRepository.save(state);
    }

    private void resendDecision(MarketplaceOrder order) throws Exception {
        if (order.getStatus() == MarketplaceOrderStatus.ACCEPTED) {
            client.decide(order.getTianggeOrderId(), "ACCEPTED", order.getShopOrderId(), null);
        } else if (order.getStatus() == MarketplaceOrderStatus.BACKORDERED) {
            client.decide(order.getTianggeOrderId(), "BACKORDERED", order.getShopOrderId(), "Supplier replenishment is on the way");
        } else {
            client.decide(order.getTianggeOrderId(), "REJECTED", order.getShopOrderId(), "Order cannot be filled");
        }
    }

    private void tryResolve(MarketplaceOrder order, String deliveredProductId) {
        try {
            List<ChannelOrderLine> lines = mapper.readValue(order.getLinesJson(), new TypeReference<>() {});
            if (deliveredProductId != null && lines.stream().noneMatch(line -> line.sellerSku().equals(deliveredProductId))) return;
            if (!lines.stream().allMatch(line -> inventory.checkStock(line.sellerSku(), line.qty()))) return;
            ShopOrderResult result = orders.place(new ShopOrderCommand(lines.stream()
                    .map(line -> new ShopOrderLine(line.sellerSku(), line.qty())).toList()));
            if (!"CONFIRMED".equals(result.status())) return;
            order.setShopOrderId(result.orderId());
            order.setStatus(MarketplaceOrderStatus.ACCEPTED);
            orderRepository.save(order);
            client.resolve(order.getTianggeOrderId(), "ACCEPTED");
            state.setBackorderCount(orderRepository.findByStatus(MarketplaceOrderStatus.BACKORDERED).size());
            stateRepository.save(state);
        } catch (Exception exception) {
            recordError(exception);
        }
    }

    private void publishListings() throws Exception {
        List<TianggePayloads.Listing> listings = inventory.getAllProducts().stream()
                .filter(product -> SUPPLIER_SKUS.containsKey(product.productId()))
                .map(product -> new TianggePayloads.Listing(product.productId(), product.productName(),
                        SUPPLIER_SKUS.get(product.productId())))
                .toList();
        client.publishListings(listings);
        state.setListingCount(listings.size());
        stateRepository.save(state);
    }

    private void publishAllStock() throws Exception {
        client.publishStock(inventory.getAllProducts().stream()
                .map(product -> new TianggePayloads.Stock(product.productId(), product.stock())).toList());
    }

    private void countDecision() {
        state.incrementDecisions();
        stateRepository.save(state);
    }

    private void recordError(Exception exception) {
        if (state == null) return;
        state.setOnline(false);
        state.setLastError(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        stateRepository.save(state);
    }
}
