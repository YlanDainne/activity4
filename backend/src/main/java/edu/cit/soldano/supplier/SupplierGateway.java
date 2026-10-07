package edu.cit.soldano.supplier;

public interface SupplierGateway {
    SupplierOrderResult placeReorder(String productId, int unitsNeeded);
}