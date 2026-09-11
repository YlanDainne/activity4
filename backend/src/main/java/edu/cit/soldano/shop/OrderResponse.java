package edu.cit.soldano.shop;

public record OrderResponse(
    String status,
    String reason,
    Integer inventory
) {
}