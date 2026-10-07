package edu.cit.soldano.events;

public record SupplierOrderDeliveredEvent(String productId, int units, String poNumber) {}