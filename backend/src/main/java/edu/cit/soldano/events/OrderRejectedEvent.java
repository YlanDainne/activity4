package edu.cit.soldano.events;

public record OrderRejectedEvent(String orderId, String reason) {}