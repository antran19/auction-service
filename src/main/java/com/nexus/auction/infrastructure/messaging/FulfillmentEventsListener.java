package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.usecase.CancelAuctionForMissingStockUseCase;
import com.nexus.auction.application.usecase.SyncProductStockUseCase;
import com.nexus.common.events.AuctionHoldFailedEvent;
import com.nexus.common.events.InventoryLevelChangedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Feeds auction-service's copy of each product's stock, checked when an auction is created, and
// cancels an auction fulfillment could not hold a unit for. Order reservation outcomes on the
// same topic are commerce's business and are ignored here.
@Component
public class FulfillmentEventsListener {

    private final ObjectMapper objectMapper;
    private final SyncProductStockUseCase syncProductStockUseCase;
    private final CancelAuctionForMissingStockUseCase cancelAuctionForMissingStockUseCase;

    public FulfillmentEventsListener(ObjectMapper objectMapper, SyncProductStockUseCase syncProductStockUseCase,
                                     CancelAuctionForMissingStockUseCase cancelAuctionForMissingStockUseCase) {
        this.objectMapper = objectMapper;
        this.syncProductStockUseCase = syncProductStockUseCase;
        this.cancelAuctionForMissingStockUseCase = cancelAuctionForMissingStockUseCase;
    }

    // Exceptions are NOT caught here on purpose: KafkaErrorHandlingConfig retries them, then
    // moves the record to the dead-letter topic instead of losing it.
    @KafkaListener(topics = "fulfillment-events")
    public void onMessage(String rawPayload) throws JsonProcessingException {
        String eventType = objectMapper.readTree(rawPayload).path("eventType").asText();
        if ("InventoryLevelChanged".equals(eventType)) {
            InventoryLevelChangedEvent e = objectMapper.readValue(rawPayload, InventoryLevelChangedEvent.class);
            syncProductStockUseCase.onLevelChanged(e.getProductId(), e.getAvailableQuantity(), e.getOccurredAt());
        } else if ("AuctionHoldFailed".equals(eventType)) {
            AuctionHoldFailedEvent e = objectMapper.readValue(rawPayload, AuctionHoldFailedEvent.class);
            cancelAuctionForMissingStockUseCase.cancel(e.getAuctionId());
        }
    }
}
