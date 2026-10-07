package edu.cit.soldano.shop;

import java.util.List;

public record OrderResponse(String orderId, String status, String reason, List<ItemOutcome> items) {}