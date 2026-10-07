package edu.cit.soldano.events;

public record LowStockEvent(String productId, int currentStock) {}