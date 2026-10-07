package edu.cit.soldano.shop;

import java.util.List;

public record OrderHistoryDto(String orderId, String status, String reason, List<OrderRequestItem> items) {}