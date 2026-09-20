package edu.cit.soldano.events;

import java.util.List;

public record OrderPlacedEvent(String orderId, List<OrderItemRecord> items) {}