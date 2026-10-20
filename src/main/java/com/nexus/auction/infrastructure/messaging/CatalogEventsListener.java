package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.usecase.SyncProductSnapshotUseCase;
import com.nexus.common.events.ProductCreatedEvent;
import com.nexus.common.events.ProductStatusChangedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Feeds auction-service's copy of product owner and status, checked when an auction is created.
// Other catalog events (renames, categories) are ignored.
@Component
public class CatalogEventsListener {

    private final ObjectMapper objectMapper;
    private final SyncProductSnapshotUseCase syncProductSnapshotUseCase;

    public CatalogEventsListener(ObjectMapper objectMapper, SyncProductSnapshotUseCase syncProductSnapshotUseCase) {
        this.objectMapper = objectMapper;
        this.syncProductSnapshotUseCase = syncProductSnapshotUseCase;
    }

    // Exceptions are NOT caught here on purpose: NexusKafkaAutoConfiguration (common-kafka) retries them, then
    // moves the record to the dead-letter topic instead of losing it.
    @KafkaListener(topics = "catalog-events")
    public void onMessage(String rawPayload) throws JsonProcessingException {
        String eventType = objectMapper.readTree(rawPayload).path("eventType").asText();
        if ("ProductCreated".equals(eventType)) {
            ProductCreatedEvent e = objectMapper.readValue(rawPayload, ProductCreatedEvent.class);
            syncProductSnapshotUseCase.onCreated(e.getProductId(), e.getSellerId());
        } else if ("ProductStatusChanged".equals(eventType)) {
            ProductStatusChangedEvent e = objectMapper.readValue(rawPayload, ProductStatusChangedEvent.class);
            syncProductSnapshotUseCase.onStatusChanged(e.getProductId(), e.getNewStatus());
        }
    }
}
