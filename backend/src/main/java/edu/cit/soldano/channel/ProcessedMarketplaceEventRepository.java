package edu.cit.soldano.channel;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedMarketplaceEventRepository extends JpaRepository<ProcessedMarketplaceEvent, Long> {
    boolean existsByEventId(String eventId);
}
