package edu.cit.soldano.channel;

import jakarta.persistence.*;

@Entity
@Table(name = "marketplace_events")
class ProcessedMarketplaceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;
    @Column(nullable = false)
    private long sequence;
    @Column(nullable = false)
    private String type;

    public ProcessedMarketplaceEvent() {}
    ProcessedMarketplaceEvent(String eventId, long sequence, String type) {
        this.eventId = eventId;
        this.sequence = sequence;
        this.type = type;
    }
}
