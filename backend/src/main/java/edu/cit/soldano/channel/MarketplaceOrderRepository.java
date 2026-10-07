package edu.cit.soldano.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, Long> {
    Optional<MarketplaceOrder> findByTianggeOrderId(String tianggeOrderId);
    List<MarketplaceOrder> findByStatus(MarketplaceOrderStatus status);
}
