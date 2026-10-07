package edu.cit.soldano.supplier;

import java.time.LocalDateTime;

public record SupplierOrderDto(
        Integer id,
        String productId,
        String buyerRef,
        String requestId,
        String poNumber,
        int cases,
        int units,
        SupplierOrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
