package edu.cit.soldano.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Integer> {
    Optional<SupplierOrder> findByBuyerRef(String buyerRef);
    Optional<SupplierOrder> findFirstByProductIdAndStatusIn(String productId, List<SupplierOrderStatus> statuses);
        Optional<SupplierOrder> findFirstByProductIdAndStatusInAndCreatedAtAfter(
            String productId, List<SupplierOrderStatus> statuses, LocalDateTime createdAt);
    List<SupplierOrder> findByStatus(SupplierOrderStatus status);
}