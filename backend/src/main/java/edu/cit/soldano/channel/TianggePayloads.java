package edu.cit.soldano.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

final class TianggePayloads {
    private TianggePayloads() {}

    record HeartbeatRequest(String appName, String startedAt, long uptimeSeconds) {}
    record HeartbeatResponse(String serverTime, Integer nextHeartbeatSeconds) {}
    record Listing(String sellerSku, String title, String supplierSku) {}
    record Stock(String sellerSku, int available) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedResponse(List<FeedEvent> events, Long nextCursor) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedEvent(long seq, String eventId, String type, String orderId,
                     List<ChannelOrderLine> lines, String placedAt,
                     String decisionDeadline, String cancelledAt, String confirmDeadline) {}

    record DecisionRequest(String decision, String shopOrderId, String reason) {}
    record ResolutionRequest(String status) {}
    record CancellationRequest(boolean restocked) {}
}
