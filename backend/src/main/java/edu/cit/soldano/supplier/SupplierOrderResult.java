package edu.cit.soldano.supplier;

public record SupplierOrderResult(
    Integer internalOrderId,
    String buyerRef,
    String poNumber,
    int casesOrdered,
    int unitsExpected,
    SupplierOrderStatus status
) {}