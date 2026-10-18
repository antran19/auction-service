package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.auction.application.usecase.SyncProductStockUseCase;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.*;

class FulfillmentEventsListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SyncProductStockUseCase syncProductStockUseCase = mock(SyncProductStockUseCase.class);
    private final FulfillmentEventsListener listener = new FulfillmentEventsListener(objectMapper, syncProductStockUseCase);

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
}
