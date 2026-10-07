package edu.cit.soldano.events;

public record StockChangedEvent(String productId, int available, String reason) {}