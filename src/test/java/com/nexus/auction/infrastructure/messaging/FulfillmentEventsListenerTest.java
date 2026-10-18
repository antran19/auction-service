package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.usecase.CancelAuctionForMissingStockUseCase;
import com.nexus.auction.application.usecase.SyncProductStockUseCase;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.*;

class FulfillmentEventsListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SyncProductStockUseCase syncProductStockUseCase = mock(SyncProductStockUseCase.class);
    private final CancelAuctionForMissingStockUseCase cancelAuctionForMissingStockUseCase =
            mock(CancelAuctionForMissingStockUseCase.class);
    private final FulfillmentEventsListener listener = new FulfillmentEventsListener(objectMapper, syncProductStockUseCase,
            cancelAuctionForMissingStockUseCase);

    @Test
    void onMessage_inventoryLevelChanged_updatesTheStockCopy() throws Exception {
        listener.onMessage("{\"eventId\":\"e-1\",\"eventType\":\"InventoryLevelChanged\",\"occurredAt\":\"2026-01-01T00:00:05Z\","
                + "\"aggregateId\":\"product-1\",\"productId\":\"product-1\",\"availableQuantity\":2}");

        verify(syncProductStockUseCase).onLevelChanged("product-1", 2, Instant.parse("2026-01-01T00:00:05Z"));
    }

    @Test
    void onMessage_reservationEvents_areIgnored() throws Exception {
        listener.onMessage("{\"eventId\":\"e-2\",\"eventType\":\"InventoryReserved\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"order-1\",\"orderId\":\"order-1\"}");

        verifyNoInteractions(syncProductStockUseCase);
    }

    @Test
    void onMessage_auctionHoldFailed_cancelsTheAuction() throws Exception {
        listener.onMessage("{\"eventId\":\"e-3\",\"eventType\":\"AuctionHoldFailed\",\"occurredAt\":\"2026-01-01T00:00:00Z\","
                + "\"aggregateId\":\"auction-1\",\"auctionId\":\"auction-1\",\"reason\":\"Not enough stock\"}");

        verify(cancelAuctionForMissingStockUseCase).cancel("auction-1");
        verifyNoInteractions(syncProductStockUseCase);
    }
}
