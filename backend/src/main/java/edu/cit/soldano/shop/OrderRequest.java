package edu.cit.soldano.shop;

public record OrderRequest(
    String productId,
    int quantity
) {
}