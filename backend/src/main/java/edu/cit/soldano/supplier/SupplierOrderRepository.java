package edu.cit.soldano.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Integer> {
    Optional<SupplierOrder> findByBuyerRef(String buyerRef);
    Optional<SupplierOrder> findFirstByProductIdAndStatusIn(String productId, List<SupplierOrderStatus> statuses);
    List<SupplierOrder> findByStatus(SupplierOrderStatus status);
}